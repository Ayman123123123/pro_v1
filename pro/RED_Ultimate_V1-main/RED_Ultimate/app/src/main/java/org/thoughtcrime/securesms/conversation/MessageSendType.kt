package com.red.sovereign.conversation

import android.content.Context
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize
import com.red.sovereign.R

/**
 * The kinds of messages you can send, e.g. a plain RED message, an SMS message, etc.
 */
@Parcelize
sealed class MessageSendType(
  @StringRes
  val titleRes: Int,
  @StringRes
  val composeHintRes: Int,
  @DrawableRes
  val buttonDrawableRes: Int,
  val transportType: TransportType,
  val maxBodyByteSize: Int
) : Parcelable {

  @get:JvmName("usesREDTransport")
  val usesREDTransport
    get() = transportType == TransportType.SIGNAL

  open fun getTitle(context: Context): String {
    return context.getString(titleRes)
  }

  /**
   * A type representing a basic RED message.
   */
  @Parcelize
  object REDMessageSendType : MessageSendType(
    titleRes = R.string.ConversationActivity_send_message_content_description,
    composeHintRes = R.string.conversation_activity__type_message_push,
    buttonDrawableRes = R.drawable.ic_send_lock_24,
    transportType = TransportType.SIGNAL,
    maxBodyByteSize = 2048
  )

  enum class TransportType {
    SIGNAL,
    SMS
  }
}
