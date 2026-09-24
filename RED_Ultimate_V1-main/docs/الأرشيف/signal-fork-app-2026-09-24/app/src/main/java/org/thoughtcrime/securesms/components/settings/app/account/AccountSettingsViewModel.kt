package com.red.sovereign.components.settings.app.account

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.TextSecurePreferences

class AccountSettingsViewModel : ViewModel() {
  private val store: MutableStateFlow<AccountSettingsState> = MutableStateFlow(getCurrentState())

  val state: StateFlow<AccountSettingsState> = store

  fun refreshState() {
    store.update { getCurrentState() }
  }

  fun togglePinKeyboardType() {
    store.update {
      it.copy(pinKeyboardType = it.pinKeyboardType.other)
    }
  }

  private fun getCurrentState(): AccountSettingsState {
    return AccountSettingsState(
      hasPin = REDStore.svr.hasPin() && !REDStore.svr.hasOptedOut(),
      pinKeyboardType = REDStore.pin.keyboardType,
      hasRestoredAep = REDStore.account.restoredAccountEntropyPool,
      pinRemindersEnabled = REDStore.pin.arePinRemindersEnabled() && REDStore.svr.hasPin(),
      registrationLockEnabled = REDStore.svr.isRegistrationLockEnabled,
      userUnregistered = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application),
      clientDeprecated = REDStore.misc.isClientDeprecated,
      canTransferWhileUnregistered = true
    )
  }
}
