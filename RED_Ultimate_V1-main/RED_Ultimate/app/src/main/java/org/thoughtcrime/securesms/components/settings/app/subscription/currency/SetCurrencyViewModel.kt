package com.red.sovereign.components.settings.app.subscription.currency

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.signal.donations.InAppPaymentType
import com.red.sovereign.BuildConfig
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.livedata.Store
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.util.Currency
import java.util.Locale

class SetCurrencyViewModel(
  private val inAppPaymentType: InAppPaymentType,
  supportedCurrencyCodes: List<String>
) : ViewModel() {

  private val store = Store(
    SetCurrencyState(
      selectedCurrencyCode = if (inAppPaymentType.recurring) {
        REDStore.inAppPayments.getRecurringDonationCurrency().currencyCode
      } else {
        REDStore.inAppPayments.getOneTimeCurrency().currencyCode
      },
      currencies = supportedCurrencyCodes
        .map(Currency::getInstance)
        .sortedWith(CurrencyComparator(BuildConfig.DEFAULT_CURRENCIES.split(",")))
    )
  )

  init {
    check(inAppPaymentType != InAppPaymentType.RECURRING_BACKUP) { "Setting currency is unsupported for backups." }
  }

  val state: LiveData<SetCurrencyState> = store.stateLiveData

  fun setSelectedCurrency(selectedCurrencyCode: String) {
    store.update { it.copy(selectedCurrencyCode = selectedCurrencyCode) }

    if (!inAppPaymentType.recurring) {
      REDStore.inAppPayments.setOneTimeCurrency(Currency.getInstance(selectedCurrencyCode))
    } else {
      val currency = Currency.getInstance(selectedCurrencyCode)
      val subscriber = InAppPaymentsRepository.getRecurringDonationSubscriber(currency)

      if (subscriber != null) {
        InAppPaymentsRepository.setSubscriber(subscriber)
      } else {
        InAppPaymentsRepository.setSubscriber(
          InAppPaymentSubscriberRecord(
            subscriberId = SubscriberId.generate(),
            currency = currency,
            type = inAppPaymentType.requireSubscriberType(),
            requiresCancel = false,
            paymentMethodType = InAppPaymentData.PaymentMethodType.UNKNOWN,
            iapSubscriptionId = null
          )
        )
      }
    }
  }

  @VisibleForTesting
  class CurrencyComparator(private val defaults: List<String>) : Comparator<Currency> {

    companion object {
      private const val USD = "USD"
    }

    override fun compare(o1: Currency, o2: Currency): Int {
      val isO1Default = o1.currencyCode in defaults
      val isO2Default = o2.currencyCode in defaults

      return if (o1.currencyCode == o2.currencyCode) {
        0
      } else if (o1.currencyCode == USD) {
        -1
      } else if (o2.currencyCode == USD) {
        1
      } else if (isO1Default && isO2Default) {
        o1.getDisplayName(Locale.getDefault()).compareTo(o2.getDisplayName(Locale.getDefault()))
      } else if (isO1Default) {
        -1
      } else if (isO2Default) {
        1
      } else {
        o1.getDisplayName(Locale.getDefault()).compareTo(o2.getDisplayName(Locale.getDefault()))
      }
    }
  }

  class Factory(private val inAppPaymentType: InAppPaymentType, private val supportedCurrencyCodes: List<String>) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(SetCurrencyViewModel(inAppPaymentType, supportedCurrencyCodes))!!
    }
  }
}
