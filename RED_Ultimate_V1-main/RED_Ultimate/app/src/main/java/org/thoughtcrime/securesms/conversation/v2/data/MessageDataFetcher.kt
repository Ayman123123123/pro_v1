/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.v2.data

import androidx.annotation.WorkerThread
import org.signal.core.util.UuidUtil
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.roundedString
import com.red.sovereign.attachments.DatabaseAttachment
import com.red.sovereign.database.CallTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.Mention
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.model.ReactionRecord
import com.red.sovereign.database.model.withAttachments
import com.red.sovereign.database.model.withCall
import com.red.sovereign.database.model.withPayment
import com.red.sovereign.database.model.withPoll
import com.red.sovereign.database.model.withReactions
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.memberlabel.MemberLabel
import com.red.sovereign.groups.memberlabel.MemberLabelRepository
import com.red.sovereign.payments.Payment
import com.red.sovereign.polls.PollRecord
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

/**
 * Fetches various pieces of associated message data in parallel and returns the result.
 */
object MessageDataFetcher {

  /**
   * Singular version of [fetch].
   */
  fun fetch(messageRecord: MessageRecord, threadRecipient: Recipient? = null): ExtraMessageData {
    return fetch(listOf(messageRecord), threadRecipient)
  }

  /**
   * Fetches all associated message data in parallel.
   * It also performs a side-effect of resolving recipients referenced in group update messages.
   *
   * While work is spun off on various threads, the calling thread is blocked until they all complete,
   * so this should be called on a background thread.
   */
  @WorkerThread
  fun fetch(messageRecords: List<MessageRecord>, threadRecipient: Recipient? = null): ExtraMessageData {
    val startTimeNanos = System.nanoTime()
    val context = AppDependencies.application

    val messageIds: List<Long> = messageRecords.map { it.id }
    val executor = REDExecutors.BOUNDED

    val mentionsFuture = executor.submitTimed {
      REDDatabase.mentions.getMentionsForMessages(messageIds)
    }

    val hasBeenQuotedFuture = executor.submitTimed {
      REDDatabase.messages.isQuoted(messageRecords)
    }

    val reactionsFuture = executor.submitTimed {
      REDDatabase.reactions.getReactionsForMessages(messageIds)
    }

    val attachmentsFuture = executor.submitTimed {
      REDDatabase.attachments.getAttachmentsForMessages(messageIds)
    }

    val paymentsFuture = executor.submitTimed {
      val paymentUuidToMessageId: Map<UUID, Long> = messageRecords
        .filter { it.isMms && it.isPaymentNotification }
        .map { UuidUtil.parseOrNull(it.body) to it.id }
        .filter { it.first != null }
        .associate { it.first!! to it.second }

      REDDatabase
        .payments
        .getPayments(paymentUuidToMessageId.keys)
        .associateBy { paymentUuidToMessageId[it.uuid]!! }
    }

    val callsFuture = executor.submitTimed {
      REDDatabase.calls.getCallsForCache(messageIds)
    }

    val recipientsFuture = executor.submitTimed {
      messageRecords.forEach { record ->
        record.getUpdateDisplayBody(context, null)?.let { description ->
          val ids = description.mentioned.map { RecipientId.from(it) }
          Recipient.resolvedList(ids)
        }
      }
    }

    val pollsFuture = executor.submitTimed {
      REDDatabase.polls.getPollsForMessages(messageIds)
    }

    val memberLabelsFuture = if (threadRecipient != null && threadRecipient.isPushV2Group) {
      executor.submitTimed {
        val fromRecipients = mutableSetOf<Recipient>()
        val quoteRecipientIds = mutableSetOf<RecipientId>()
        for (record in messageRecords) {
          if (!record.isOutgoing) {
            fromRecipients.add(record.fromRecipient)
          }
          if (record is MmsMessageRecord && record.quote != null) {
            quoteRecipientIds.add(record.quote!!.author)
          }
        }
        val recipients = fromRecipients + Recipient.resolvedList(quoteRecipientIds)
        MemberLabelRepository.instance.getLabelsSync(threadRecipient.requireGroupId().requireV2(), recipients)
      }
    } else {
      null
    }

    val mentionsResult = mentionsFuture.get()
    val hasBeenQuotedResult = hasBeenQuotedFuture.get()
    val reactionsResult = reactionsFuture.get()
    val attachmentsResult = attachmentsFuture.get()
    val paymentsResult = paymentsFuture.get()
    val callsResult = callsFuture.get()
    val recipientsResult = recipientsFuture.get()
    val pollsResult = pollsFuture.get()
    val memberLabelsResult = memberLabelsFuture?.get()

    val wallTimeMs = (System.nanoTime() - startTimeNanos).nanoseconds.toDouble(DurationUnit.MILLISECONDS)

    val cpuTimeNanos = arrayOf(mentionsResult, hasBeenQuotedResult, reactionsResult, attachmentsResult, paymentsResult, callsResult, recipientsResult).sumOf { it.durationNanos } + (memberLabelsResult?.durationNanos ?: 0)
    val cpuTimeMs = cpuTimeNanos.nanoseconds.toDouble(DurationUnit.MILLISECONDS)

    return ExtraMessageData(
      mentionsById = mentionsResult.result,
      hasBeenQuoted = hasBeenQuotedResult.result,
      reactions = reactionsResult.result,
      attachments = attachmentsResult.result,
      payments = paymentsResult.result,
      calls = callsResult.result,
      polls = pollsResult.result,
      memberLabels = memberLabelsResult?.result,
      timeLog = "mentions: ${mentionsResult.duration}, is-quoted: ${hasBeenQuotedResult.duration}, reactions: ${reactionsResult.duration}, attachments: ${attachmentsResult.duration}, payments: ${paymentsResult.duration}, calls: ${callsResult.duration}, member-labels: ${memberLabelsResult?.duration ?: "n/a"} >> cpuTime: ${cpuTimeMs.roundedString(2)}, wallTime: ${wallTimeMs.roundedString(2)}"
    )
  }

