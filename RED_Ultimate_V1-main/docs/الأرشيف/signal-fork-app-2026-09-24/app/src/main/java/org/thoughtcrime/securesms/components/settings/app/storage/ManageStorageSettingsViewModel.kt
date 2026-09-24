/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.storage

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.backup.v2.MessageBackupTier
import com.red.sovereign.backup.v2.ui.subscription.BackupUpgradeAvailabilityChecker
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.MediaTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.REDDatabase.Companion.media
import com.red.sovereign.database.ThreadTable
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.OptimizeMediaJob
import com.red.sovereign.jobs.RestoreOptimizedMediaJob
import com.red.sovereign.keyvalue.KeepMessagesDuration
import com.red.sovereign.keyvalue.REDStore

class ManageStorageSettingsViewModel : ViewModel() {

  private val store = MutableStateFlow(
    ManageStorageState(
      keepMessagesDuration = REDStore.settings.keepMessagesDuration,
      lengthLimit = if (REDStore.settings.isTrimByLengthEnabled) REDStore.settings.threadTrimLength else ManageStorageState.NO_LIMIT,
      syncTrimDeletes = REDStore.settings.shouldSyncThreadTrimDeletes(),
      localBackupsEnabled = REDStore.backup.newLocalBackupsEnabled,
      isPrimary = REDStore.account.isPrimaryDevice
    )
  )
  val state = store.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.Default) {
      InAppPaymentsRepository.observeLatestBackupPayment()
        .collectLatest { payment ->
          store.update { it.copy(isPaidTierPending = payment.state == InAppPaymentTable.State.PENDING) }
        }
    }

    viewModelScope.launch {
      store.update {
        it.copy(onDeviceStorageOptimizationState = getOnDeviceStorageOptimizationState())
      }
    }
  }

  fun refresh() {
    viewModelScope.launch {
      val breakdown: MediaTable.StorageBreakdown = media.getStorageBreakdown()
      store.update { it.copy(breakdown = breakdown) }
    }
  }

  fun deleteChatHistory() {
    REDExecutors.BOUNDED_IO.execute {
      REDDatabase.threads.deleteAllConversations()
      AppDependencies.messageNotifier.updateNotification(AppDependencies.application)
    }
  }

  fun setKeepMessagesDuration(newDuration: KeepMessagesDuration) {
    REDStore.settings.setKeepMessagesForDuration(newDuration)
    AppDependencies.trimThreadsByDateManager.scheduleIfNecessary()

    store.update { it.copy(keepMessagesDuration = newDuration) }
  }

  fun showConfirmKeepDurationChange(newDuration: KeepMessagesDuration): Boolean {
    return newDuration.ordinal > state.value.keepMessagesDuration.ordinal
  }

  fun setChatLengthLimit(newLimit: Int) {
    val restrictingChange = isRestrictingLengthLimitChange(newLimit)

    REDStore.settings.setThreadTrimByLengthEnabled(newLimit != ManageStorageState.NO_LIMIT)
    REDStore.settings.threadTrimLength = newLimit
    store.update { it.copy(lengthLimit = newLimit) }

    if (REDStore.settings.isTrimByLengthEnabled && restrictingChange) {
      REDExecutors.BOUNDED.execute {
        val keepMessagesDuration = REDStore.settings.keepMessagesDuration

        val trimBeforeDate = if (keepMessagesDuration != KeepMessagesDuration.FOREVER) {
          System.currentTimeMillis() - keepMessagesDuration.duration
        } else {
          ThreadTable.NO_TRIM_BEFORE_DATE_SET
        }

        REDDatabase.threads.trimAllThreads(newLimit, trimBeforeDate)
      }
    }
  }

  fun showConfirmSetChatLengthLimit(newLimit: Int): Boolean {
    return isRestrictingLengthLimitChange(newLimit)
  }

  fun setSyncTrimDeletes(syncTrimDeletes: Boolean) {
    REDStore.settings.setSyncThreadTrimDeletes(syncTrimDeletes)
    store.update { it.copy(syncTrimDeletes = syncTrimDeletes) }
  }

  fun setOptimizeStorage(enabled: Boolean) {
    viewModelScope.launch {
      val storageState = getOnDeviceStorageOptimizationState()
      if (storageState >= OnDeviceStorageOptimizationState.DISABLED) {
        REDStore.backup.optimizeStorage = enabled
        store.update {
          it.copy(
            onDeviceStorageOptimizationState = if (enabled) OnDeviceStorageOptimizationState.ENABLED else OnDeviceStorageOptimizationState.DISABLED,
            storageOptimizationStateChanged = true
          )
        }
      }
    }
  }

  private fun isRestrictingLengthLimitChange(newLimit: Int): Boolean {
    return state.value.lengthLimit == ManageStorageState.NO_LIMIT || (newLimit != ManageStorageState.NO_LIMIT && newLimit < state.value.lengthLimit)
  }

  private suspend fun getOnDeviceStorageOptimizationState(): OnDeviceStorageOptimizationState {
    return when {
      !REDStore.account.isPrimaryDevice -> OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE
      !REDStore.backup.areBackupsEnabled || !BackupUpgradeAvailabilityChecker.isUpgradeAvailable(AppDependencies.application) -> OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE
      REDStore.backup.backupTier != MessageBackupTier.PAID -> OnDeviceStorageOptimizationState.REQUIRES_PAID_TIER
      REDStore.backup.optimizeStorage -> OnDeviceStorageOptimizationState.ENABLED
      else -> OnDeviceStorageOptimizationState.DISABLED
    }
  }

  override fun onCleared() {
    if (state.value.storageOptimizationStateChanged) {
      when (state.value.onDeviceStorageOptimizationState) {
        OnDeviceStorageOptimizationState.DISABLED -> RestoreOptimizedMediaJob.enqueue()
        OnDeviceStorageOptimizationState.ENABLED -> OptimizeMediaJob.enqueue()
        else -> Unit
      }
    }
  }

  enum class OnDeviceStorageOptimizationState {
    /**
     * The entire feature is not available and the option should not be displayed to the user.
     */
    FEATURE_NOT_AVAILABLE,

    /**
     * The feature is available, but the user is not on the paid backups plan.
     */
    REQUIRES_PAID_TIER,

    /**
     * The user is on the paid backups plan but optimized storage is disabled.
     */
    DISABLED,

    /**
     * The user is on the paid backups plan and optimized storage is enabled.
     */
    ENABLED
  }

  @Immutable
  data class ManageStorageState(
    val keepMessagesDuration: KeepMessagesDuration,
    val lengthLimit: Int,
    val syncTrimDeletes: Boolean,
    val breakdown: MediaTable.StorageBreakdown? = null,
    val onDeviceStorageOptimizationState: OnDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE,
    val storageOptimizationStateChanged: Boolean = false,
    val isPaidTierPending: Boolean = false,
    val localBackupsEnabled: Boolean = false,
    val isPrimary: Boolean = true
  ) {
    companion object {
      const val NO_LIMIT = 0
    }
  }
}
