package com.red.sovereign.components.settings.app.subscription.donate.gateway

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.signal.donations.PaymentSourceType
import com.red.sovereign.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import com.red.sovereign.components.settings.app.subscription.GooglePayRepository
import com.red.sovereign.components.settings.app.subscription.InAppDonations
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.payments.currency.CurrencyUtil
import java.math.BigDecimal

class GatewaySelectorViewModel(
  args: GatewaySelectorBottomSheetArgs,
  repository: GooglePayRepository
) : ViewModel() {

  private val store = MutableStateFlow<GatewaySelectorState>(GatewaySelectorState.Loading)
  private val disposables = CompositeDisposable()

  val state = store.asStateFlow()

  init {
    val inAppPayment = InAppPaymentsRepository.requireInAppPayment(args.inAppPaymentId)
    val isGooglePayAvailable = repository.isGooglePayAvailable().toSingleDefault(true).onErrorReturnItem(false)
    val gatewayConfiguration = inAppPayment.flatMap { GatewaySelectorRepository.getAvailableGatewayConfiguration(currencyCode = it.data.amount!!.currencyCode) }

    disposables += Single.zip(inAppPayment, isGooglePayAvailable, gatewayConfiguration, ::Triple).subscribeBy { (inAppPayment, googlePayAvailable, gatewayConfiguration) ->
      REDStore.inAppPayments.isGooglePayReady = googlePayAvailable
      store.update {
        GatewaySelectorState.Ready(
          gatewayOrderStrategy = GatewayOrderStrategy.getStrategy(),
          inAppPayment = inAppPayment,
          isCreditCardAvailable = InAppDonations.isDonationsPaymentSourceAvailable(PaymentSourceType.Stripe.CreditCard, inAppPayment.type) && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.CARD),
          isGooglePayAvailable = InAppDonations.isDonationsPaymentSourceAvailable(PaymentSourceType.Stripe.GooglePay, inAppPayment.type) && googlePayAvailable && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.GOOGLE_PAY),
          isPayPalAvailable = InAppDonations.isDonationsPaymentSourceAvailable(PaymentSourceType.PayPal, inAppPayment.type) && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.PAYPAL),
          isSEPADebitAvailable = InAppDonations.isDonationsPaymentSourceAvailable(PaymentSourceType.Stripe.SEPADebit, inAppPayment.type) && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.SEPA_DEBIT),
          isIDEALAvailable = InAppDonations.isDonationsPaymentSourceAvailable(PaymentSourceType.Stripe.IDEAL, inAppPayment.type) && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.IDEAL),
          sepaEuroMaximum = gatewayConfiguration.sepaEuroMaximum
        )
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun getSepaMaximum(): BigDecimal {
    val state = store.value as GatewaySelectorState.Ready
    return state.sepaEuroMaximum!!.amount
  }

  fun checkIsSepaPaymentValidAmount(): Boolean {
    val state = store.value as GatewaySelectorState.Ready

    val price = state.inAppPayment.data.amount!!.toFiatMoney()
    return !(
      state.sepaEuroMaximum != null &&
        price.currency == CurrencyUtil.EURO &&
        price.amount > state.sepaEuroMaximum.amount
      )
  }

  suspend fun updateInAppPaymentMethod(inAppPaymentMethodType: InAppPaymentData.PaymentMethodType): InAppPaymentTable.InAppPayment {
    val state = store.value as GatewaySelectorState.Ready

    return GatewaySelectorRepository.setInAppPaymentMethodType(state.inAppPayment, inAppPaymentMethodType)
  }
}
