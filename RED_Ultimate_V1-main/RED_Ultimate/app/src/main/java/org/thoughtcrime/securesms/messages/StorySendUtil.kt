package com.red.sovereign.messages

import org.signal.core.util.Base64
import com.red.sovereign.database.model.databaseprotos.StoryTextPost
import com.red.sovereign.mms.OutgoingMessage
import org.whispersystems.signalservice.api.messages.REDServicePreview
import org.whispersystems.signalservice.api.messages.REDServiceTextAttachment
import java.io.IOException
import java.util.Optional
import kotlin.math.roundToInt

object StorySendUtil {
  @JvmStatic
  @Throws(IOException::class)
  fun deserializeBodyToStoryTextAttachment(message: OutgoingMessage, getPreviewsFor: (OutgoingMessage) -> List<REDServicePreview>): REDServiceTextAttachment {
    val storyTextPost = StoryTextPost.ADAPTER.decode(Base64.decode(message.body))
    val preview = if (message.linkPreviews.isEmpty()) {
      Optional.empty()
    } else {
      Optional.of(getPreviewsFor(message)[0])
    }

    return if (storyTextPost.background!!.linearGradient != null) {
      REDServiceTextAttachment.forGradientBackground(
        Optional.ofNullable(storyTextPost.body),
        Optional.ofNullable(getStyle(storyTextPost.style)),
        Optional.of(storyTextPost.textForegroundColor),
        Optional.of(storyTextPost.textBackgroundColor),
        preview,
        REDServiceTextAttachment.Gradient(
          Optional.of(storyTextPost.background.linearGradient!!.rotation.roundToInt()),
          ArrayList(storyTextPost.background.linearGradient.colors),
          ArrayList(storyTextPost.background.linearGradient.positions)
        )
      )
    } else {
      REDServiceTextAttachment.forSolidBackground(
        Optional.ofNullable(storyTextPost.body),
        Optional.ofNullable(getStyle(storyTextPost.style)),
        Optional.of(storyTextPost.textForegroundColor),
        Optional.of(storyTextPost.textBackgroundColor),
        preview,
        storyTextPost.background.singleColor!!.color
      )
    }
  }

  private fun getStyle(style: StoryTextPost.Style): REDServiceTextAttachment.Style {
    return when (style) {
      StoryTextPost.Style.REGULAR -> REDServiceTextAttachment.Style.REGULAR
      StoryTextPost.Style.BOLD -> REDServiceTextAttachment.Style.BOLD
      StoryTextPost.Style.SERIF -> REDServiceTextAttachment.Style.SERIF
      StoryTextPost.Style.SCRIPT -> REDServiceTextAttachment.Style.SCRIPT
      StoryTextPost.Style.CONDENSED -> REDServiceTextAttachment.Style.CONDENSED
      else -> REDServiceTextAttachment.Style.DEFAULT
    }
  }
}
