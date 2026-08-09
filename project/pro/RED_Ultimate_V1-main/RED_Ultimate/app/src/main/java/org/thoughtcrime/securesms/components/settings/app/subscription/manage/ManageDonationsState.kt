package com.red.sovereign.components.settings.app.subscription.manage

import com.red.sovereign.badges.models.Badge
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.model.databaseprotos.PendingOneTimeDonation
import com.red.sovereign.subscription.Subscription

data class ManageDonationsState(
  val hasOneTimeBadge: Boolean = false,
  val hasReceipts: Boolean = false,
  val featuredBadge: Badge? = null,
  val isLoaded: Boolean = false,
  val networkError: Boolean = false,
  val availableSubscriptions: List<Subscription> = emptyList(),
  val activeSubscription: InAppPaymentTable.InAppPayment? = null,
  val subscriptionRedemptionState: RedemptionState = RedemptionState.NONE,
  val pendingOneTimeDonation: PendingOneTimeDonation? = null,
  val nonVerifiedMonthlyDonation: NonVerifiedMonthlyDonation? = null,
  val subscriberRequiresCancel: Boolean = false
) {

  enum class RedemptionState {
    NONE,
    IN_PROGRESS,
    SUBSCRIPTION_REFRESH,
    IS_PENDING_BANK_TRANSFER,
    FAILED
  }
}
