package com.red.sovereign.components.settings.app.subscription.receipts.list

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.InAppPaymentReceiptRecord

class DonationReceiptListPageRepository {
  fun getRecords(type: InAppPaymentReceiptRecord.Type?): Single<List<InAppPaymentReceiptRecord>> {
    return Single.fromCallable {
      REDDatabase.donationReceipts.getReceipts(type)
    }.subscribeOn(Schedulers.io())
  }
}
