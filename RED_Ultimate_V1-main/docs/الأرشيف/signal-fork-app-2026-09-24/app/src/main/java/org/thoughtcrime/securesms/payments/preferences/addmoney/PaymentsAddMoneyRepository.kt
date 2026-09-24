package com.red.sovereign.payments.preferences.addmoney

import androidx.annotation.MainThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore
import org.signal.core.util.Result as REDResult

internal class PaymentsAddMoneyRepository {
  @MainThread
  fun getWalletAddress(): Single<REDResult<AddressAndUri, Error>> {
    if (!REDStore.payments.mobileCoinPaymentsEnabled()) {
      return Single.just(REDResult.failure(Error.PAYMENTS_NOT_ENABLED))
    }

    return Single.fromCallable<REDResult<AddressAndUri, Error>> {
      val publicAddress = AppDependencies.payments.wallet.mobileCoinPublicAddress
      val paymentAddressBase58 = publicAddress.paymentAddressBase58
      val paymentAddressUri = publicAddress.paymentAddressUri
      REDResult.success(AddressAndUri(paymentAddressBase58, paymentAddressUri))
    }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.io())
  }

  internal enum class Error {
    PAYMENTS_NOT_ENABLED
  }
}
