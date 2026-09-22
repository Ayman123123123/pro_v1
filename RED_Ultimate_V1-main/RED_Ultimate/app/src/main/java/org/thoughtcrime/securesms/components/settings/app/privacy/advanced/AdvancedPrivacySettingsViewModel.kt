package com.red.sovereign.components.settings.app.privacy.advanced

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.REDDispatchers
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobs.RefreshAttributesJob
import com.red.sovereign.jobs.RefreshOwnProfileJob
import com.red.sovereign.keyvalue.SettingsValues
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.storage.StorageSyncHelper
import com.red.sovereign.util.REDE164Util
import com.red.sovereign.util.TextSecurePreferences
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState

class AdvancedPrivacySettingsViewModel(
  private val sharedPreferences: SharedPreferences,
  private val repository: AdvancedPrivacySettingsRepository
) : ViewModel() {

  private val store = MutableStateFlow(getState())
  private val singleEvents = MutableSharedFlow<Event>()

  val state: StateFlow<AdvancedPrivacySettingsState> = store
  val events: SharedFlow<Event> = singleEvents
  val disposables: CompositeDisposable = CompositeDisposable()

  init {
    disposables.add(
      AppDependencies.webSocketObserver
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { refresh() }
    )
  }

  fun setAlwaysRelayCalls(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.ALWAYS_RELAY_CALLS_PREF, enabled).apply()
    refresh()
  }

  fun setShowStatusIconForSealedSender(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.SHOW_UNIDENTIFIED_DELIVERY_INDICATORS, enabled).apply()
    repository.syncShowSealedSenderIconState()
    refresh()
  }

  fun setAllowSealedSenderFromAnyone(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.UNIVERSAL_UNIDENTIFIED_ACCESS, enabled).apply()
    AppDependencies.jobManager.startChain(RefreshAttributesJob()).then(RefreshOwnProfileJob()).enqueue()
    refresh()
  }

  fun setCensorshipCircumventionEnabled(enabled: Boolean) {
    REDStore.settings.setCensorshipCircumventionEnabled(enabled)
    REDStore.misc.isServiceReachableWithoutCircumvention = false
    AppDependencies.resetNetwork()
    refresh()
  }

  fun setAllowAutomaticVerification(enabled: Boolean) {
    REDStore.settings.automaticVerificationEnabled = enabled
    REDStore.misc.hasKeyTransparencyFailure = false
    REDStore.misc.hasSeenKeyTransparencyFailure = false
    refresh()
    viewModelScope.launch(REDDispatchers.Default) {
      if (!enabled) {
        REDDatabase.recipients.clearAllKeyTransparencyData()
        REDStore.account.distinguishedHead = null
      }
      REDDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  fun refresh() {
    store.update { getState().copy(showProgressSpinner = it.showProgressSpinner) }
  }

  override fun onCleared() {
    disposables.dispose()
  }

  private fun getState(): AdvancedPrivacySettingsState {
    val censorshipCircumventionState = getCensorshipCircumventionState()

    return AdvancedPrivacySettingsState(
      isPushEnabled = REDStore.account.isRegistered,
      alwaysRelayCalls = TextSecurePreferences.isTurnOnly(AppDependencies.application),
      censorshipCircumventionState = censorshipCircumventionState,
      censorshipCircumventionEnabled = getCensorshipCircumventionEnabled(censorshipCircumventionState),
      showSealedSenderStatusIcon = TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(
        AppDependencies.application
      ),
      allowSealedSenderFromAnyone = TextSecurePreferences.isUniversalUnidentifiedAccess(
        AppDependencies.application
      ),
      showProgressSpinner = false,
      allowAutomaticKeyVerification = REDStore.settings.automaticVerificationEnabled,
      isPrimaryDevice = REDStore.account.isPrimaryDevice
    )
  }

  private fun getCensorshipCircumventionState(): CensorshipCircumventionState {
    val countryCode: Int = REDE164Util.getLocalCountryCode()
    val isCountryCodeCensoredByDefault: Boolean = AppDependencies.signalServiceNetworkAccess.isCountryCodeCensoredByDefault(countryCode)
    val enabledState: SettingsValues.CensorshipCircumventionEnabled = REDStore.settings.censorshipCircumventionEnabled
    val hasInternet: Boolean = NetworkConstraint.isMet(AppDependencies.application)
    val websocketConnected: Boolean = AppDependencies.authWebSocket.state.firstOrError().blockingGet() == WebSocketConnectionState.CONNECTED

    return when {
      REDStore.internal.allowChangingCensorshipSetting -> {
        CensorshipCircumventionState.AVAILABLE
      }
      isCountryCodeCensoredByDefault && enabledState == SettingsValues.CensorshipCircumventionEnabled.DISABLED -> {
        CensorshipCircumventionState.AVAILABLE_MANUALLY_DISABLED
      }
      isCountryCodeCensoredByDefault -> {
        CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED
      }
      !hasInternet && enabledState != SettingsValues.CensorshipCircumventionEnabled.ENABLED -> {
        CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET
      }
      websocketConnected && enabledState != SettingsValues.CensorshipCircumventionEnabled.ENABLED -> {
        CensorshipCircumventionState.UNAVAILABLE_CONNECTED
      }
      else -> {
        CensorshipCircumventionState.AVAILABLE
      }
    }
  }

  private fun getCensorshipCircumventionEnabled(state: CensorshipCircumventionState): Boolean {
    return when (state) {
      CensorshipCircumventionState.UNAVAILABLE_CONNECTED,
      CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET,
      CensorshipCircumventionState.AVAILABLE_MANUALLY_DISABLED -> {
        false
      }
      CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED -> {
        true
      }
      else -> {
        REDStore.settings.censorshipCircumventionEnabled == SettingsValues.CensorshipCircumventionEnabled.ENABLED
      }
    }
  }

  enum class Event {
    DISABLE_PUSH_FAILED
  }
}
