package com.red.sovereign.components.settings.app.subscription.receipts.detail

import com.red.sovereign.database.model.InAppPaymentReceiptRecord

data class DonationReceiptDetailState(
  val inAppPaymentReceiptRecord: InAppPaymentReceiptRecord? = null
)
