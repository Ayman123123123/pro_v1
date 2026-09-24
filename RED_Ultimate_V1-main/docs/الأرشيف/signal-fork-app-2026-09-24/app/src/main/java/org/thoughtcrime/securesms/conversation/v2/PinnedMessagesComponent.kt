package com.red.sovereign.conversation.v2

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnPreDraw
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.theme.REDTheme
import org.signal.core.ui.fonts.REDSymbols
import org.signal.core.ui.fonts.REDSymbols.getSpannedString
import org.signal.glide.compose.GlideImage
import org.signal.glide.decryptableuri.DecryptableUri
import com.red.sovereign.R
import com.red.sovereign.components.emoji.EmojiTextView
import com.red.sovereign.contactshare.ContactUtil
import com.red.sovereign.conversation.ConversationMessage
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.mms.AudioSlide
import com.red.sovereign.mms.DocumentSlide
import com.red.sovereign.mms.ImageSlide
import com.red.sovereign.mms.StickerSlide
import com.red.sovereign.mms.VideoSlide
import com.red.sovereign.util.DynamicTheme
import com.red.sovereign.util.hasSharedContact
import com.red.sovereign.util.hasSticker
import com.red.sovereign.util.isPoll
import com.red.sovereign.util.isViewOnceMessage
import org.whispersystems.signalservice.api.payments.FormatterOptions
import kotlin.jvm.optionals.getOrDefault

/**
 * Displays pinned messages banner on conversation fragment
 */
@Composable
fun PinnedMessagesBanner(
  messages: List<ConversationMessage> = emptyList(),
  canUnpin: Boolean,
  hasWallpaper: Boolean,
  onUnpinMessage: (Long) -> Unit = {},
  onGoToMessage: (Long) -> Unit = {},
  onViewAllMessages: () -> Unit = {}
) {
  val menuController = remember { DropdownMenus.MenuController() }
  var index by remember(messages) { mutableIntStateOf(messages.size - 1) }
  val conversationMessage = messages[index % messages.size]
  val message = conversationMessage.messageRecord as MmsMessageRecord
  val (glyph, body, showThumbnail) = getMessageMetadata(conversationMessage)

  Column(
    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 54.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(color = if (DynamicTheme.isDarkTheme(LocalContext.current)) Color(0XFF4A4C52) else Color(0XFFCCCFD5))
    ) {}
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .background(if (hasWallpaper) colorResource(R.color.conversation_toolbar_color_wallpaper_scrolled) else REDTheme.colors.colorSurface2)
        .clickable {
          index = (index + 1) % messages.size
          onGoToMessage(message.id)
        }
        .height(IntrinsicSize.Min)
    ) {
      if (messages.size > 1) {
        Heading(index, messages.size)
      } else {
        Spacer(modifier = Modifier.size(16.dp))
      }

      AnimatedContent(
        modifier = Modifier.weight(1f),
        targetState = message,
        transitionSpec = {
          slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(150)) togetherWith
            slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(150))
        }
      ) { message ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clickable(
              onClick = {
                index = (index + 1) % messages.size
                onGoToMessage(message.id)
              },
              indication = null,
              interactionSource = remember { MutableInteractionSource() },
              onClickLabel = stringResource(R.string.REDCheckbox_accessibility_on_click_label),
              enabled = true
            )
            .padding(vertical = 7.dp)
        ) {
          if (showThumbnail &&
            !message.hasSticker() &&
            message.slideDeck.firstSlide?.uri != null &&
            !message.slideDeck.firstSlide!!.isVideoGif
          ) {
            GlideImage(
              model = DecryptableUri(message.slideDeck.firstSlide!!.uri!!),
              modifier = Modifier
                .padding(end = 8.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
            )
          }

          Column(
            modifier = Modifier
              .weight(1f)
          ) {
            Text(
              text = if (message.fromRecipient.isSelf) {
                stringResource(R.string.Recipient_you)
              } else {
                message.fromRecipient.getDisplayName(LocalContext.current)
              },
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.bodySmall
            )

            val displayBody = if (glyph != null) {
              SpannableStringBuilder()
                .append(getSpannedString(LocalContext.current, REDSymbols.Weight.REGULAR, glyph, -1))
                .append(" ")
                .append(body)
            } else {
              body
            }

            AndroidView(
              factory = ::EmojiTextView
            ) { view ->
              view.enableRenderSpoilers()
              view.text = displayBody
              view.ellipsize = TextUtils.TruncateAt.END
              view.maxLines = 1
              view.doOnPreDraw {
                (it as EmojiTextView).ellipsizeEmojiTextForMaxLines()
              }
            }
          }
        }
      }

      Box(modifier = Modifier.padding(vertical = 7.dp, horizontal = 8.dp).padding(end = 8.dp)) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.symbol_pin_24),
          contentDescription = stringResource(R.string.PinnedMessage__pinned),
          modifier = Modifier
            .clickable { menuController.show() }
            .padding(vertical = 8.dp),
          tint = MaterialTheme.colorScheme.onSurface
        )

        DropdownMenus.Menu(controller = menuController, offsetX = 2.dp, offsetY = 16.dp) { menuController ->
          Column {
            if (canUnpin) {
              DropdownMenus.ItemWithIcon(menuController, R.drawable.symbol_pin_slash_24, R.string.PinnedMessage__unpin_message) { onUnpinMessage(message.id) }
            }
            DropdownMenus.ItemWithIcon(menuController, R.drawable.symbol_chat_arrow_24, R.string.PinnedMessage__go_to_message) { onGoToMessage(message.id) }
            DropdownMenus.ItemWithIcon(menuController, R.drawable.symbol_list_bullet_24, R.string.PinnedMessage__view_all_messages) { onViewAllMessages() }
          }
        }
      }
    }
  }
}

