/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.backups.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.models.AccountEntropyPool
import org.signal.core.util.concurrent.REDDispatchers
import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import com.red.sovereign.backup.v2.BackupRepository
import com.red.sovereign.backup.v2.StagedBackupKeyRotations
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.RestoreOptimizedMediaJob
import com.red.sovereign.keyvalue.REDStore

class BackupKeyDisplayViewModel : ViewModel(), BackupKeyCredentialManagerHandler {

  companion object {
    private val TAG = Log.tag(BackupKeyDisplayViewModel::class.java)
  }

  private val internalUiState = MutableStateFlow(BackupKeyDisplayUiState())
  val uiState: StateFlow<BackupKeyDisplayUiState> = internalUiState.asStateFlow()

  override fun updateBackupKeySaveState(newState: BackupKeySaveState?) {
    internalUiState.update { it.copy(keySaveState = newState) }
  }

  init {
    getKeyRotationLimit()
  }

  fun rotateBackupKey() {
    viewModelScope.launch {
      internalUiState.update { it.copy(rotationState = BackupKeyRotationState.GENERATING_KEY) }

      val stagedKeyRotations = withContext(REDDispatchers.Default) {
        BackupRepository.stageBackupKeyRotations()
      }

      internalUiState.update {
        it.copy(
          accountEntropyPool = stagedKeyRotations.aep,
          stagedKeyRotations = stagedKeyRotations,
          rotationState = BackupKeyRotationState.USER_VERIFICATION
        )
      }
    }
  }

  fun commitBackupKey() {
    viewModelScope.launch {
      internalUiState.update { it.copy(rotationState = BackupKeyRotationState.COMMITTING_KEY) }

      val keyRotations = internalUiState.value.stagedKeyRotations ?: error("No key rotations to commit!")

      withContext(REDDispatchers.IO) {
        BackupRepository.commitAEPKeyRotation(keyRotations)
      }

      internalUiState.update { it.copy(rotationState = BackupKeyRotationState.FINISHED) }
    }
  }

  fun getKeyRotationLimit() {
    viewModelScope.launch(REDDispatchers.IO) {
      val result = BackupRepository.getKeyRotationLimit()
      if (result is NetworkResult.Success) {
        internalUiState.update {
          it.copy(
            canRotateKey = result.result.hasPermitsRemaining ?: true
          )
        }
      } else {
        Log.w(TAG, "Error while getting rotation limit: $result. Default to allowing key rotations.")
      }
    }
  }

  fun turnOffOptimizedStorageAndDownloadMedia() {
    REDStore.backup.optimizeStorage = false
    // SIGNAL_INHERITED: TODO - flag to notify when complete.
    AppDependencies.jobManager.add(RestoreOptimizedMediaJob())
  }
}

data class BackupKeyDisplayUiState(
  val accountEntropyPool: AccountEntropyPool = REDStore.account.accountEntropyPool,
  val keySaveState: BackupKeySaveState? = null,
  val isOptimizedStorageEnabled: Boolean = REDStore.backup.optimizeStorage,
  val rotationState: BackupKeyRotationState = BackupKeyRotationState.NOT_STARTED,
  val stagedKeyRotations: StagedBackupKeyRotations? = null,
  val canRotateKey: Boolean = true
)

enum class BackupKeyRotationState {
  NOT_STARTED,
  GENERATING_KEY,
  USER_VERIFICATION,
  COMMITTING_KEY,
  FINISHED
}
