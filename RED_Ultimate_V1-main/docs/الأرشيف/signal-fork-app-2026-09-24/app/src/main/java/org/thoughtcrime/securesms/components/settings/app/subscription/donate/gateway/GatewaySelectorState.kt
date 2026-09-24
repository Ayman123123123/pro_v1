package com.red.sovereign.components.settings.app.subscription.donate.gateway

import org.signal.core.util.money.FiatMoney
import com.red.sovereign.database.InAppPaymentTable

sealed interface GatewaySelectorState {
  data object Loading : GatewaySelectorState

  data class Ready(
    val gatewayOrderStrategy: GatewayOrderStrategy,
    val inAppPayment: InAppPaymentTable.InAppPayment,
    val isGooglePayAvailable: Boolean = false,
    val isPayPalAvailable: Boolean = false,
    val isCreditCardAvailable: Boolean = false,
    val isSEPADebitAvailable: Boolean = false,
    val isIDEALAvailable: Boolean = false,
    val sepaEuroMaximum: FiatMoney? = null
  ) : GatewaySelectorState
}
