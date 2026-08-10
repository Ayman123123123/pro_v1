package com.red.sovereign.mediasend.v2.review

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.signal.core.ui.view.Stub
import org.signal.core.util.ByteLimitInputFilter
import org.signal.core.util.EditTextUtil
import org.signal.core.util.getParcelableCompat
import org.signal.mediasend.HudCommand
import com.red.sovereign.R
import com.red.sovereign.backup.v2.ui.warning.guardAgainstRecoveryKeyPaste
import com.red.sovereign.components.KeyboardAwareLinearLayout
import com.red.sovereign.components.KeyboardEntryDialogFragment
import com.red.sovereign.components.ViewBinderDelegate
import com.red.sovereign.components.emoji.MediaKeyboard
import com.red.sovereign.components.mention.MentionAnnotation
import com.red.sovereign.conversation.ui.inlinequery.InlineQuery
import com.red.sovereign.conversation.ui.inlinequery.InlineQuery.NoQuery
import com.red.sovereign.conversation.ui.inlinequery.InlineQueryChangedListener
import com.red.sovereign.conversation.ui.inlinequery.InlineQueryResultsController
import com.red.sovereign.conversation.ui.inlinequery.InlineQueryViewModel
import com.red.sovereign.conversation.ui.mentions.MentionsPickerFragment
import com.red.sovereign.conversation.ui.mentions.MentionsPickerViewModel
import com.red.sovereign.databinding.V2MediaAddMessageDialogFragmentBinding
import com.red.sovereign.keyboard.KeyboardPage
import com.red.sovereign.keyboard.KeyboardPagerViewModel
import com.red.sovereign.keyboard.emoji.EmojiKeyboardEvent
import com.red.sovereign.keyboard.emoji.EmojiKeyboardEventViewModel
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.stories.Stories
import com.red.sovereign.util.MessageUtil
import com.red.sovereign.util.ViewUtil
import com.red.sovereign.util.viewModel
import com.red.sovereign.util.visible

class AddMessageDialogFragment : KeyboardEntryDialogFragment(R.layout.v2_media_add_message_dialog_fragment) {

  private val viewModel: AddMessageViewModel by viewModel {
    AddMessageViewModel(initialMessage = requireArguments().getCharSequence(ARG_INITIAL_TEXT, null))
  }

  private val commandViewModel: EmojiKeyboardEventViewModel by activityViewModels()

  private val keyboardPagerViewModel: KeyboardPagerViewModel by activityViewModels()

  private lateinit var mentionsViewModel: MentionsPickerViewModel

  private val inlineQueryViewModel: InlineQueryViewModel by activityViewModels()

  private val binding by ViewBinderDelegate(V2MediaAddMessageDialogFragmentBinding::bind, onBindingWillBeDestroyed = { binding ->
    binding.content.addAMessageInput.setInlineQueryChangedListener(null)
    binding.content.addAMessageInput.setMentionValidator(null)
  })

  private lateinit var emojiDrawerStub: Stub<MediaKeyboard>
  private lateinit var inlineQueryResultsController: InlineQueryResultsController

  private var requestedEmojiDrawer: Boolean = false
  private var displayingRecoveryKeyWarning: Boolean = false

  private var recipient: Recipient? = null

  private val disposables = CompositeDisposable()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    val themeWrapper = ContextThemeWrapper(inflater.context, R.style.TextSecure_DarkTheme)
    val themedInflater = LayoutInflater.from(themeWrapper)

    return super.onCreateView(themedInflater, container, savedInstanceState)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    if (viewModel.isViewOnce) {
      dismissAllowingStateLoss()
      return
    }

    emojiDrawerStub = Stub(binding.content.emojiDrawerStub)

    if (Stories.isFeatureEnabled()) {
      EditTextUtil.addGraphemeClusterLimitFilter(binding.content.addAMessageInput, Stories.MAX_CAPTION_SIZE)
    }

    binding.content.addAMessageInput.addTextChangedListener(afterTextChanged = {
      viewModel.updateAddAMessageCount(it)
    })

    binding.content.addAMessageInput.setText(requireArguments().getCharSequence(ARG_INITIAL_TEXT))
    binding.content.addAMessageInput.addTextChangedListener { viewModel.message = it }
    binding.content.addAMessageInput.filters += ByteLimitInputFilter(MessageUtil.MAX_TOTAL_BODY_SIZE_BYTES)
    binding.content.addAMessageInput.guardAgainstRecoveryKeyPaste(
      host = this,
      onWarningShown = { displayingRecoveryKeyWarning = true },
      onWarningDismissed = {
        displayingRecoveryKeyWarning = false
        ViewUtil.focusAndShowKeyboard(binding.content.addAMessageInput)
      }
    )

