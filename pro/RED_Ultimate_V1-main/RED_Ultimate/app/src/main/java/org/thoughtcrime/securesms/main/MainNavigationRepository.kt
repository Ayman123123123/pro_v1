package com.red.sovereign.main

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import com.red.sovereign.database.RxDatabaseObserver
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.recipients.Recipient

object MainNavigationRepository {

  fun getNumberOfUnreadMessages(): Flow<Long> {
    return RxDatabaseObserver.conversationList.map { REDDatabase.threads.getUnreadMessageCount() }.asFlow()
  }

  fun getNumberOfUnseenStories(): Flow<Long> {
    return RxDatabaseObserver.conversationList.map {
      REDDatabase
        .messages
        .getUnreadStoryThreadRecipientIds()
        .map { Recipient.resolved(it) }
        .filterNot { it.shouldHideStory }
        .size
        .toLong()
    }.asFlow()
  }

  fun getHasFailedOutgoingStories(): Flow<Boolean> {
    return RxDatabaseObserver.conversationList.map { REDDatabase.messages.hasFailedOutgoingStory() }.asFlow()
  }

  fun getNumberOfUnseenCalls(): Flow<Long> {
    return RxDatabaseObserver.conversationList.map { REDDatabase.calls.getUnreadMissedCallCount() }.asFlow()
  }
}
