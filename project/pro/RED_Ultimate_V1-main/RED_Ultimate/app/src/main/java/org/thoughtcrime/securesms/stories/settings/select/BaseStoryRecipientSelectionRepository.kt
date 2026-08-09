package com.red.sovereign.stories.settings.select

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.CursorUtil
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.DistributionListId
import com.red.sovereign.database.model.DistributionListRecord
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.stories.Stories

class BaseStoryRecipientSelectionRepository {

  fun getRecord(distributionListId: DistributionListId): Single<DistributionListRecord> {
    return Single.fromCallable {
      REDDatabase.distributionLists.getList(distributionListId) ?: error("Record does not exist.")
    }.subscribeOn(Schedulers.io())
  }

  fun updateDistributionListMembership(distributionListRecord: DistributionListRecord, recipients: Set<RecipientId>) {
    REDExecutors.BOUNDED.execute {
      val currentRecipients = REDDatabase.distributionLists.getRawMembers(distributionListRecord.id, distributionListRecord.privacyMode).toSet()
      val oldNotNew = currentRecipients - recipients
      val newNotOld = recipients - currentRecipients

      oldNotNew.forEach {
        REDDatabase.distributionLists.removeMemberFromList(distributionListRecord.id, distributionListRecord.privacyMode, it)
      }

      newNotOld.forEach {
        REDDatabase.distributionLists.addMemberToList(distributionListRecord.id, distributionListRecord.privacyMode, it)
      }

      Stories.onStorySettingsChanged(distributionListRecord.id)
    }
  }

  fun getAllREDContacts(): Single<Set<RecipientId>> {
    return Single.fromCallable {
      REDDatabase.recipients.getREDContacts(RecipientTable.IncludeSelfMode.Exclude).use {
        val recipientSet = mutableSetOf<RecipientId>()
        while (it.moveToNext()) {
          recipientSet.add(RecipientId.from(CursorUtil.requireLong(it, RecipientTable.ID)))
        }

        recipientSet
      } ?: emptySet()
    }.subscribeOn(Schedulers.io())
  }
}
