/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.ui.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import com.red.sovereign.backup.v2.BackupRepository
import com.red.sovereign.backup.v2.MessageBackupTier
import com.red.sovereign.backup.v2.RemoteRestoreResult
import com.red.sovereign.backup.v2.RestoreTimestampResult
import com.red.sovereign.backup.v2.RestoreV2Event
import com.red.sovereign.database.model.databaseprotos.RestoreDecisionState
import com.red.sovereign.keyvalue.Completed
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.Skipped
import com.red.sovereign.registration.data.QuickRegistrationRepository
import org.whispersystems.signalservice.api.provisioning.RestoreMethod
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class RemoteRestoreViewModel(isOnlyRestoreOption: Boolean) : ViewModel() {

  companion object {
    private val TAG = Log.tag(RemoteRestoreViewModel::class)
  }

  private val store: MutableStateFlow<ScreenState> = MutableStateFlow(
    ScreenState(
      isRemoteRestoreOnlyOption = isOnlyRestoreOption,
      backupTier = REDStore.backup.backupTier,
      backupTime = REDStore.backup.lastBackupTime,
      backupSize = REDStore.registration.restoreBackupMediaSize.bytes
    )
  )

  val state: StateFlow<ScreenState> = store.asStateFlow()

  init {
    reload()
  }

  fun reload() {
    viewModelScope.launch(Dispatchers.IO) {
      store.update { it.copy(loadState = ScreenState.LoadState.LOADING, loadAttempts = it.loadAttempts + 1) }
      Log.i(TAG, "Fetching remote backup information")
      var result: RestoreTimestampResult = BackupRepository.restoreBackupFileTimestamp()

      if (result is RestoreTimestampResult.VerificationFailure && REDStore.account.restoredAccountEntropyPool) {
        Log.w(TAG, "Resetting backup id reservation due to zk verification failure with restored AEP")
        result = when (val triggerResult = BackupRepository.triggerBackupIdReservationForRestore()) {
          is NetworkResult.Success -> {
            Log.i(TAG, "Reset successful, trying to restore timestamp")
            BackupRepository.restoreBackupFileTimestamp()
          }
          else -> {
            Log.w(TAG, "Reset unsuccessful, failing", triggerResult.getCause())
            result
          }
        }
      }

      store.update {
        when (result) {
          is RestoreTimestampResult.Success -> {
            it.copy(
              loadState = ScreenState.LoadState.LOADED,
              backupTier = REDStore.backup.backupTier,
              backupTime = REDStore.backup.lastBackupTime,
              backupSize = REDStore.registration.restoreBackupMediaSize.bytes
            )
          }

          is RestoreTimestampResult.BackupsNotEnabled,
          is RestoreTimestampResult.NotFound -> {
            it.copy(loadState = ScreenState.LoadState.NOT_FOUND)
          }

          else -> {
            if (it.loadState == ScreenState.LoadState.LOADING) {
              it.copy(loadState = ScreenState.LoadState.FAILURE)
            } else {
              it
            }
          }
        }
      }
    }

    viewModelScope.launch(Dispatchers.IO) {
      val config = BackupRepository.getBackupLevelConfiguration()
      if (config is NetworkResult.Success) {
        store.update {
          it.copy(backupMediaTTL = config.result.mediaTtlDays.days)
        }
      }
    }
  }

  fun restore() {
    viewModelScope.launch {
      store.update { it.copy(importState = ImportState.InProgress) }

      withContext(Dispatchers.IO) {
        QuickRegistrationRepository.setRestoreMethodForOldDevice(RestoreMethod.REMOTE_BACKUP)

        when (val result = BackupRepository.restoreRemoteBackup()) {
          is RemoteRestoreResult.Success -> {
            Log.i(TAG, "Restore successful", true)
            REDStore.registration.restoreDecisionState = RestoreDecisionState.Completed

            REDStore.backup.backupSecretRestoreRequired = false
            StorageServiceRestore.restore()

            store.update { it.copy(importState = ImportState.Restored) }
          }

          RemoteRestoreResult.NetworkError -> {
            Log.w(TAG, "Restore failed to download", true)
            store.update { it.copy(importState = ImportState.NetworkFailure) }
          }

          RemoteRestoreResult.Canceled,
          RemoteRestoreResult.Failure -> {
            Log.w(TAG, "Restore failed with $result", true)
            store.update { it.copy(importState = ImportState.Failed) }
          }

          RemoteRestoreResult.PermanentSvrBFailure -> {
            Log.w(TAG, "Hit a permanent SVRB error.", true)
            store.update { it.copy(importState = ImportState.FailureWithLogPrompt) }
          }
        }
      }
    }
  }

  fun updateRestoreProgress(restoreEvent: RestoreV2Event) {
    store.update { it.copy(restoreProgress = restoreEvent) }
  }

  fun clearError() {
    store.update { it.copy(importState = ImportState.None, restoreProgress = null) }
  }

  fun skipRestore() {
    REDStore.registration.restoreDecisionState = RestoreDecisionState.Skipped

    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        QuickRegistrationRepository.setRestoreMethodForOldDevice(RestoreMethod.DECLINE)
      }
    }
  }

  suspend fun performStorageServiceAccountRestoreIfNeeded() {
    if (REDStore.account.restoredAccountEntropyPool || REDStore.svr.masterKeyForInitialDataRestore != null) {
      store.update { it.copy(loadState = ScreenState.LoadState.STORAGE_SERVICE_RESTORE) }
      StorageServiceRestore.restore()
    }
  }

  data class ScreenState(
    val isRemoteRestoreOnlyOption: Boolean = false,
    val backupMediaTTL: Duration = 30.days,
    val backupTier: MessageBackupTier? = null,
    val backupTime: Long = -1,
    val backupSize: ByteSize = 0.bytes,
    val importState: ImportState = ImportState.None,
    val restoreProgress: RestoreV2Event? = null,
    val loadState: LoadState = if (backupTier != null) LoadState.LOADED else LoadState.LOADING,
    val loadAttempts: Int = 0
  ) {

    fun isLoaded(): Boolean {
      return loadState == LoadState.LOADED
    }

    enum class LoadState {
      LOADING, LOADED, NOT_FOUND, FAILURE, STORAGE_SERVICE_RESTORE
    }
  }

  sealed interface ImportState {
    data object None : ImportState
    data object InProgress : ImportState
    data object Restored : ImportState
    data object NetworkFailure : ImportState
    data object Failed : ImportState
    data object FailureWithLogPrompt : ImportState
  }
}
