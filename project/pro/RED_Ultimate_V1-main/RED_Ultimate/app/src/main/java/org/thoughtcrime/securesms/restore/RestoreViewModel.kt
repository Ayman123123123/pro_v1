/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.restore

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.red.sovereign.backup.v2.MessageBackupTier
import com.red.sovereign.database.model.databaseprotos.RestoreDecisionState
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.Skipped
import com.red.sovereign.keyvalue.includeDeviceToDeviceTransfer
import com.red.sovereign.keyvalue.skippedRestoreChoice
import com.red.sovereign.registration.data.QuickRegistrationRepository
import com.red.sovereign.registration.ui.restore.RestoreMethod
import com.red.sovereign.registration.ui.restore.StorageServiceRestore
import com.red.sovereign.util.Environment
import org.whispersystems.signalservice.api.provisioning.RestoreMethod as ApiRestoreMethod

/**
 * Shared view model for the restore flow.
 */
class RestoreViewModel : ViewModel() {
  private val store = MutableStateFlow(RestoreState())
  val uiState = store.asLiveData()

  var showStorageAccountRestoreProgress by mutableStateOf(false)
    private set

  fun setNextIntent(nextIntent: Intent) {
    store.update {
      it.copy(nextIntent = nextIntent)
    }
  }

  fun setBackupFileUri(backupFileUri: Uri) {
    store.update {
      it.copy(backupFile = backupFileUri)
    }
  }

  fun getBackupFileUri(): Uri? = store.value.backupFile

  fun getNextIntent(): Intent? = store.value.nextIntent

  fun hasNoRestoreMethods(): Boolean {
    return getAvailableRestoreMethods().isEmpty()
  }

  fun getAvailableRestoreMethods(): List<RestoreMethod> {
    if (REDStore.registration.isOtherDeviceAndroid || REDStore.registration.restoreDecisionState.skippedRestoreChoice) {
      val methods = if (Environment.Backups.isNewFormatSupportedForLocalBackup()) {
        mutableListOf(RestoreMethod.FROM_LOCAL_BACKUP_V2)
      } else {
        mutableListOf(RestoreMethod.FROM_LOCAL_BACKUP_V1)
      }

      if (REDStore.registration.isOtherDeviceAndroid && REDStore.registration.restoreDecisionState.includeDeviceToDeviceTransfer) {
        methods.add(0, RestoreMethod.FROM_OLD_DEVICE)
      }

      when (REDStore.backup.backupTier) {
        MessageBackupTier.FREE -> methods.add(1, RestoreMethod.FROM_SIGNAL_BACKUPS)
        MessageBackupTier.PAID -> methods.add(0, RestoreMethod.FROM_SIGNAL_BACKUPS)
        null -> if (!REDStore.backup.restoringViaQr) {
          methods.add(1, RestoreMethod.FROM_SIGNAL_BACKUPS)
        }
      }

      return methods
    }

    if (REDStore.backup.restoringViaQr && REDStore.backup.backupTier != null) {
      return listOf(RestoreMethod.FROM_SIGNAL_BACKUPS)
    }

    return emptyList()
  }

  fun hasRestoredAccountEntropyPool(): Boolean {
    return REDStore.account.restoredAccountEntropyPool
  }

  fun hasRestoredBackupDataFromQr(): Boolean {
    return REDStore.backup.restoringViaQr && REDStore.backup.backupTier != null
  }

  fun skipRestore() {
    REDStore.registration.restoreDecisionState = RestoreDecisionState.Skipped

    viewModelScope.launch {
      QuickRegistrationRepository.setRestoreMethodForOldDevice(ApiRestoreMethod.DECLINE)
    }
  }

  suspend fun performStorageServiceAccountRestoreIfNeeded() {
    if (hasRestoredAccountEntropyPool() || REDStore.svr.masterKeyForInitialDataRestore != null) {
      showStorageAccountRestoreProgress = true
      StorageServiceRestore.restore()
    }
  }
}