    binding.content.emojiToggle.setOnClickListener { onEmojiToggleClicked() }
    if (requireArguments().getBoolean(ARG_INITIAL_EMOJI_TOGGLE) && view is KeyboardAwareLinearLayout) {
      view.addOnKeyboardShownListener(EmojiLaunchListener(view))
    }

    binding.hud.setOnClickListener { dismissAllowingStateLoss() }

    binding.content.viewOnceToggle.setOnClickListener {
      viewModel.message = null
      viewModel.isViewOnce = true

      dismissAllowingStateLoss()
    }

    val confirm: View = view.findViewById(R.id.confirm_button)
    confirm.setOnClickListener { dismissAllowingStateLoss() }

    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
      viewModel.watchAddAMessageCount().collect { count ->
        binding.content.addAMessageLimit.visible = count.shouldDisplayCount()
        binding.content.addAMessageLimit.text = count.getRemaining().toString()
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(state = Lifecycle.State.RESUMED) {
        commandViewModel.events.collect {
          when (it) {
            EmojiKeyboardEvent.CloseEmojiSearch -> closeEmojiSearch()
            is EmojiKeyboardEvent.EmojiInsert -> onEmojiSelected(it.emoji)
            is EmojiKeyboardEvent.EmojiKeyEvent -> onKeyEvent(it.keyEvent)
            EmojiKeyboardEvent.OpenEmojiSearch -> openEmojiSearch()
          }
        }
      }
    }

