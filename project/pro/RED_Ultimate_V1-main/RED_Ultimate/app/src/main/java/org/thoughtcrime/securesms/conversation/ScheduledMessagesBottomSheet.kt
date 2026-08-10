package com.red.sovereign.conversation

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.BottomSheetUtil.requireCoordinatorLayout
import org.signal.core.ui.FixedRoundedCornerBottomSheetDialogFragment
import org.signal.core.util.StreamUtil
import org.signal.core.util.Util
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.SimpleTask
import org.signal.core.util.dp
import org.signal.core.util.logging.Log
import com.red.sovereign.R
import com.red.sovereign.components.REDProgressDialog
import com.red.sovereign.components.menu.ActionItem
import com.red.sovereign.components.menu.REDContextMenu
import com.red.sovereign.components.recyclerview.SmoothScrollingLinearLayoutManager
import com.red.sovereign.conversation.colors.ColorizerV1
import com.red.sovereign.conversation.colors.RecyclerViewColorizer
import com.red.sovereign.conversation.mutiselect.MultiselectPart
import com.red.sovereign.conversation.mutiselect.MultiselectPart.Attachments
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.giph.mp4.GiphyMp4ItemDecoration
import com.red.sovereign.giph.mp4.GiphyMp4PlaybackController
import com.red.sovereign.giph.mp4.GiphyMp4PlaybackPolicy
import com.red.sovereign.giph.mp4.GiphyMp4ProjectionPlayerHolder
import com.red.sovereign.giph.mp4.GiphyMp4ProjectionRecycler
import com.red.sovereign.groups.GroupId
import com.red.sovereign.mms.PartAuthority
import com.red.sovereign.mms.TextSlide
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.util.StickyHeaderDecoration
import com.red.sovereign.util.fragments.findListener
import com.red.sovereign.util.hasTextSlide
import com.red.sovereign.util.requireTextSlide
import java.io.IOException
import java.util.Locale
import org.signal.core.ui.R as CoreUiR

/**
 * Bottom sheet dialog to view all scheduled messages within a given thread.
 */
class ScheduledMessagesBottomSheet : FixedRoundedCornerBottomSheetDialogFragment(), ScheduleMessageTimePickerBottomSheet.RescheduleCallback {

  override val peekHeightPercentage: Float = 0.66f
  override val themeResId: Int = R.style.Widget_RED_FixedRoundedCorners_Messages

  private var firstRender: Boolean = true
  private var deleteDialog: AlertDialog? = null

  private lateinit var messageAdapter: ConversationAdapter
  private lateinit var callback: ConversationBottomSheetCallback

  private val viewModel: ScheduledMessagesViewModel by viewModels(
    factoryProducer = {
      val threadId = requireArguments().getLong(KEY_THREAD_ID)
      ScheduledMessagesViewModel.Factory(threadId)
    }
  )

