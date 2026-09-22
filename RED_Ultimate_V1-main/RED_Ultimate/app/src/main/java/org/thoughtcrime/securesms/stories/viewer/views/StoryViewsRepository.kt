package com.red.sovereign.stories.viewer.views

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import com.red.sovereign.database.DatabaseObserver
import com.red.sovereign.database.GroupReceiptTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import org.whispersystems.signalservice.api.push.DistributionId

class StoryViewsRepository {

  companion object {
    private val TAG = Log.tag(StoryViewsRepository::class.java)
  }

  fun isReadReceiptsEnabled(): Boolean = REDStore.story.viewedReceiptsEnabled

  fun getStoryRecipient(storyId: Long): Single<Recipient> {
    return Single.fromCallable {
      REDDatabase.messages.getMessageRecord(storyId).toRecipient
    }.subscribeOn(Schedulers.io())
  }

  fun getViews(storyId: Long): Observable<List<StoryViewItemData>> {
    return Observable.create<List<StoryViewItemData>> { emitter ->
      val record: MessageRecord = REDDatabase.messages.getMessageRecord(storyId)
      val filterIds: Set<RecipientId> = if (record.toRecipient.isDistributionList) {
        val distributionId: DistributionId = REDDatabase.distributionLists.getDistributionId(record.toRecipient.requireDistributionListId())!!
        REDDatabase.storySends.getRecipientsForDistributionId(storyId, distributionId)
      } else {
        emptySet()
      }

      fun refresh() {
        emitter.onNext(
          REDDatabase.groupReceipts.getGroupReceiptInfo(storyId).filter {
            it.status == GroupReceiptTable.STATUS_VIEWED
          }.filter {
            filterIds.isEmpty() || it.recipientId in filterIds
          }.map {
            StoryViewItemData(
              recipient = Recipient.resolved(it.recipientId),
              timeViewedInMillis = it.timestamp
            )
          }
        )
      }

      val observer = DatabaseObserver.MessageObserver { refresh() }

      AppDependencies.databaseObserver.registerMessageUpdateObserver(observer)
      emitter.setCancellable {
        AppDependencies.databaseObserver.unregisterObserver(observer)
      }

      refresh()
    }.subscribeOn(Schedulers.io())
  }

  fun removeUserFromStory(user: Recipient, story: Recipient): Completable {
    return Completable.fromAction {
      val distributionListRecord = REDDatabase.distributionLists.getList(story.requireDistributionListId())!!
      if (user.id in distributionListRecord.members) {
        REDDatabase.distributionLists.excludeFromStory(user.id, distributionListRecord)
      } else {
        Log.w(TAG, "User is no longer in the distribution list.")
      }
    }
  }
}
