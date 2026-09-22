package com.red.sovereign.components.settings.app.subscription.donate.gateway

import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.money.FiatMoney
import com.red.sovereign.components.settings.app.subscription.getAvailablePaymentMethods
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.payments.currency.CurrencyUtil
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.util.Locale

object GatewaySelectorRepository {
  fun getAvailableGatewayConfiguration(currencyCode: String): Single<GatewayConfiguration> {
    return Single.fromCallable {
      AppDependencies.donationsService.getDonationsConfiguration(Locale.getDefault())
    }.flatMap { it.flattenResult() }
      .map { configuration ->
        val available = configuration.getAvailablePaymentMethods(currencyCode).map {
          when (it) {
            SubscriptionsConfiguration.PAYPAL -> listOf(InAppPaymentData.PaymentMethodType.PAYPAL)
            SubscriptionsConfiguration.CARD -> listOf(InAppPaymentData.PaymentMethodType.CARD, InAppPaymentData.PaymentMethodType.GOOGLE_PAY)
            SubscriptionsConfiguration.SEPA_DEBIT -> listOf(InAppPaymentData.PaymentMethodType.SEPA_DEBIT)
            SubscriptionsConfiguration.IDEAL -> listOf(InAppPaymentData.PaymentMethodType.IDEAL)
            else -> listOf()
          }
        }.flatten().toSet()

        GatewayConfiguration(
          availableGateways = available,
          sepaEuroMaximum = if (configuration.sepaMaximumEuros != null) FiatMoney(configuration.sepaMaximumEuros, CurrencyUtil.EURO) else null
        )
      }
  }

  suspend fun setInAppPaymentMethodType(inAppPayment: InAppPaymentTable.InAppPayment, paymentMethodType: InAppPaymentData.PaymentMethodType): InAppPaymentTable.InAppPayment {
    return withContext(Dispatchers.Default) {
      REDDatabase.inAppPayments.update(
        inAppPayment.copy(
          data = inAppPayment.data.copy(
            paymentMethodType = paymentMethodType
          )
        )
      )

      REDDatabase.inAppPayments.getById(inAppPayment.id) ?: throw Exception("Not found.")
    }
  }

  data class GatewayConfiguration(
    val availableGateways: Set<InAppPaymentData.PaymentMethodType>,
    val sepaEuroMaximum: FiatMoney?
  )
}