  private val disposables: LifecycleDisposable = LifecycleDisposable()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    val view = inflater.inflate(R.layout.scheduled_messages_bottom_sheet, container, false)
    disposables.bindTo(viewLifecycleOwner)
    return view
  }

  @SuppressLint("WrongThread")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val conversationRecipientId = RecipientId.from(arguments?.getString(KEY_CONVERSATION_RECIPIENT_ID, null) ?: throw IllegalArgumentException())
    val conversationRecipient = Recipient.resolved(conversationRecipientId)

    callback = findListener<ConversationBottomSheetCallback>() ?: run {
      dismissAllowingStateLoss()
      return
    }

    @Suppress("DEPRECATION")
    val colorizer = ColorizerV1()

    messageAdapter = ConversationAdapter(requireContext(), viewLifecycleOwner, Glide.with(this), Locale.getDefault(), ConversationAdapterListener(), conversationRecipient.hasWallpaper, colorizer).apply {
      setCondensedMode(ConversationItemDisplayMode.Condensed(ConversationItemDisplayMode.MessageMode.SCHEDULED))
    }

    val stickyHeaders = StickyHeaderDecoration(messageAdapter, false, false, ConversationAdapter.HEADER_TYPE_INLINE_DATE)

    val list: RecyclerView = view.findViewById<RecyclerView>(R.id.scheduled_list).apply {
      layoutManager = SmoothScrollingLinearLayoutManager(requireContext(), true)
      adapter = messageAdapter
      itemAnimator = null

      doOnNextLayout {
        // Adding this without waiting for a layout pass would result in an indeterminate amount of padding added to the top of the view
        addItemDecoration(stickyHeaders)
      }
    }

    val recyclerViewColorizer = RecyclerViewColorizer(list)

    disposables += viewModel.getMessages(requireContext()).subscribe { messages ->
      if (messages.isEmpty()) {
        deleteDialog?.dismiss()
        dismissAllowingStateLoss()
      }

      messageAdapter.submitList(messages) {
        if (firstRender) {
          (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(messages.size - 1, 100)

          firstRender = false
        } else if (!list.canScrollVertically(1)) {
          list.layoutManager?.scrollToPosition(0)
        }

        stickyHeaders.invalidateCache()
        list.invalidateItemDecorations()
      }
      recyclerViewColorizer.setChatColors(conversationRecipient.chatColors)
    }

    initializeGiphyMp4(view.findViewById(R.id.video_container)!!, list)
  }

  private fun initializeGiphyMp4(videoContainer: ViewGroup, list: RecyclerView): GiphyMp4ProjectionRecycler {
    val maxPlayback = GiphyMp4PlaybackPolicy.maxSimultaneousPlaybackInConversation()
    val holders = GiphyMp4ProjectionPlayerHolder.injectVideoViews(
      requireContext(),
      viewLifecycleOwner.lifecycle,
      videoContainer,
      maxPlayback
    )
    val callback = GiphyMp4ProjectionRecycler(holders)

    GiphyMp4PlaybackController.attach(list, callback, maxPlayback)
    list.addItemDecoration(GiphyMp4ItemDecoration(callback), 0)

    return callback
  }

  private fun showScheduledMessageContextMenu(view: View, conversationMessage: ConversationMessage) {
    REDContextMenu.Builder(view, requireCoordinatorLayout())
      .offsetX(12.dp)
      .offsetY(12.dp)
      .preferredVerticalPosition(REDContextMenu.VerticalPosition.ABOVE)
      .show(getMenuActionItems(conversationMessage))
  }

  private fun getMenuActionItems(message: ConversationMessage): List<ActionItem> {
    val canCopy = message.multiselectCollection.toSet().any { it !is Attachments && message.messageRecord.body.isNotEmpty() }
    val items: MutableList<ActionItem> = ArrayList()
    items.add(ActionItem(CoreUiR.drawable.symbol_trash_24, resources.getString(R.string.conversation_selection__menu_delete), action = { handleDeleteMessage(message.messageRecord) }))
    if (canCopy) {
      items.add(ActionItem(CoreUiR.drawable.symbol_copy_android_24, resources.getString(R.string.conversation_selection__menu_copy), action = { handleCopyMessage(message) }))
    }
    items.add(ActionItem(R.drawable.symbol_send_24, resources.getString(R.string.ScheduledMessagesBottomSheet_menu_send_now), action = { handleSendMessageNow(message.messageRecord) }))
    items.add(ActionItem(R.drawable.symbol_calendar_24, resources.getString(R.string.ScheduledMessagesBottomSheet_menu_reschedule), action = { handleRescheduleMessage(message.messageRecord) }))
    return items
  }

  private fun handleRescheduleMessage(messageRecord: MessageRecord) {
    ScheduleMessageTimePickerBottomSheet.showReschedule(childFragmentManager, messageRecord.id, (messageRecord as MmsMessageRecord).scheduledDate)
  }

  private fun handleSendMessageNow(messageRecord: MessageRecord) {
    viewModel.rescheduleMessage(messageRecord.id, System.currentTimeMillis())
  }

  private fun handleDeleteMessage(messageRecord: MessageRecord) {
    deleteDialog?.dismiss()
    deleteDialog = buildDeleteScheduledMessageConfirmationDialog(messageRecord)
      .setOnDismissListener { deleteDialog = null }
      .show()
  }

  private fun handleCopyMessage(message: ConversationMessage) {
    SimpleTask.run(
      viewLifecycleOwner.lifecycle,
      { getMessageText(message) },
      { bodies: CharSequence? ->
        if (!Util.isEmpty(bodies)) {
          Util.copyToClipboard(requireContext(), bodies!!)
        }
      }
    )
  }

  private fun buildDeleteScheduledMessageConfirmationDialog(messageRecord: MessageRecord): AlertDialog.Builder {
    return MaterialAlertDialogBuilder(requireContext())
      .setTitle(resources.getString(R.string.ScheduledMessagesBottomSheet_delete_dialog_message))
      .setCancelable(true)
      .setPositiveButton(R.string.ScheduledMessagesBottomSheet_delete_dialog_action) { _: DialogInterface?, _: Int ->
        deleteMessage(messageRecord.id)
      }
      .setNegativeButton(android.R.string.cancel, null)
  }

  private fun getMessageText(message: ConversationMessage): CharSequence {
    if (message.messageRecord.hasTextSlide()) {
      val textSlide: TextSlide = message.messageRecord.requireTextSlide()
      if (textSlide.uri == null) {
        return message.getDisplayBody(requireContext())
      }
      try {
        PartAuthority.getAttachmentStream(requireContext(), textSlide.uri!!).use { stream ->
          val body = StreamUtil.readFullyAsString(stream)
          return ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(requireContext(), message.messageRecord, body, message.threadRecipient)
            .getDisplayBody(requireContext())
        }
      } catch (e: IOException) {
        Log.w(TAG, "Failed to read text slide data.")
      }
    }
    return ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(requireContext(), message.messageRecord, message.threadRecipient).getDisplayBody(requireContext())
  }

  private fun deleteMessage(messageId: Long) {
    context?.let { context ->
      val progressDialog = REDProgressDialog.show(
        context = context,
        message = resources.getString(R.string.ScheduledMessagesBottomSheet_deleting_progress_message),
        indeterminate = true
      )
      SimpleTask.run({
        REDDatabase.messages.deleteScheduledMessage(messageId)
      }, {
        progressDialog.dismiss()
      })
    }
  }

  override fun onReschedule(scheduledTime: Long, messageId: Long) {
    viewModel.rescheduleMessage(messageId, scheduledTime)
  }

  private inner class ConversationAdapterListener : ConversationAdapter.ItemClickListener by callback.getConversationAdapterListener() {
    override fun onQuoteClicked(messageRecord: MmsMessageRecord) {
      dismissAllowingStateLoss()
      callback.getConversationAdapterListener().onQuoteClicked(messageRecord)
    }

    override fun onScheduledIndicatorClicked(view: View, conversationMessage: ConversationMessage) {
      showScheduledMessageContextMenu(view, conversationMessage)
    }

    override fun onGroupMemberClicked(recipientId: RecipientId, groupId: GroupId) {
      dismissAllowingStateLoss()
      callback.getConversationAdapterListener().onGroupMemberClicked(recipientId, groupId)
    }

    override fun onVoiceNotePlay(uri: Uri, messageId: Long, position: Double) {
      callback.getConversationAdapterListener().onSingleVoiceNotePlay(uri, messageId, position)
    }

    override fun onItemLongClick(itemView: View?, item: MultiselectPart?) = Unit
    override fun onItemClick(item: MultiselectPart?) = Unit
  }

  companion object {
    private val TAG = Log.tag(ScheduledMessagesBottomSheet::class.java)

    private const val KEY_THREAD_ID = "thread_id"
    private const val KEY_CONVERSATION_RECIPIENT_ID = "conversation_recipient_id"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, threadId: Long, conversationRecipientId: RecipientId) {
      val args = Bundle().apply {
        putLong(KEY_THREAD_ID, threadId)
        putString(KEY_CONVERSATION_RECIPIENT_ID, conversationRecipientId.serialize())
      }

      val fragment = ScheduledMessagesBottomSheet().apply {
        arguments = args
      }

      fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
