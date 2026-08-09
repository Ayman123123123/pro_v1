package com.red.sovereign.mediasend.v2

import android.net.Uri
import org.signal.core.models.media.Media
import org.signal.mediasend.MediaConstraints
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.edit.video.VideoTrimData
import com.red.sovereign.conversation.MessageSendType
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.mms.TranscodingConfigProvider
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.stories.Stories
import com.red.sovereign.util.MediaUtil
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.video.TranscodingConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class MediaSelectionState(
  val sendType: MessageSendType,
  val selectedMedia: List<Media> = listOf(),
  val focusedMedia: Media? = null,
  val recipient: Recipient? = null,
  val quality: SentMediaQuality = REDStore.settings.sentMediaQuality,
  val message: CharSequence? = null,
  val viewOnceToggleState: ViewOnceToggleState = ViewOnceToggleState.default,
  val isTouchEnabled: Boolean = true,
  val isSent: Boolean = false,
  val isPreUploadEnabled: Boolean = false,
  val isMeteredConnection: Boolean = false,
  val editorStateMap: Map<Uri, Any> = mapOf(),
  val cameraFirstCapture: Media? = null,
  val isStory: Boolean,
  val storySendRequirements: Stories.MediaTransform.SendRequirements = Stories.MediaTransform.SendRequirements.CAN_NOT_SEND,
  val suppressEmptyError: Boolean = true,
  val transcodingConfigs: List<TranscodingConfig.QualityTier> = TranscodingConfigProvider.getConfigsForMediaQuality(SentMediaQuality.fromCode(quality.code))
) {

  val isVideoTrimmingVisible: Boolean = focusedMedia != null && MediaUtil.isVideoType(focusedMedia.contentType) && MediaConstraints.isVideoTranscodeAvailable() && !focusedMedia.isVideoGif

  val maxSelection = RemoteConfig.maxAttachmentCount

  val canSend = !isSent && selectedMedia.isNotEmpty()

  fun getOrCreateVideoTrimData(uri: Uri): VideoTrimData {
    return editorStateMap[uri] as? VideoTrimData ?: VideoTrimData()
  }

  fun calculateMaxVideoDurationUs(videoDuration: Duration): Long {
    return if (isStory && !MediaConstraints.isVideoTranscodeAvailable()) {
      Stories.MAX_VIDEO_DURATION_MILLIS
    } else {
      TranscodingConfig.calculateMaxVideoUploadDurationInSeconds(transcodingConfigs, videoDuration).seconds.inWholeMicroseconds
    }
  }

  enum class ViewOnceToggleState(val code: Int) {
    INFINITE(0),
    ONCE(1);

    fun next(): ViewOnceToggleState {
      return when (this) {
        INFINITE -> ONCE
        ONCE -> INFINITE
      }
    }

    companion object {
      val default = INFINITE

      fun fromCode(code: Int): ViewOnceToggleState {
        return when (code) {
          1 -> ONCE
          else -> INFINITE
        }
      }
    }
  }
}
