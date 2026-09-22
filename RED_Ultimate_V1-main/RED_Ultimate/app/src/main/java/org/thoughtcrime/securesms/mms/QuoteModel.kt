package com.red.sovereign.mms

import com.red.sovereign.attachments.Attachment
import com.red.sovereign.database.model.Mention
import com.red.sovereign.database.model.databaseprotos.BodyRangeList
import com.red.sovereign.recipients.RecipientId
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage
import org.whispersystems.signalservice.internal.push.DataMessage

class QuoteModel(
  val id: Long,
  val author: RecipientId,
  val text: String,
  val isOriginalMissing: Boolean,
  val attachment: Attachment?,
  mentions: List<Mention>?,
  val type: Type,
  val bodyRanges: BodyRangeList?
) {
  val mentions: List<Mention>

  init {
    this.mentions = mentions ?: emptyList()
  }

  enum class Type(val code: Int, val dataMessageType: REDServiceDataMessage.Quote.Type) {

    NORMAL(0, REDServiceDataMessage.Quote.Type.NORMAL),
    GIFT_BADGE(1, REDServiceDataMessage.Quote.Type.GIFT_BADGE),
    POLL(2, REDServiceDataMessage.Quote.Type.POLL);

    companion object {
      @JvmStatic
      fun fromCode(code: Int): Type {
        for (value in entries) {
          if (value.code == code) {
            return value
          }
        }
        throw IllegalArgumentException("Invalid code: $code")
      }

      @JvmStatic
      fun fromDataMessageType(dataMessageType: REDServiceDataMessage.Quote.Type): Type {
        for (value in entries) {
          if (value.dataMessageType === dataMessageType) {
            return value
          }
        }
        return NORMAL
      }

      fun fromProto(type: DataMessage.Quote.Type?): Type {
        return when (type) {
          DataMessage.Quote.Type.NORMAL -> NORMAL
          DataMessage.Quote.Type.GIFT_BADGE -> GIFT_BADGE
          DataMessage.Quote.Type.POLL -> POLL
          null -> NORMAL
        }
      }
    }
  }
}