  /**
   * Merges the data in [ExtraMessageData] into the provided list of [MessageRecord], outputted as
   * a new list of models.
   */
  fun updateModelsWithData(messageRecords: List<MessageRecord>, data: ExtraMessageData): List<MessageRecord> {
    return messageRecords.map { it.updateWithData(data) }
  }

  /**
   * Singular version of [updateModelsWithData]
   */
  fun updateModelWithData(messageRecord: MessageRecord, data: ExtraMessageData): MessageRecord {
    return listOf(messageRecord).map { it.updateWithData(data) }.first()
  }

  private fun MessageRecord.updateWithData(data: ExtraMessageData): MessageRecord {
    var output: MessageRecord = this

    output = data.reactions[id]?.let {
      output.withReactions(it)
    } ?: output

    output = data.attachments[id]?.let {
      output.withAttachments(it)
    } ?: output

    output = data.payments[id]?.let {
      output.withPayment(it)
    } ?: output

    output = data.calls[id]?.let {
      output.withCall(it)
    } ?: output

    output = data.polls[id]?.let {
      output.withPoll(it)
    } ?: output

    return output
  }

  private fun <T> ExecutorService.submitTimed(callable: Callable<T>): Future<TimedResult<T>> {
    return this.submit(
      Callable {
        val start = System.nanoTime()
        val result = callable.call()
        val end = System.nanoTime()

        TimedResult(result = result, durationNanos = end - start)
      }
    )
  }

  data class TimedResult<T>(
    val result: T,
    val durationNanos: Long
  ) {
    val duration: String
      get() = durationNanos.nanoseconds.toDouble(DurationUnit.MILLISECONDS).roundedString(2)
  }

  data class ExtraMessageData(
    val mentionsById: Map<Long, List<Mention>>,
    val hasBeenQuoted: Set<Long>,
    val reactions: Map<Long, List<ReactionRecord>>,
    val attachments: Map<Long, List<DatabaseAttachment>>,
    val payments: Map<Long, Payment>,
    val calls: Map<Long, CallTable.Call>,
    val polls: Map<Long, PollRecord>,
    val memberLabels: Map<RecipientId, MemberLabel>?,
    val timeLog: String
  )
}
