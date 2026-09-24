/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.subscription.donate.transfer.mandate

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.REDDispatchers
import org.signal.core.util.logging.Log
import org.signal.donations.PaymentSourceType
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import com.red.sovereign.components.settings.app.subscription.donate.transfer.details.BankTransferDetailsViewModel
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData

class BankTransferMandateViewModel(
  private val inAppPaymentId: InAppPaymentTable.InAppPaymentId
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(BankTransferDetailsViewModel::class)
  }

  private val disposables = CompositeDisposable()
  private val internalMandate = mutableStateOf("")
  private val internalFailedToLoadMandate = mutableStateOf(false)

  val mandate: State<String> = internalMandate
  val failedToLoadMandate: State<Boolean> = internalFailedToLoadMandate

  init {
    val inAppPayment = InAppPaymentsRepository.requireInAppPayment(inAppPaymentId)

    disposables += inAppPayment
      .flatMap {
        BankTransferMandateRepository.getMandate(it.data.paymentMethodType.toPaymentSourceType() as PaymentSourceType.Stripe)
      }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onSuccess = { internalMandate.value = it },
        onError = {
          Log.w(TAG, "Failed to load mandate.", it)
          internalFailedToLoadMandate.value = true
        }
      )
  }

  suspend fun getPaymentMethodType(): InAppPaymentData.PaymentMethodType {
    return withContext(REDDispatchers.Default) {
      REDDatabase.inAppPayments.getById(inAppPaymentId)!!.data.paymentMethodType
    }
  }

  override fun onCleared() {
    disposables.clear()
  }
}
