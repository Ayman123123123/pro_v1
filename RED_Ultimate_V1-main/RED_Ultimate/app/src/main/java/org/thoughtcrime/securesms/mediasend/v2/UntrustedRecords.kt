package com.red.sovereign.mediasend.v2

import androidx.annotation.WorkerThread
import androidx.core.util.Consumer
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.contacts.paged.ContactSearchKey
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.IdentityRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.recipients.Recipient
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

object UntrustedRecords {

  fun checkForBadIdentityRecords(contactSearchKeys: Set<ContactSearchKey.RecipientSearchKey>, changedSince: Long): Completable {
    return Completable.fromAction {
      val untrustedRecords: List<IdentityRecord> = checkForBadIdentityRecordsSync(contactSearchKeys, changedSince)
      if (untrustedRecords.isNotEmpty()) {
        throw UntrustedRecordsException(untrustedRecords, contactSearchKeys)
      }
    }.subscribeOn(Schedulers.io())
  }

  fun checkForBadIdentityRecords(contactSearchKeys: Set<ContactSearchKey.RecipientSearchKey>, changedSince: Long, consumer: Consumer<List<IdentityRecord>>) {
    REDExecutors.BOUNDED.execute {
      consumer.accept(checkForBadIdentityRecordsSync(contactSearchKeys, changedSince))
    }
  }

  @WorkerThread
  private fun checkForBadIdentityRecordsSync(contactSearchKeys: Set<ContactSearchKey.RecipientSearchKey>, changedSince: Long): List<IdentityRecord> {
    val recipients: List<Recipient> = contactSearchKeys
      .map { Recipient.resolved(it.recipientId) }
      .map { recipient ->
        when {
          recipient.isGroup -> Recipient.resolvedList(recipient.participantIds)
          recipient.isDistributionList -> Recipient.resolvedList(REDDatabase.distributionLists.getMembers(recipient.distributionListId.get()))
          else -> listOf(recipient)
        }
      }
      .flatten()

    val calculatedUntrustedWindow = System.currentTimeMillis() - changedSince
    val identityRecords = AppDependencies
      .protocolStore
      .aci()
      .identities()
      .getIdentityRecords(recipients)

    val untrustedRecords = identityRecords.getUntrustedRecords(calculatedUntrustedWindow.coerceIn(5.seconds.inWholeMilliseconds..1.hours.inWholeMilliseconds))
    return (untrustedRecords + identityRecords.unverifiedRecords).distinctBy { it.recipientId }
  }

  class UntrustedRecordsException(val untrustedRecords: List<IdentityRecord>, val destinations: Set<ContactSearchKey.RecipientSearchKey>) : Throwable()
}
