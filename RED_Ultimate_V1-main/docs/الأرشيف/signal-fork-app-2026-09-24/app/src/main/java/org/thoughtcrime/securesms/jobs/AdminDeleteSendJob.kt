package com.red.sovereign.jobs

import org.signal.core.models.ServiceId
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import com.red.sovereign.database.GroupTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.documents.NetworkFailure
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.database.model.RecipientRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint
import com.red.sovereign.jobs.protos.AdminDeleteJobData
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.messages.GroupSendUtil
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.recipients.RecipientUtil
import com.red.sovereign.util.GroupUtil
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage.Companion.newBuilder
import kotlin.time.Duration.Companion.days

/**
 * Job used when an admin deletes a message in a group
 */
class AdminDeleteSendJob private constructor(
  private val messageId: Long,
  private val recipientIds: MutableList<Long>,
  private val initialRecipientCount: Int,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY: String = "AdminDeleteSendJob"

    private val TAG = tag(AdminDeleteSendJob::class.java)

    @JvmStatic
    fun create(messageId: Long, filterRecipients: List<RecipientId>): AdminDeleteSendJob? {
      val message = REDDatabase.messages.getMessageRecordOrNull(messageId)
      if (message == null) {
        return null
      }

      val conversationRecipientId = REDDatabase.threads.getRecipientIdForThreadId(message.threadId)

      if (conversationRecipientId == null) {
        return null
      }

      val conversationRecipient = REDDatabase.recipients.getRecord(conversationRecipientId)
      val groupId = conversationRecipient.groupId
      val members = if (groupId != null) REDDatabase.groups.getGroupMemberIds(groupId, GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF) else emptyList()

      val recipientIds = filterRecipients.ifEmpty { members }.map { it.toLong() }.toMutableList()

      return AdminDeleteSendJob(
        messageId = messageId,
        recipientIds = recipientIds,
        initialRecipientCount = recipientIds.size,
        parameters = Parameters.Builder()
          .setQueue(conversationRecipient.id.toQueueKey())
          .addConstraint(NetworkConstraint.KEY)
          .addConstraint(SealedSenderConstraint.KEY)
          .setLifespan(1.days.inWholeMilliseconds)
          .setMaxAttempts(Parameters.UNLIMITED)
          .build()
      )
    }
  }

  override fun serialize(): ByteArray? {
    return AdminDeleteJobData(messageId, recipientIds, initialRecipientCount).encode()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    if (!REDStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.failure()
    }

    val message = REDDatabase.messages.getMessageRecord(messageId)
    if (!message.fromRecipient.hasServiceId) {
      Log.w(TAG, "Missing service id for the target author.")
      return Result.failure()
    }

    val existingNetworkFailures = message.networkFailures.toMutableSet()
    val existingIdentityMismatches = message.identityKeyMismatches.toMutableSet()
    val targets = (recipientIds + existingIdentityMismatches.map { it.recipientId.toLong() } + existingNetworkFailures.map { it.recipientId.toLong() }).toSet()

    val recipients = targets.map { Recipient.resolved(RecipientId.from(it)) }.toMutableList()
    val targetSentTimestamp = message.dateSent
    val targetAuthor = message.fromRecipient.requireServiceId()

    val conversationRecipientId = REDDatabase.threads.getRecipientIdForThreadId(message.threadId)
    if (conversationRecipientId == null) {
      Log.w(TAG, "We have a message, but couldn't find the thread!")
      return Result.failure()
    }

    val conversationRecipient = REDDatabase.recipients.getRecord(conversationRecipientId)
    val groupId = conversationRecipient.groupId

    if (groupId == null || !groupId.isV2) {
      Log.w(TAG, "Cannot admin delete in a non V2 group.")
      return Result.failure()
    }

    val groupRecord = REDDatabase.groups.getGroup(groupId)
    if (groupRecord.isPresent && groupRecord.get().isTerminated) {
      Log.w(TAG, "Cannot admin delete in a terminated group.")
      return Result.failure()
    }

    if (groupRecord.isEmpty || !groupRecord.get().isAdmin(Recipient.self())) {
      Log.w(TAG, "Cannot delete because you are not an admin.")
      return Result.failure()
    }

    val eligible = RecipientUtil.getEligibleForSending(recipients.filter { it.hasServiceId })
    val ineligibleRecipients = recipients - eligible
    val sendResult = deliver(conversationRecipient, eligible, targetAuthor, targetSentTimestamp)

    val completedIds = sendResult.completed.map { it.id }.toSet()
    existingNetworkFailures.removeAll { completedIds.contains(it.recipientId) }
    existingIdentityMismatches.removeAll { completedIds.contains(it.recipientId) }

    val ineligibleIds = (ineligibleRecipients.map { it.id } + sendResult.unregistered).toSet()
    existingNetworkFailures.removeAll { ineligibleIds.contains(it.recipientId) }
    existingIdentityMismatches.removeAll { ineligibleIds.contains(it.recipientId) }

    existingIdentityMismatches.addAll(sendResult.identityMismatch)

    REDDatabase.messages.setNetworkFailures(messageId, existingNetworkFailures)
    REDDatabase.messages.setMismatchedIdentities(messageId, existingIdentityMismatches)

    for (completion in sendResult.completed) {
      recipientIds.remove(completion.id.toLong())
    }

    for (unregistered in sendResult.unregistered) {
      REDDatabase.recipients.markUnregistered(unregistered)
    }

    for (recipient in ineligibleRecipients) {
      recipientIds.remove(recipient.id.toLong())
    }

    Log.i(TAG, "Completed now: ${sendResult.completed.size} Skipped: ${ineligibleRecipients.size + sendResult.skipped.size} Remaining: ${recipientIds.size}")

    if (existingNetworkFailures.isEmpty() && existingIdentityMismatches.isEmpty() && recipientIds.isEmpty()) {
      REDDatabase.messages.markAsSentAdminDelete(messageId)
      return Result.success()
    } else if (existingIdentityMismatches.isNotEmpty()) {
      Log.w(TAG, "Failing because there were ${existingIdentityMismatches.size} identity mismatches.")
      return Result.failure()
    } else {
      Log.w(TAG, "Still need to send to ${recipientIds.size} recipients. Retrying.")
      return Result.retry(defaultBackoff())
    }
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to send admin delete to all recipients! ${initialRecipientCount - recipientIds.size} /  $initialRecipientCount. Marking remaining non-identity mismatched failures as network failure.")
    val message = REDDatabase.messages.getMessageRecordOrNull(messageId)
    if (message == null) {
      Log.w(TAG, "Message no longer exists, ignoring.")
    } else {
      val existingIdentityMismatches = message.identityKeyMismatches.map { it.recipientId.toLong() }
      recipientIds.removeAll { existingIdentityMismatches.contains(it) }
      REDDatabase.messages.setNetworkFailures(messageId, recipientIds.map { NetworkFailure(RecipientId.from(it)) }.toSet())
      REDDatabase.messages.markAsFailedAdminDelete(messageId)
    }
  }

  private fun deliver(
    conversationRecipient: RecipientRecord,
    destinations: MutableList<Recipient>,
    targetAuthor: ServiceId,
    targetSentTimestamp: Long
  ): GroupSendJobHelper.SendResult {
    val dataMessageBuilder = newBuilder()
      .withTimestamp(System.currentTimeMillis())
      .withAdminDelete(REDServiceDataMessage.AdminDelete(targetAuthor, targetSentTimestamp))

    val groupId = conversationRecipient.groupId!!
    GroupUtil.setDataMessageGroupContext(context, dataMessageBuilder, groupId.requirePush())

    val nonSelfDestinations = destinations.filterNot { it.isSelf }
    val includeSelf = destinations.size != nonSelfDestinations.size

    val dataMessage = dataMessageBuilder.build()

    val results = GroupSendUtil.sendResendableDataMessage(
      context,
      groupId.requireV2(),
      null,
      nonSelfDestinations,
      false,
      ContentHint.RESENDABLE,
      MessageId(messageId),
      dataMessage,
      true,
      false,
      null,
      null
    ).toMutableList()

    if (includeSelf) {
      results.add(AppDependencies.signalServiceMessageSender.sendSyncMessage(dataMessage))
    }

    return GroupSendJobHelper.getCompletedSends(destinations, results)
  }

  class Factory : Job.Factory<AdminDeleteSendJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AdminDeleteSendJob {
      val data = AdminDeleteJobData.ADAPTER.decode(serializedData!!)

      return AdminDeleteSendJob(
        messageId = data.messageId,
        recipientIds = data.recipientIds.toMutableList(),
        initialRecipientCount = data.initialRecipientCount,
        parameters = parameters
      )
    }
  }
}
