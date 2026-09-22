package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import com.red.sovereign.database.GroupTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.database.model.RecipientRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint
import com.red.sovereign.jobs.protos.PollVoteJobData
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.messages.GroupSendUtil
import com.red.sovereign.polls.PollRecord
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.recipients.RecipientUtil
import com.red.sovereign.util.GroupUtil
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage.Companion.newBuilder
import kotlin.time.Duration.Companion.days

/**
 * Sends a poll vote for a given poll in a group. If the vote completely fails to send, we do our best to undo that vote.
 */
class PollVoteJob(
  private val messageId: Long,
  private val recipientIds: MutableList<Long>,
  private val initialRecipientCount: Int,
  private val voteCount: Int,
  private val isRemoval: Boolean,
  private val optionId: Long,
  parameters: Parameters
) : Job(parameters) {

  companion object {
    const val KEY: String = "PollVoteJob"
    private val TAG = Log.tag(PollVoteJob::class.java)

    fun create(messageId: Long, voteCount: Int, isRemoval: Boolean, optionId: Long): PollVoteJob? {
      val message = REDDatabase.messages.getMessageRecordOrNull(messageId)
      if (message == null) {
        Log.w(TAG, "Unable to find corresponding message")
        return null
      }

      val conversationRecipientId = REDDatabase.threads.getRecipientIdForThreadId(message.threadId)
      if (conversationRecipientId == null) {
        Log.w(TAG, "We have a message, but couldn't find the thread!")
        return null
      }

      val conversationRecipient = REDDatabase.recipients.getRecord(conversationRecipientId)
      val groupId = conversationRecipient.groupId

      val recipients = if (groupId != null) {
        REDDatabase.groups.getGroupMemberIds(groupId, GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF).map { it.toLong() }
      } else {
        listOf(conversationRecipient.id.toLong())
      }

      return PollVoteJob(
        messageId = messageId,
        recipientIds = recipients.toMutableList(),
        initialRecipientCount = recipients.size,
        voteCount = voteCount,
        isRemoval = isRemoval,
        optionId = optionId,
        parameters = Parameters.Builder()
          .setQueue(conversationRecipient.id.toQueueKey())
          .addConstraint(NetworkConstraint.KEY)
          .addConstraint(SealedSenderConstraint.KEY)
          .setMaxAttempts(Parameters.UNLIMITED)
          .setLifespan(1.days.inWholeMilliseconds)
          .build()
      )
    }
  }

  override fun serialize(): ByteArray {
    return PollVoteJobData(messageId, recipientIds, initialRecipientCount, voteCount, isRemoval, optionId).encode()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    if (!REDStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.failure()
    }

    val message = REDDatabase.messages.getMessageRecordOrNull(messageId)
    if (message == null) {
      Log.w(TAG, "Unable to find corresponding message")
      return Result.failure()
    }

    val conversationRecipientId = REDDatabase.threads.getRecipientIdForThreadId(message.threadId)
    if (conversationRecipientId == null) {
      Log.w(TAG, "We have a message, but couldn't find the thread!")
      return Result.failure()
    }

    val conversationRecipient = REDDatabase.recipients.getRecord(conversationRecipientId)
    val groupId = conversationRecipient.groupId

    if (groupId != null && groupId.isV2 && !REDDatabase.groups.isActive(groupId)) {
      Log.w(TAG, "Cannot send poll vote to terminated or inactive group.")
      return Result.failure()
    }

    val poll = REDDatabase.polls.getPoll(messageId)
    if (poll == null) {
      Log.w(TAG, "Unable to find corresponding poll")
      return Result.failure()
    }

    val targetAuthor = message.fromRecipient
    if (targetAuthor == null || !targetAuthor.hasServiceId) {
      Log.w(TAG, "Unable to find target author")
      return Result.failure()
    }

    val targetSentTimestamp = message.dateSent

    val recipients = Recipient.resolvedList(recipientIds.map { RecipientId.from(it) })
    val registered = RecipientUtil.getEligibleForSending(recipients)
    val unregistered = recipients - registered.toSet()
    val completions: List<Recipient> = deliver(conversationRecipient, registered, targetAuthor, targetSentTimestamp, poll)

    recipientIds.removeAll(unregistered.map { it.id.toLong() })
    recipientIds.removeAll(completions.map { it.id.toLong() })

    Log.i(TAG, "Completed now: " + completions.size + ", Remaining: " + recipientIds.size)

    if (recipientIds.isNotEmpty()) {
      Log.w(TAG, "Still need to send to " + recipientIds.size + " recipients. Retrying.")
      return Result.retry(defaultBackoff())
    }

    return Result.success()
  }

  private fun deliver(conversationRecipient: RecipientRecord, destinations: List<Recipient>, targetAuthor: Recipient, targetSentTimestamp: Long, poll: PollRecord): List<Recipient> {
    val votes = REDDatabase.polls.getVotes(poll.id, poll.allowMultipleVotes, voteCount)

    val dataMessageBuilder = newBuilder()
      .withTimestamp(System.currentTimeMillis())
      .withPollVote(
        buildPollVote(
          targetAuthor = targetAuthor,
          targetSentTimestamp = targetSentTimestamp,
          optionIndexes = votes,
          voteCount = voteCount
        )
      )

    val groupId = conversationRecipient.groupId
    if (groupId != null && groupId.isV2) {
      GroupUtil.setDataMessageGroupContext(context, dataMessageBuilder, groupId.requirePush())
    }

    val dataMessage = dataMessageBuilder.build()
    val nonSelfDestinations = destinations.filter { !it.isSelf }

    val results = GroupSendUtil.sendResendableDataMessage(
      context,
      groupId?.requireV2(),
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
    )

    if (conversationRecipient.id == Recipient.self().id) {
      results.add(AppDependencies.signalServiceMessageSender.sendSyncMessage(dataMessage))
    }

    val groupResult = GroupSendJobHelper.getCompletedSends(destinations, results)

    for (unregistered in groupResult.unregistered) {
      REDDatabase.recipients.markUnregistered(unregistered)
    }

    if (groupResult.completed.isNotEmpty() || destinations.isEmpty()) {
      if (isRemoval) {
        REDDatabase.polls.markPendingAsRemoved(
          pollId = poll.id,
          voterId = Recipient.self().id.toLong(),
          voteCount = voteCount,
          messageId = poll.messageId,
          optionId = optionId
        )
      } else {
        REDDatabase.polls.markPendingAsAdded(
          pollId = poll.id,
          voterId = Recipient.self().id.toLong(),
          voteCount = voteCount,
          messageId = poll.messageId,
          optionId = optionId
        )
      }
    }

    return groupResult.completed
  }

  override fun onFailure() {
    if (recipientIds.size < initialRecipientCount) {
      Log.w(TAG, "Only sent vote to " + recipientIds.size + "/" + initialRecipientCount + " recipients. Still, it sent to someone, so it stays.")
      return
    }

    Log.w(TAG, "Failed to send to all recipients!")

    val pollId = REDDatabase.polls.getPollId(messageId)
    if (pollId == null) {
      Log.w(TAG, "Poll no longer exists")
      return
    }

    REDDatabase.polls.removePendingVote(pollId, optionId, voteCount, messageId)
  }

  private fun buildPollVote(
    targetAuthor: Recipient,
    targetSentTimestamp: Long,
    optionIndexes: List<Int>,
    voteCount: Int
  ): REDServiceDataMessage.PollVote {
    return REDServiceDataMessage.PollVote(
      targetAuthor = targetAuthor.requireServiceId(),
      targetSentTimestamp = targetSentTimestamp,
      optionIndexes = optionIndexes,
      voteCount = voteCount
    )
  }

  class Factory : Job.Factory<PollVoteJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PollVoteJob {
      val data = PollVoteJobData.ADAPTER.decode(serializedData!!)

      return PollVoteJob(
        messageId = data.messageId,
        recipientIds = data.recipients.toMutableList(),
        initialRecipientCount = data.initialRecipientCount,
        voteCount = data.voteCount,
        isRemoval = data.isRemoval,
        optionId = data.optionId,
        parameters = parameters
      )
    }
  }
}
