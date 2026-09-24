package com.red.sovereign.components.settings.app.subscription.receipts.list

import com.red.sovereign.badges.models.Badge
import com.red.sovereign.database.model.InAppPaymentReceiptRecord

data class DonationReceiptBadge(
  val type: InAppPaymentReceiptRecord.Type,
  val level: Int,
  val badge: Badge
)
