/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.backups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.REDDispatchers
import org.signal.core.util.logging.Log
import com.red.sovereign.backup.DeletionState
import com.red.sovereign.backup.v2.BackupRepository
import com.red.sovereign.backup.v2.MessageBackupTier
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.storage.StorageSyncHelper
import com.red.sovereign.util.Environment
import kotlin.time.Duration.Companion.milliseconds

class BackupsSettingsViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(BackupsSettingsViewModel::class)
  }

  private val internalStateFlow: MutableStateFlow<BackupsSettingsState>

  val stateFlow: StateFlow<BackupsSettingsState> by lazy { internalStateFlow }

  init {
    val repo = BackupStateObserver(viewModelScope, useDatabaseFallbackOnNetworkError = true)
    internalStateFlow = MutableStateFlow(BackupsSettingsState(backupState = repo.backupState.value))

    viewModelScope.launch {
      repo.backupState.collect { enabledState ->
        Log.d(TAG, "Found enabled state $enabledState. Updating UI state.")
        internalStateFlow.update {
          it.copy(
            backupState = enabledState,
            lastBackupAt = REDStore.backup.lastBackupTime.milliseconds,
            showBackupTierInternalOverride = Environment.IS_STAGING && REDStore.account.isPrimaryDevice,
            backupTierInternalOverride = REDStore.backup.backupTierInternalOverride
          )
        }
      }
    }

    viewModelScope.launch(Dispatchers.Default) {
      REDStore.backup.lastBackupTimeFlow
        .collect { lastBackupTime ->
          internalStateFlow.update {
            it.copy(lastBackupAt = lastBackupTime.milliseconds)
          }
        }
    }

    if (REDStore.account.isLinkedDevice) {
      viewModelScope.launch(Dispatchers.IO) {
        BackupRepository.refreshBackupFileTimestamp()
      }
    }
  }

  fun onBackupTierInternalOverrideChanged(tier: MessageBackupTier?) {
    REDStore.backup.backupTierInternalOverride = tier
    REDStore.backup.deletionState = DeletionState.NONE
    viewModelScope.launch(REDDispatchers.Default) {
      REDDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    BackupStateObserver.notifyBackupStateChanged(scope = viewModelScope)
  }
}
