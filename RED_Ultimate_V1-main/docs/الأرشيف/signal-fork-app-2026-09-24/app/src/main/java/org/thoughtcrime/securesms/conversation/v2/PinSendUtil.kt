package com.red.sovereign.conversation.v2

import android.content.Context
import org.signal.core.models.ServiceId
import com.red.sovereign.crypto.ProfileKeyUtil
import com.red.sovereign.database.MessageTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.GroupRecord
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.GroupAccessControl
import com.red.sovereign.groups.GroupNotAMemberException
import com.red.sovereign.messages.GroupSendUtil
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.transport.UndeliverableMessageException
import com.red.sovereign.util.GroupUtil
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.messages.SendMessageResult
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage.Companion.newBuilder
import java.io.IOException
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Functions used when pinning/unpinning messages
 */
object PinSendUtil {

  private val PIN_TERMINATE_TIMEOUT = 7000.milliseconds

  @Throws(IOException::class, GroupNotAMemberException::class, UndeliverableMessageException::class)
  fun sendPinMessage(applicationContext: Context, threadRecipient: Recipient, message: OutgoingMessage, destinations: List<Recipient>, includeSelf: Boolean, relatedMessageId: Long): List<SendMessageResult?> {
    val builder = newBuilder()
    val groupId = if (threadRecipient.isPushV2Group) threadRecipient.requireGroupId().requireV2() else null

    if (groupId != null) {
      val groupRecord: GroupRecord? = REDDatabase.groups.getGroup(groupId).getOrNull()

      if (groupRecord != null && !groupRecord.isActive) {
        throw UndeliverableMessageException("Cannot pin messages in an inactive group!")
      }

      if (groupRecord != null && groupRecord.attributesAccessControl == GroupAccessControl.ONLY_ADMINS && !groupRecord.isAdmin(Recipient.self())) {
        throw UndeliverableMessageException("Non-admins cannot pin messages!")
      }
      GroupUtil.setDataMessageGroupContext(AppDependencies.application, builder, groupId)
    }

    val sentTime = System.currentTimeMillis()
    val message = builder
      .withTimestamp(sentTime)
      .withExpiration((message.expiresIn / 1000).toInt())
      .withProfileKey(ProfileKeyUtil.getSelfProfileKey().serialize())
      .withPinnedMessage(
        REDServiceDataMessage.PinnedMessage(
          targetAuthor = ServiceId.parseOrThrow(message.messageExtras!!.pinnedMessage!!.targetAuthorAci),
          targetSentTimestamp = message.messageExtras.pinnedMessage.targetTimestamp,
          pinDurationInSeconds = message.messageExtras.pinnedMessage.pinDurationInSeconds.takeIf { it != MessageTable.PIN_FOREVER }?.toInt(),
          forever = (message.messageExtras.pinnedMessage.pinDurationInSeconds == MessageTable.PIN_FOREVER).takeIf { it }
        )
      )
      .build()

    return if (includeSelf) {
      listOf(AppDependencies.signalServiceMessageSender.sendSyncMessage(message))
    } else {
      GroupSendUtil.sendResendableDataMessage(
        applicationContext,
        groupId,
        null,
        destinations,
        false,
        ContentHint.RESENDABLE,
        MessageId(relatedMessageId),
        message,
        false,
        false,
        null
      ) { System.currentTimeMillis() - sentTime > PIN_TERMINATE_TIMEOUT.inWholeMilliseconds }
    }
  }

  @Throws(IOException::class, GroupNotAMemberException::class, UndeliverableMessageException::class)
  fun sendUnpinMessage(applicationContext: Context, threadRecipient: Recipient, targetAuthor: ServiceId, targetSentTimestamp: Long, destinations: List<Recipient>, includeSelf: Boolean, relatedMessageId: Long): List<SendMessageResult?> {
    val builder = newBuilder()
    val groupId = if (threadRecipient.isPushV2Group) threadRecipient.requireGroupId().requireV2() else null
    if (groupId != null) {
      val groupRecord: GroupRecord? = REDDatabase.groups.getGroup(groupId).getOrNull()

      if (groupRecord != null && !groupRecord.isActive) {
        throw UndeliverableMessageException("Cannot unpin messages in an inactive group!")
      }

      if (groupRecord != null && groupRecord.attributesAccessControl == GroupAccessControl.ONLY_ADMINS && !groupRecord.isAdmin(Recipient.self())) {
        throw UndeliverableMessageException("Non-admins cannot pin messages!")
      }

      GroupUtil.setDataMessageGroupContext(AppDependencies.application, builder, groupId)
    }

    val sentTime = System.currentTimeMillis()
    val message = builder
      .withTimestamp(sentTime)
      .withProfileKey(ProfileKeyUtil.getSelfProfileKey().serialize())
      .withUnpinnedMessage(
        REDServiceDataMessage.UnpinnedMessage(
          targetAuthor = targetAuthor,
          targetSentTimestamp = targetSentTimestamp
        )
      )
      .build()

    return if (includeSelf) {
      listOf(AppDependencies.signalServiceMessageSender.sendSyncMessage(message))
    } else {
      GroupSendUtil.sendResendableDataMessage(
        applicationContext,
        groupId,
        null,
        destinations,
        false,
        ContentHint.RESENDABLE,
        MessageId(relatedMessageId),
        message,
        false,
        false,
        null
      ) { System.currentTimeMillis() - sentTime > PIN_TERMINATE_TIMEOUT.inWholeMilliseconds }
    }
  }
}
