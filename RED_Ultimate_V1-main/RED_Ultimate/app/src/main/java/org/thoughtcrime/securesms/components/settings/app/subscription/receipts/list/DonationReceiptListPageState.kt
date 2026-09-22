package com.red.sovereign.components.settings.app.subscription.receipts.list

import com.red.sovereign.database.model.InAppPaymentReceiptRecord

data class DonationReceiptListPageState(
  val records: List<InAppPaymentReceiptRecord> = emptyList(),
  val isLoaded: Boolean = false
)
