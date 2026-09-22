/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.ui.subscription

import androidx.compose.runtime.Immutable
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.billing.BillingResponseCode
import com.red.sovereign.backup.v2.MessageBackupTier
import com.red.sovereign.components.settings.app.backups.remote.BackupKeySaveState
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.keyvalue.REDStore

@Immutable
data class MessageBackupsFlowState(
  val selectedMessageBackupTier: MessageBackupTier? = REDStore.backup.backupTier,
  val currentMessageBackupTier: MessageBackupTier? = null,
  val allBackupTypes: List<MessageBackupsType> = emptyList(),
  val googlePlayApiAvailability: GooglePlayServicesAvailability = GooglePlayServicesAvailability.SUCCESS,
  val googlePlayBillingAvailability: BillingResponseCode = BillingResponseCode.FEATURE_NOT_SUPPORTED,
  val inAppPayment: InAppPaymentTable.InAppPayment? = null,
  val startScreen: MessageBackupsStage,
  val stage: MessageBackupsStage = startScreen,
  val accountEntropyPool: AccountEntropyPool = REDStore.account.accountEntropyPool,
  val failure: Throwable? = null,
  val paymentReadyState: PaymentReadyState = PaymentReadyState.NOT_READY,
  val backupKeySaveState: BackupKeySaveState? = null
) {
  enum class PaymentReadyState {
    NOT_READY,
    READY,
    FAILED
  }

  /**
   * Whether or not the 'next' button on the type selection screen is enabled.
   */
  fun isCheckoutButtonEnabled(): Boolean {
    return selectedMessageBackupTier in allBackupTypes.map { it.tier } &&
      selectedMessageBackupTier != currentMessageBackupTier &&
      paymentReadyState == PaymentReadyState.READY
  }
}
