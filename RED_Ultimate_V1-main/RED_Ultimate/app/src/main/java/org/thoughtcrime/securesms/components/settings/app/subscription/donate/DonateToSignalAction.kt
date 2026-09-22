package com.red.sovereign.components.settings.app.subscription.donate

import org.signal.donations.InAppPaymentType
import com.red.sovereign.database.InAppPaymentTable

sealed class DonateToREDAction {
  data class DisplayCurrencySelectionDialog(val inAppPaymentType: InAppPaymentType, val supportedCurrencies: List<String>) : DonateToREDAction()
  data class DisplayGatewaySelectorDialog(val inAppPayment: InAppPaymentTable.InAppPayment) : DonateToREDAction()
  data object CancelSubscription : DonateToREDAction()
  data class UpdateSubscription(val inAppPayment: InAppPaymentTable.InAppPayment, val isLongRunning: Boolean) : DonateToREDAction()
}
