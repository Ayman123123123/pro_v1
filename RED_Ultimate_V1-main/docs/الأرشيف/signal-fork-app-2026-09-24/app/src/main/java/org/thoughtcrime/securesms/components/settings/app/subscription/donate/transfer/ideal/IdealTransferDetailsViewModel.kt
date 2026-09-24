/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.subscription.donate.transfer.ideal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.REDDatabase

class IdealTransferDetailsViewModel(inAppPaymentId: InAppPaymentTable.InAppPaymentId) : ViewModel() {

  private val internalState = MutableStateFlow(IdealTransferDetailsState())
  var state: StateFlow<IdealTransferDetailsState> = internalState

  init {
    viewModelScope.launch {
      val inAppPayment = withContext(Dispatchers.Default) {
        REDDatabase.inAppPayments.getById(inAppPaymentId)!!
      }

      internalState.update {
        it.copy(inAppPayment = inAppPayment)
      }
    }
  }

  fun onNameChanged(name: String) {
    internalState.update {
      it.copy(name = name)
    }
  }

  fun onEmailChanged(email: String) {
    internalState.update {
      it.copy(email = email)
    }
  }

  fun onFocusChanged(field: Field, isFocused: Boolean) {
    internalState.update { state ->
      when (field) {
        Field.NAME -> {
          if (isFocused && state.nameFocusState == IdealTransferDetailsState.FocusState.NOT_FOCUSED) {
            state.copy(nameFocusState = IdealTransferDetailsState.FocusState.FOCUSED)
          } else if (!isFocused && state.nameFocusState == IdealTransferDetailsState.FocusState.FOCUSED) {
            state.copy(nameFocusState = IdealTransferDetailsState.FocusState.LOST_FOCUS)
          } else {
            state
          }
        }

        Field.EMAIL -> {
          if (isFocused && state.emailFocusState == IdealTransferDetailsState.FocusState.NOT_FOCUSED) {
            state.copy(emailFocusState = IdealTransferDetailsState.FocusState.FOCUSED)
          } else if (!isFocused && state.emailFocusState == IdealTransferDetailsState.FocusState.FOCUSED) {
            state.copy(emailFocusState = IdealTransferDetailsState.FocusState.LOST_FOCUS)
          } else {
            state
          }
        }
      }
    }
  }

  enum class Field {
    NAME,
    EMAIL
  }
}
