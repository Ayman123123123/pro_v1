package com.red.sovereign.stories.settings.custom

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.DistributionListId
import com.red.sovereign.database.model.DistributionListRecord
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.sms.MessageSender
import com.red.sovereign.stories.Stories

class PrivateStorySettingsRepository {
  fun getRecord(distributionListId: DistributionListId): Single<DistributionListRecord> {
    return Single.fromCallable {
      REDDatabase.distributionLists.getList(distributionListId) ?: error("Record does not exist.")
    }.subscribeOn(Schedulers.io())
  }

  fun removeMember(distributionListRecord: DistributionListRecord, member: RecipientId): Completable {
    return Completable.fromAction {
      REDDatabase.distributionLists.removeMemberFromList(distributionListRecord.id, distributionListRecord.privacyMode, member)
      Stories.onStorySettingsChanged(distributionListRecord.id)
    }.subscribeOn(Schedulers.io())
  }

  fun delete(distributionListId: DistributionListId): Completable {
    return Completable.fromAction {
      REDDatabase.distributionLists.deleteList(distributionListId)
      Stories.onStorySettingsChanged(distributionListId)

      val recipientId = REDDatabase.recipients.getOrInsertFromDistributionListId(distributionListId)
      REDDatabase.messages.getAllStoriesFor(recipientId, -1).use { reader ->
        for (record in reader) {
          MessageSender.sendRemoteDelete(record.id)
        }
      }
    }.subscribeOn(Schedulers.io())
  }

  fun getRepliesAndReactionsEnabled(distributionListId: DistributionListId): Single<Boolean> {
    return Single.fromCallable {
      REDDatabase.distributionLists.getStoryType(distributionListId).isStoryWithReplies
    }.subscribeOn(Schedulers.io())
  }

  fun setRepliesAndReactionsEnabled(distributionListId: DistributionListId, repliesAndReactionsEnabled: Boolean): Completable {
    return Completable.fromAction {
      REDDatabase.distributionLists.setAllowsReplies(distributionListId, repliesAndReactionsEnabled)
      Stories.onStorySettingsChanged(distributionListId)
    }.subscribeOn(Schedulers.io())
  }
}
