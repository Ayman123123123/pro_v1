package com.red.sovereign.components.settings.app

import androidx.compose.runtime.Immutable
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.Environment
import com.red.sovereign.util.RemoteConfig

@Immutable
data class AppSettingsState(
  val isPrimaryDevice: Boolean,
  val unreadPaymentsCount: Int,
  val hasExpiredGiftBadge: Boolean,
  val allowUserToGoToDonationManagementScreen: Boolean,
  val userUnregistered: Boolean,
  val clientDeprecated: Boolean,
  val showInternalPreferences: Boolean = RemoteConfig.internalUser,
  val showPayments: Boolean = REDStore.payments.paymentsAvailability.showPaymentsMenu(),
  val showAppUpdates: Boolean = Environment.IS_NIGHTLY,
  val backupFailureState: BackupFailureState = BackupFailureState.NONE
) {
  fun isRegisteredAndUpToDate(): Boolean {
    return !userUnregistered && !clientDeprecated
  }
}