    binding.content.viewOnceToggle.visible = requireArguments().getBoolean(ARG_VIEW_ONCE_AVAILABLE, false)
    initializeMentions()
  }

  override fun onResume() {
    super.onResume()

    requestedEmojiDrawer = false
    ViewUtil.focusAndShowKeyboard(binding.content.addAMessageInput)
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)

    setFragmentResult(
      REQUEST_KEY,
      Bundle().apply {
        putCharSequence(RESULT_MESSAGE, viewModel.message)
        putBoolean(RESULT_INCREMENT_VIEW_ONCE_STATE, viewModel.isViewOnce)
      }
    )
  }

  override fun onKeyboardHidden() {
    if (!requestedEmojiDrawer && !displayingRecoveryKeyWarning) {
      super.onKeyboardHidden()
    }
  }

  override fun onKeyboardShown() {
    super.onKeyboardShown()
    if (emojiDrawerStub.resolved() && emojiDrawerStub.get().isShowing) {
      if (emojiDrawerStub.get().isEmojiSearchMode) {
        binding.content.emojiToggle.setToIme()
      } else {
        emojiDrawerStub.get().hide(true)
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    disposables.dispose()
  }

  private fun initializeMentions() {
    mentionsViewModel = ViewModelProvider(requireActivity(), MentionsPickerViewModel.Factory()).get(MentionsPickerViewModel::class.java)

    inlineQueryResultsController = InlineQueryResultsController(
      inlineQueryViewModel,
      requireView().findViewById(R.id.background_holder),
      (requireView() as ViewGroup),
      binding.content.addAMessageInput,
      viewLifecycleOwner
    )

    binding.content.addAMessageInput.setInlineQueryChangedListener(object : InlineQueryChangedListener {
      override fun onQueryChanged(inlineQuery: InlineQuery) {
        when (inlineQuery) {
          is InlineQuery.Mention -> {
            recipient?.takeIf { it.isPushV2Group && it.isActiveGroup }.let {
              ensureMentionsContainerFilled()
              mentionsViewModel.onQueryChange(inlineQuery.query)
            }
            inlineQueryViewModel.onQueryChange(inlineQuery)
          }

          is InlineQuery.Emoji -> {
            inlineQueryViewModel.onQueryChange(inlineQuery)
            mentionsViewModel.onQueryChange(null)
          }

          is NoQuery -> {
            mentionsViewModel.onQueryChange(null)
            inlineQueryViewModel.onQueryChange(inlineQuery)
          }
        }
      }

      override fun clearQuery() {
        onQueryChanged(NoQuery)
      }
    })

    disposables += inlineQueryViewModel
      .selection
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { r -> binding.content.addAMessageInput.replaceText(r) }

    val recipientId: RecipientId = requireArguments().getParcelableCompat(ARG_DESTINATION, RecipientId::class.java) ?: return

    Recipient.live(recipientId).observe(viewLifecycleOwner) { recipient ->
      this.recipient = recipient

      val confirmButton = requireView().findViewById<View>(R.id.confirm_button)
      ViewCompat.setBackgroundTintList(confirmButton, ColorStateList.valueOf(recipient.chatColors.asSingleColor()))
      mentionsViewModel.onRecipientChange(recipient)

      binding.content.addAMessageInput.setMentionValidator { annotations ->
        if (!recipient.isPushV2Group) {
          annotations
        } else {
          val validRecipientIds: Set<String> = recipient.participantIds
            .map { id -> MentionAnnotation.idToMentionAnnotationValue(id) }
            .toSet()

          annotations
            .filter { !validRecipientIds.contains(it.value) }
            .toList()
        }
      }
    }

    mentionsViewModel.selectedRecipient.observe(viewLifecycleOwner) { recipient ->
      binding.content.addAMessageInput.replaceTextWithMention(recipient.getDisplayName(requireContext()), recipient.id)
    }
  }

  private fun ensureMentionsContainerFilled() {
    val mentionsFragment = childFragmentManager.findFragmentById(R.id.mentions_picker_container)
    if (mentionsFragment == null) {
      childFragmentManager
        .beginTransaction()
        .replace(R.id.mentions_picker_container, MentionsPickerFragment())
        .commitNowAllowingStateLoss()
    }
  }

  private fun onEmojiToggleClicked() {
    if (!emojiDrawerStub.resolved()) {
      keyboardPagerViewModel.setOnlyPage(KeyboardPage.EMOJI)
      emojiDrawerStub.get().setFragmentManager(childFragmentManager)
      binding.content.emojiToggle.attach(emojiDrawerStub.get())
    }

    if (binding.hud.currentInput == emojiDrawerStub.get()) {
      requestedEmojiDrawer = false
      binding.hud.showSoftkey(binding.content.addAMessageInput)
    } else {
      requestedEmojiDrawer = true
      binding.hud.show(binding.content.addAMessageInput, emojiDrawerStub.get())
    }
  }

  private fun openEmojiSearch() {
    if (emojiDrawerStub.resolved()) {
      emojiDrawerStub.get().onOpenEmojiSearch()
    }
  }

  private fun closeEmojiSearch() {
    if (emojiDrawerStub.resolved()) {
      emojiDrawerStub.get().onCloseEmojiSearch()
    }
  }

  private fun onEmojiSelected(emoji: String?) {
    binding.content.addAMessageInput.insertEmoji(emoji)
  }

  private fun onKeyEvent(keyEvent: KeyEvent?) {
    binding.content.addAMessageInput.dispatchKeyEvent(keyEvent)
  }

  private inner class EmojiLaunchListener(private val layout: KeyboardAwareLinearLayout) : KeyboardAwareLinearLayout.OnKeyboardShownListener {
    override fun onKeyboardShown() {
      layout.removeOnKeyboardShownListener(this)
      onEmojiToggleClicked()
    }
  }

  companion object {

    const val TAG = "ADD_MESSAGE_DIALOG_FRAGMENT"

    private const val ARG_INITIAL_TEXT = "arg.initial.text"
    private const val ARG_INITIAL_EMOJI_TOGGLE = "arg.initial.emojiToggle"
    private const val ARG_VIEW_ONCE_AVAILABLE = "arg.viewOnceAvailable"
    private const val ARG_DESTINATION = "arg.destination"

    const val REQUEST_KEY = "AddMessageViewModel__RequestKey"
    const val RESULT_INCREMENT_VIEW_ONCE_STATE = "AddMessageViewModel_IncrementViewOnceState"
    const val RESULT_MESSAGE = "AddMessageViewModel__Message"

    fun show(fragmentManager: FragmentManager, addAMessageDialog: HudCommand.ShowAddAMessageDialog, destination: RecipientId?) {
      return show(fragmentManager, addAMessageDialog.message, addAMessageDialog.startWithEmojiKeyboard, addAMessageDialog.isViewOnceAvailable, destination)
    }

    fun show(fragmentManager: FragmentManager, initialText: CharSequence?, startWithEmojiKeyboard: Boolean, isViewOnceAvailable: Boolean, destination: RecipientId?) {
      AddMessageDialogFragment().apply {
        arguments = Bundle().apply {
          putCharSequence(ARG_INITIAL_TEXT, initialText)
          putBoolean(ARG_INITIAL_EMOJI_TOGGLE, startWithEmojiKeyboard)
          putBoolean(ARG_VIEW_ONCE_AVAILABLE, isViewOnceAvailable)
          putParcelable(ARG_DESTINATION, destination)
        }
      }.show(fragmentManager, TAG)
    }
  }
}
