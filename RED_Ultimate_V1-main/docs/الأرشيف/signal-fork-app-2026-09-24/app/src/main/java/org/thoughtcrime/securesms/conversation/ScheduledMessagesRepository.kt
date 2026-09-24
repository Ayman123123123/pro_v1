package com.red.sovereign.conversation

import android.content.Context
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.conversation.v2.data.AttachmentHelper
import com.red.sovereign.database.DatabaseObserver
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.recipients.Recipient

/**
 * Handles retrieving scheduled messages data to be shown in [ScheduledMessagesBottomSheet] and [ConversationParentFragment]
 */
class ScheduledMessagesRepository {

  /**
   * Get all the scheduled messages for the specified thread, ordered by scheduled time
   */
  fun getScheduledMessages(context: Context, threadId: Long): Observable<List<ConversationMessage>> {
    return Observable.create { emitter ->
      val databaseObserver: DatabaseObserver = AppDependencies.databaseObserver
      val observer = DatabaseObserver.Observer { emitter.onNext(getScheduledMessagesSync(context, threadId)) }

      databaseObserver.registerScheduledMessageObserver(threadId, observer)

      emitter.setCancellable { databaseObserver.unregisterObserver(observer) }
      emitter.onNext(getScheduledMessagesSync(context, threadId))
    }.subscribeOn(Schedulers.io())
  }

  @WorkerThread
  private fun getScheduledMessagesSync(context: Context, threadId: Long): List<ConversationMessage> {
    var scheduledMessages: List<MessageRecord> = REDDatabase.messages.getScheduledMessagesInThread(threadId)
    val threadRecipient: Recipient = requireNotNull(REDDatabase.threads.getRecipientForThreadId(threadId))

    val attachmentHelper = AttachmentHelper()

    attachmentHelper.addAll(scheduledMessages)

    attachmentHelper.fetchAttachments()

    scheduledMessages = attachmentHelper.buildUpdatedModels(AppDependencies.application, scheduledMessages)

    val replies: List<ConversationMessage> = scheduledMessages
      .map { ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, it, threadRecipient) }

    return replies
  }

  /**
   * Get the number of scheduled messages for a given thread
   */
  fun getScheduledMessageCount(threadId: Long): Observable<Int> {
    return Observable.create { emitter ->
      val databaseObserver: DatabaseObserver = AppDependencies.databaseObserver
      val observer = DatabaseObserver.Observer { emitter.onNext(REDDatabase.messages.getScheduledMessageCountForThread(threadId)) }

      databaseObserver.registerScheduledMessageObserver(threadId, observer)

      emitter.setCancellable { databaseObserver.unregisterObserver(observer) }
      emitter.onNext(REDDatabase.messages.getScheduledMessageCountForThread(threadId))
    }.subscribeOn(Schedulers.io())
  }

  fun rescheduleMessage(threadId: Long, messageId: Long, scheduleTime: Long) {
    REDDatabase.messages.rescheduleMessage(threadId, messageId, scheduleTime)
  }
}
