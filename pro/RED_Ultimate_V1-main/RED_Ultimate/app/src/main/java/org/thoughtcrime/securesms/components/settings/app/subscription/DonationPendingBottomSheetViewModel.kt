/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.REDDispatchers
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.REDDatabase

class DonationPendingBottomSheetViewModel(
  inAppPaymentId: InAppPaymentTable.InAppPaymentId
) : ViewModel() {

  private val internalInAppPayment = MutableStateFlow<InAppPaymentTable.InAppPayment?>(null)
  val inAppPayment: StateFlow<InAppPaymentTable.InAppPayment?> = internalInAppPayment

  init {
    viewModelScope.launch {
      val inAppPayment = withContext(REDDispatchers.Default) {
        REDDatabase.inAppPayments.getById(inAppPaymentId)!!
      }

      internalInAppPayment.update { inAppPayment }
    }
  }
}