/**
 * Heading to show how many pinned messages there are and which one (of three) is being displayed
 */
@Composable
fun Heading(selectedIndex: Int, size: Int) {
  AnimatedContent(
    targetState = selectedIndex,
    transitionSpec = {
      fadeIn(tween(durationMillis = 150)).togetherWith(fadeOut(tween(durationMillis = 150)))
    }
  ) { selectedIndex ->
    Column(
      modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp)
    ) {
      for (i in 0 until size) {
        Box(
          modifier = Modifier
            .padding(vertical = 2.dp)
            .padding(horizontal = 7.dp)
            .width(2.dp)
            .weight(1f)
            .background(
              color = if (i == selectedIndex) {
                MaterialTheme.colorScheme.onSurface
              } else if (DynamicTheme.isDarkTheme(LocalContext.current)) {
                MaterialTheme.colorScheme.secondaryContainer
              } else {
                REDTheme.colors.colorTransparentInverse2
              },
              shape = RoundedCornerShape(16.dp)
            )
        )
      }
    }
  }
}

/**
 * Given the type of message, returns the associated glyph, body, and whether or not a thumbnail should be rendered with it
 */
@Composable
fun getMessageMetadata(conversationMessage: ConversationMessage): Triple<REDSymbols.Glyph?, SpannableString, Boolean> {
  val context = LocalContext.current
  val message = conversationMessage.messageRecord as MmsMessageRecord
  val slide = message.slideDeck.firstSlide
  return if (slide is StickerSlide) {
    Triple(REDSymbols.Glyph.STICKER, SpannableString(stringResource(R.string.PinnedMessage__sticker)), false)
  } else if (slide is AudioSlide) {
    Triple(REDSymbols.Glyph.AUDIO, SpannableString(stringResource(R.string.PinnedMessage__voice)), false)
  } else if (slide is DocumentSlide) {
    Triple(REDSymbols.Glyph.FILE, SpannableString(slide.fileName.getOrDefault(stringResource(R.string.DocumentView_unnamed_file))), false)
  } else if (message.isViewOnceMessage()) {
    Triple(REDSymbols.Glyph.VIEW_ONCE, SpannableString(stringResource(R.string.PinnedMessage__view_once)), false)
  } else if (message.isPoll()) {
    Triple(REDSymbols.Glyph.POLL, SpannableString(stringResource(R.string.Poll__poll_question, message.body)), false)
  } else if (message.hasSharedContact()) {
    Triple(REDSymbols.Glyph.PERSON_CIRCLE, SpannableString(ContactUtil.getDisplayName(message.sharedContacts.first())), false)
  } else if (message.isPaymentNotification && message.payment != null) {
    Triple(REDSymbols.Glyph.CREDIT_CARD, SpannableString(message.payment!!.amount.toString(FormatterOptions.defaults())), false)
  } else if (slide?.isVideoGif == true) {
    Triple(REDSymbols.Glyph.GIF_RECTANGLE, SpannableString(stringResource(R.string.PinnedMessage__gif)), false)
  } else if (slide is ImageSlide && message.body.isEmpty()) {
    Triple(null, SpannableString(stringResource(R.string.PinnedMessage__photo)), true)
  } else if (slide is VideoSlide && message.body.isEmpty()) {
    Triple(null, SpannableString(stringResource(R.string.PinnedMessage__video)), true)
  } else {
    Triple(null, conversationMessage.getDisplayBody(context), true)
  }
}
