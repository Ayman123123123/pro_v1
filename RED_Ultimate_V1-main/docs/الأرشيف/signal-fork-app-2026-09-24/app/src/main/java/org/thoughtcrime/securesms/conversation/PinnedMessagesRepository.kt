package com.red.sovereign.conversation

import android.app.Application
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import com.red.sovereign.conversation.ConversationMessage.ConversationMessageFactory
import com.red.sovereign.conversation.v2.data.AttachmentHelper
import com.red.sovereign.conversation.v2.data.ReactionHelper
import com.red.sovereign.database.DatabaseObserver
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.dependencies.AppDependencies

/**
 * Repository when getting the pinned messages shown in the pinned message bottom sheet
 */
class PinnedMessagesRepository {

  companion object {
    private val TAG = Log.tag(PinnedMessagesRepository::class.java)
  }

  fun getPinnedMessage(application: Application, threadId: Long): Observable<List<ConversationMessage>> {
    return Observable.create { emitter ->
      val databaseObserver: DatabaseObserver = AppDependencies.databaseObserver
      val observer = DatabaseObserver.Observer { emitter.onNext(getPinnedMessages(application, threadId)) }
      databaseObserver.registerConversationObserver(threadId, observer)
      emitter.setCancellable { databaseObserver.unregisterObserver(observer) }

      emitter.onNext(getPinnedMessages(application, threadId))
    }.subscribeOn(Schedulers.io())
  }

  fun getPinnedMessageRecords(threadId: Long): List<MessageRecord> {
    return REDDatabase.messages.getPinnedMessages(threadId = threadId, orderByPinned = false)
  }

  private fun getPinnedMessages(application: Application, threadId: Long): List<ConversationMessage> {
    var records: List<MessageRecord> = getPinnedMessageRecords(threadId)

    val reactionHelper = ReactionHelper()
    val attachmentHelper = AttachmentHelper()
    val threadRecipient = requireNotNull(REDDatabase.threads.getRecipientForThreadId(threadId))

    reactionHelper.addAll(records)
    attachmentHelper.addAll(records)

    reactionHelper.fetchReactions()
    attachmentHelper.fetchAttachments()

    records = reactionHelper.buildUpdatedModels(records)
    records = attachmentHelper.buildUpdatedModels(AppDependencies.application, records)

    return records.map { ConversationMessageFactory.createWithUnresolvedData(application, it, threadRecipient) }
  }
}
