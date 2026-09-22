package com.red.sovereign.starred

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import com.red.sovereign.conversation.ConversationMessage
import com.red.sovereign.database.RxDatabaseObserver
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.recipients.Recipient

class StarredMessagesViewModel(
  private val threadId: Long?
) : ViewModel() {

  fun getMessages(): Flow<List<ConversationMessage>> {
    val trigger = if (threadId != null) {
      RxDatabaseObserver.conversation(threadId)
    } else {
      RxDatabaseObserver.starredMessages
    }

    return trigger.toObservable().asFlow()
      .map {
        val messages = REDDatabase.messages.getStarredMessages(threadId)
        messages.map { record ->
          val incomingRecord = if (record is MmsMessageRecord && record.isOutgoing) {
            record.withIncomingType()
          } else {
            record
          }
          val threadRecipient = REDDatabase.threads.getRecipientForThreadId(record.threadId) ?: Recipient.UNKNOWN
          ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(
            AppDependencies.application,
            incomingRecord,
            threadRecipient
          )
        }
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
  }

  suspend fun unstarMessage(messageId: Long) {
    withContext(Dispatchers.Default) {
      REDDatabase.messages.setStarred(messageId, false)
    }
  }

  class Factory(
    private val threadId: Long?
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return StarredMessagesViewModel(threadId) as T
    }
  }
}
