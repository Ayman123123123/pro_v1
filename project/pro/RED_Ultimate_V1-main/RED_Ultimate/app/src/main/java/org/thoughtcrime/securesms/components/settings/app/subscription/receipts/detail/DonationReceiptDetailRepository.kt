package com.red.sovereign.components.settings.app.subscription.receipts.detail

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.InAppPaymentReceiptRecord

class DonationReceiptDetailRepository {
  fun getDonationReceiptRecord(id: Long): Single<InAppPaymentReceiptRecord> {
    return Single.fromCallable<InAppPaymentReceiptRecord> {
      REDDatabase.donationReceipts.getReceipt(id)!!
    }.subscribeOn(Schedulers.io())
  }
}
