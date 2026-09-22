package com.red.sovereign.contacts.management

import android.content.Context
import androidx.annotation.CheckResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.RotateProfileKeyJob
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientUtil

class ContactsManagementRepository(context: Context) {
  private val context = context.applicationContext

  @CheckResult
  fun blockContact(recipient: Recipient): Completable {
    return Completable.fromAction {
      if (recipient.isDistributionList) {
        error("Blocking a distribution list makes no sense")
      } else if (recipient.isGroup) {
        RecipientUtil.block(context, recipient)
      } else {
        RecipientUtil.blockNonGroup(context, recipient)
      }
    }.subscribeOn(Schedulers.io())
  }

  @CheckResult
  fun hideContact(recipient: Recipient): Completable {
    return Completable.fromAction {
      if (recipient.isGroup || recipient.isDistributionList || recipient.isSelf) {
        error("Cannot hide groups, self, or distribution lists.")
      }

      val rotateProfileKey = !recipient.hasGroupsInCommon
      REDDatabase.recipients.markHidden(recipient.id, rotateProfileKey, false)
      if (rotateProfileKey) {
        AppDependencies.jobManager.add(RotateProfileKeyJob())
      }
    }.subscribeOn(Schedulers.io())
  }
}
