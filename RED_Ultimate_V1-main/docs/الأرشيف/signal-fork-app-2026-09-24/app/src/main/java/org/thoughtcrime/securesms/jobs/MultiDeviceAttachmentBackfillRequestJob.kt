/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint
import com.red.sovereign.jobs.protos.MultiDeviceAttachmentBackfillRequestJobData
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.multidevice.REDServiceSyncMessage
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import org.whispersystems.signalservice.internal.push.AddressableMessage
import org.whispersystems.signalservice.internal.push.ConversationIdentifier
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * Sent by a linked device to request the primary to re-upload attachments when we fail
 * to restore them automatically but the user has specifically requested we retry.
 */
class MultiDeviceAttachmentBackfillRequestJob private constructor(
  parameters: Parameters,
  private val messageId: Long
) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(MultiDeviceAttachmentBackfillRequestJob::class)

    const val KEY = "MultiDeviceAttachmentBackfillRequestJob"
  }

  constructor(messageId: Long) : this(
    Parameters.Builder()
      .setLifespan(30.seconds.inWholeMilliseconds)
      .setMaxAttempts(3)
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .build(),
    messageId
  )

  override fun getFactoryKey(): String = KEY

  override fun serialize(): ByteArray {
    return MultiDeviceAttachmentBackfillRequestJobData(messageId = messageId).encode()
  }

  override fun run(): Result {
    if (REDStore.account.isPrimaryDevice) {
      Log.w(TAG, "Primary device should never request attachment backfill. Dropping.")
      return Result.failure()
    }

    val record = REDDatabase.messages.getMessageRecordOrNull(messageId)
    if (record == null) {
      Log.w(TAG, "[$messageId] No message record; cannot build backfill request.")
      return Result.failure()
    }

    val author = record.fromRecipient
    val authorServiceId = author.aci.orNull()?.toByteString()
    if (authorServiceId == null) {
      Log.w(TAG, "[$messageId] No serviceId for author ${author.id}; cannot build backfill request.")
      return Result.failure()
    }

    val threadRecipient = REDDatabase.threads.getRecipientForThreadId(record.threadId)
    if (threadRecipient == null) {
      Log.w(TAG, "[$messageId] No thread recipient for thread ${record.threadId}; cannot build backfill request.")
      return Result.failure()
    }

    val conversation = threadRecipient.toBackfillConversationId()
    if (conversation == null) {
      Log.w(TAG, "[$messageId] No conversation identifier for recipient ${threadRecipient.id}; cannot build backfill request.")
      return Result.failure()
    }

    val request = SyncMessage.AttachmentBackfillRequest(
      targetMessage = AddressableMessage(
        authorServiceIdBinary = authorServiceId,
        sentTimestamp = record.dateSent
      ),
      targetConversation = conversation
    )

    val syncMessage = REDServiceSyncMessage.forAttachmentBackfillRequest(request)

    return try {
      val result = AppDependencies.signalServiceMessageSender.sendSyncMessage(syncMessage)
      if (result.isSuccess) {
        Log.i(TAG, "[${record.dateSent}] Sent attachment backfill request.")
        Result.success()
      } else {
        Log.w(TAG, "[${record.dateSent}] Non-success send result; retrying.")
        Result.retry(defaultBackoff())
      }
    } catch (e: ServerRejectedException) {
      Log.w(TAG, e)
      Result.failure()
    } catch (e: IOException) {
      Log.w(TAG, e)
      Result.retry(defaultBackoff())
    } catch (e: UntrustedIdentityException) {
      Log.w(TAG, e)
      Result.failure()
    }
  }

  override fun onFailure() = Unit

  private fun Recipient.toBackfillConversationId(): ConversationIdentifier? {
    return when {
      this.isGroup -> ConversationIdentifier(threadGroupId = this.requireGroupId().decodedId.toByteString())
      this.hasAci -> ConversationIdentifier(threadServiceIdBinary = this.requireAci().toByteString())
      this.hasPni -> ConversationIdentifier(threadServiceIdBinary = this.requirePni().toByteString())
      this.hasE164 -> ConversationIdentifier(threadE164 = this.requireE164())
      else -> null
    }
  }

  class Factory : Job.Factory<MultiDeviceAttachmentBackfillRequestJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceAttachmentBackfillRequestJob {
      val data = MultiDeviceAttachmentBackfillRequestJobData.ADAPTER.decode(serializedData!!)
      return MultiDeviceAttachmentBackfillRequestJob(parameters, data.messageId)
    }
  }
}
