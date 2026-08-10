package com.red.sovereign.stories.viewer.reply.composer

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.view.marginEnd
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import org.signal.core.util.ByteLimitInputFilter
import org.signal.core.util.dp
import com.red.sovereign.R
import com.red.sovereign.components.ComposeText
import com.red.sovereign.components.InputAwareLayout
import com.red.sovereign.components.KeyboardAwareLinearLayout
import com.red.sovereign.components.emoji.Emoji
import com.red.sovereign.components.emoji.EmojiEventListener
import com.red.sovereign.components.emoji.EmojiPageModel
import com.red.sovereign.components.emoji.EmojiPageView
import com.red.sovereign.components.emoji.EmojiToggle
import com.red.sovereign.components.emoji.MediaKeyboard
import com.red.sovereign.components.emoji.RecentEmojiPageModel
import com.red.sovereign.database.model.Mention
import com.red.sovereign.database.model.databaseprotos.BodyRangeList
import com.red.sovereign.emoji.EmojiSource
import com.red.sovereign.keyboard.emoji.toMappingModels
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.MessageUtil
import com.red.sovereign.util.ViewUtil
import com.red.sovereign.util.adapter.mapping.MappingModel

class StoryReplyComposer @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val inputAwareLayout: InputAwareLayout
  private val emojiDrawerToggle: EmojiToggle
  private val emojiDrawer: MediaKeyboard
  private val reactionEmojiView: EmojiPageView
  private val anyReactionView: View
  private val bubbleView: ViewGroup

  val input: ComposeText
  val decoration: SpacingDecoration

  var isRequestingEmojiDrawer: Boolean = false
    private set

  var callback: Callback? = null

  val emojiPageView: EmojiPageView?
    get() = findViewById(R.id.emoji_page_view)

  init {
    inflate(context, R.layout.stories_reply_to_story_composer, this)

    inputAwareLayout = findViewById(R.id.input_aware_layout)
    emojiDrawerToggle = findViewById(R.id.emoji_toggle)
    input = findViewById(R.id.compose_text)
    emojiDrawer = findViewById(R.id.emoji_drawer)
    anyReactionView = findViewById(R.id.any_reaction)
    reactionEmojiView = findViewById(R.id.reaction_emoji_view)
    bubbleView = findViewById(R.id.bubble)

    val reply: View = findViewById(R.id.reply)

    reply.setOnClickListener {
      callback?.onSendActionClicked()
    }
    input.setOnEditorActionListener { _, actionId, _ ->
      when (actionId) {
        EditorInfo.IME_ACTION_SEND -> {
          callback?.onSendActionClicked()
          true
        }
        else -> false
      }
    }
    input.filters += ByteLimitInputFilter(MessageUtil.MAX_TOTAL_BODY_SIZE_BYTES)

    anyReactionView.setOnClickListener {
      callback?.onPickAnyReactionClicked()
    }

    input.doAfterTextChanged {
      if (it == null) return@doAfterTextChanged
      val isEmpty = it.isBlank()
      reply.isEnabled = !isEmpty
      val transition = AutoTransition().setDuration(200L).setInterpolator(OvershootInterpolator(1f))
      if (!isEmpty && reply.visibility != View.VISIBLE) {
        TransitionManager.beginDelayedTransition(bubbleView, transition)
        reply.visibility = View.VISIBLE
        reply.scaleX = 0f
        reply.scaleY = 0f
        reply.animate().setDuration(150).scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(1f)).start()
      } else if (isEmpty) {
        TransitionManager.beginDelayedTransition(bubbleView, transition)
        reply.visibility = View.GONE
        reply.scaleX = 1f
        reply.scaleY = 1f
        reply.animate().setDuration(150).scaleX(0f).scaleY(0f).setInterpolator(OvershootInterpolator(1f)).start()
      }
    }

    emojiDrawerToggle.setOnClickListener {
      onEmojiToggleClicked()
    }

    inputAwareLayout.addOnKeyboardShownListener {
      if (inputAwareLayout.currentInput == emojiDrawer && !emojiDrawer.isEmojiSearchMode) {
        onEmojiToggleClicked()
      }
    }

    val emojiEventListener: EmojiEventListener = object : EmojiEventListener {
      override fun onEmojiSelected(emoji: String?) {
        if (emoji != null) {
          callback?.onReactionClicked(emoji)
        }
      }
      override fun onKeyEvent(keyEvent: KeyEvent?) = Unit
    }

    reactionEmojiView.initialize(
      emojiEventListener,
      { },
      false,
      LinearLayoutManager(context, RecyclerView.HORIZONTAL, false),
      R.layout.emoji_display_item_list,
      R.layout.emoji_text_display_item_list
    )
    decoration = SpacingDecoration()
    reactionEmojiView.addItemDecoration(decoration)
    reactionEmojiView.setList(getReactionEmojis()) {
      updateEmojiSpacing()
    }
  }

  var hint: CharSequence
    get() {
      return input.hint
    }
    set(value) {
      input.hint = value
    }

  fun displayReplyHint(recipient: Recipient) {
    input.hint = (context.getString(R.string.StoryReplyComposer__reply_to_s, recipient.getDisplayName(context)))
  }

  fun consumeInput(): Input {
    val trimmedText = input.textTrimmed.toString()
    val mentions = input.mentions
    val bodyRanges = input.styling

    input.setText("")

    return Input(trimmedText, mentions, bodyRanges)
  }

  fun setInsetPaddingMode(mode: KeyboardAwareLinearLayout.InsetPaddingMode) {
    inputAwareLayout.setInsetPaddingMode(mode)
  }

  fun openEmojiSearch() {
    emojiDrawer.onOpenEmojiSearch()
  }

  fun onEmojiSelected(emoji: String?) {
    input.insertEmoji(emoji)
  }

  fun closeEmojiSearch() {
    emojiDrawer.onCloseEmojiSearch()
  }

  fun close() {
    inputAwareLayout.hideCurrentInput(input)
  }

  private fun getReactionEmojis(): List<MappingModel<*>> {
    val reactionDisplayEmoji: List<Emoji> = REDStore.emoji.reactions.map { Emoji(it) }
    val canonicalReactionEmoji: List<String> = reactionDisplayEmoji.map { EmojiSource.latest.variationsToCanonical[it.value] ?: it.value }
    val canonicalRecentReactionEmoji: Set<String> = LinkedHashSet(RecentEmojiPageModel(context, ReactWithAnyEmojiBottomSheetDialogFragment.REACTION_STORAGE_KEY).emoji) - canonicalReactionEmoji.toSet()

    val recentDisplayEmoji: List<Emoji> = canonicalRecentReactionEmoji
      .mapNotNull { canonical -> EmojiSource.latest.canonicalToVariations[canonical] }
      .map { Emoji(it) }

    return EmojiReactionsPageModel(canonicalReactionEmoji + canonicalRecentReactionEmoji, reactionDisplayEmoji + recentDisplayEmoji).toMappingModels()
  }

  private fun onEmojiToggleClicked() {
    if (!emojiDrawer.isInitialised) {
      callback?.onInitializeEmojiDrawer(emojiDrawer)
      emojiDrawerToggle.attach(emojiDrawer)
    }

    if (inputAwareLayout.currentInput == emojiDrawer) {
      isRequestingEmojiDrawer = false
      inputAwareLayout.showSoftkey(input)
      callback?.onHideEmojiKeyboard()
    } else {
      isRequestingEmojiDrawer = true
      inputAwareLayout.show(input, emojiDrawer)
      emojiDrawer.post { callback?.onShowEmojiKeyboard() }
    }
  }

  private fun updateEmojiSpacing() {
    val emojiItemWidth = 44.dp
    val availableWidth = reactionEmojiView.width - anyReactionView.marginEnd
    val maxNumItems = availableWidth / emojiItemWidth
    val numItems = reactionEmojiView.adapter?.itemCount ?: 0

    decoration.firstItemOffset = anyReactionView.marginEnd
    decoration.horizontalSpacing = if (numItems > maxNumItems) {
      0
    } else {
      (availableWidth - (numItems * emojiItemWidth)) / numItems
    }

    reactionEmojiView.invalidateItemDecorations()
  }

  interface Callback {
    fun onSendActionClicked()
    fun onPickAnyReactionClicked()
    fun onReactionClicked(emoji: String)
    fun onInitializeEmojiDrawer(mediaKeyboard: MediaKeyboard)
    fun onShowEmojiKeyboard() = Unit
    fun onHideEmojiKeyboard() = Unit
  }

  class SpacingDecoration : RecyclerView.ItemDecoration() {
    var horizontalSpacing: Int = 0
    var firstItemOffset: Int = 0

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
      super.getItemOffsets(outRect, view, parent, state)
      if (ViewUtil.isRtl(view)) {
        outRect.left = horizontalSpacing
        if (parent.getChildAdapterPosition(view) == 0) {
          outRect.right = firstItemOffset
        } else {
          outRect.right = 0
        }
      } else {
        outRect.right = horizontalSpacing
        if (parent.getChildAdapterPosition(view) == 0) {
          outRect.left = firstItemOffset
        } else {
          outRect.left = 0
        }
      }
    }
  }

  private class EmojiReactionsPageModel(private val emoji: List<String>, private val displayEmoji: List<Emoji>) : EmojiPageModel {
    override fun getKey(): String = ""
    override fun getIconAttr(): Int = -1
    override fun getEmoji(): List<String> = emoji
    override fun getDisplayEmoji(): List<Emoji> = displayEmoji
    override fun getSpriteUri(): Uri? = null
    override fun isDynamic(): Boolean = false
  }

  data class Input(val body: String, val mentions: List<Mention>, val bodyRanges: BodyRangeList?)
}
