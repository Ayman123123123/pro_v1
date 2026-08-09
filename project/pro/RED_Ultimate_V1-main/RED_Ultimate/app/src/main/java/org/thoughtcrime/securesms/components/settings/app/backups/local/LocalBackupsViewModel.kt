/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.backups.local

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.util.StorageUtil
import org.signal.core.util.logging.Log
import com.red.sovereign.R
import com.red.sovereign.backup.BackupPassphrase
import com.red.sovereign.backup.LocalExportProgress
import com.red.sovereign.components.settings.app.backups.remote.BackupKeyCredentialManagerHandler
import com.red.sovereign.components.settings.app.backups.remote.BackupKeySaveState
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.LocalBackupJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.protos.LocalBackupCreationProgress
import com.red.sovereign.util.BackupUtil
import com.red.sovereign.util.DateUtils
import com.red.sovereign.util.TextSecurePreferences
import com.red.sovereign.util.formatHours
import java.time.LocalTime
import java.util.Locale

/**
 * Unified data model backups. Shares the same schema and file breakout as remote backups/.
 */
class LocalBackupsViewModel : ViewModel(), BackupKeyCredentialManagerHandler {

  companion object {
    private val TAG = Log.tag(LocalBackupsViewModel::class)
  }

  private val internalSettingsState = MutableStateFlow(
    LocalBackupsSettingsState(
      backupsEnabled = REDStore.backup.newLocalBackupsEnabled,
      folderDisplayName = getDisplayName(AppDependencies.application, REDStore.backup.newLocalBackupsDirectory)
    )
  )

  private val internalBackupState = MutableStateFlow(LocalBackupsKeyState())

  val settingsState = internalSettingsState
  val backupState = internalBackupState

  init {
    val applicationContext = AppDependencies.application

    viewModelScope.launch {
      REDStore.backup.newLocalBackupsEnabledFlow.collect { enabled ->
        internalSettingsState.update { it.copy(backupsEnabled = enabled) }
      }
    }

    viewModelScope.launch {
      REDStore.backup.newLocalBackupsDirectoryFlow.collect { directory ->
        internalSettingsState.update { it.copy(folderDisplayName = getDisplayName(applicationContext, directory)) }
      }
    }

    viewModelScope.launch {
      REDStore.backup.newLocalBackupsLastBackupTimeFlow.collect { lastBackupTime ->
        internalSettingsState.update { it.copy(lastBackupLabel = calculateLastBackupTimeString(applicationContext, lastBackupTime)) }
      }
    }

    viewModelScope.launch {
      LocalExportProgress.encryptedProgress.collect { progress ->
        internalSettingsState.update { it.copy(progress = progress) }
      }
    }
  }

  fun refreshSettingsState() {
    val context = AppDependencies.application
    val backupTime = LocalTime.of(REDStore.settings.backupHour, REDStore.settings.backupMinute).formatHours(context)

    val userUnregistered = TextSecurePreferences.isUnauthorizedReceived(context) || !REDStore.account.isRegistered
    val clientDeprecated = REDStore.misc.isClientDeprecated
    val legacyLocalBackupsEnabled = REDStore.settings.isBackupEnabled && BackupUtil.canUserAccessBackupDirectory(context)
    val canTurnOn = legacyLocalBackupsEnabled || (!userUnregistered && !clientDeprecated)

    if (REDStore.backup.newLocalBackupsEnabled) {
      if (!BackupUtil.canUserAccessUnifiedBackupDirectory(context)) {
        Log.w(TAG, "Lost access to backup directory, disabling backups")
        REDStore.backup.newLocalBackupsEnabled = false
        AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)
      }
    } else {
      AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)
    }

    internalSettingsState.update {
      it.copy(
        canTurnOn = canTurnOn,
        scheduleTimeLabel = backupTime,
        optimizeStorageEnabled = REDStore.backup.optimizeStorage
      )
    }
  }

  fun onBackupStarted() {
    LocalExportProgress.setEncryptedProgress(LocalBackupCreationProgress(exporting = LocalBackupCreationProgress.Exporting(phase = LocalBackupCreationProgress.ExportPhase.NONE)))
  }

  fun turnOffAndDelete(context: Context) {
    internalSettingsState.update { it.copy(isDeleting = true, deleteCompleted = 0, deleteTotal = 0) }

    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        REDStore.backup.newLocalBackupsEnabled = false
        val path = REDStore.backup.newLocalBackupsDirectory
        REDStore.backup.newLocalBackupsDirectory = null
        AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)
        BackupUtil.deleteUnifiedBackups(context, path) { completed, total ->
          internalSettingsState.update { it.copy(deleteCompleted = completed, deleteTotal = total) }
        }
      }

      internalSettingsState.update { it.copy(isDeleting = false, deleteCompleted = 0, deleteTotal = 0) }
    }
  }

  override fun updateBackupKeySaveState(newState: BackupKeySaveState?) {
    internalBackupState.update { it.copy(keySaveState = newState) }
  }

  suspend fun handleUpgrade(context: Context) {
    if (REDStore.settings.isBackupEnabled) {
      withContext(Dispatchers.IO) {
        AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)
        AppDependencies.jobManager.flush()

        REDStore.backup.newLocalBackupsDirectory = REDStore.settings.signalBackupDirectory?.toString()

        BackupPassphrase.set(context, null)
        REDStore.settings.isBackupEnabled = false
        BackupUtil.deleteAllBackups()
      }
    }

    REDStore.backup.newLocalBackupsEnabled = true
    LocalBackupJob.enqueueArchive(false)
  }
}

private fun getDisplayName(context: Context, directoryUri: String?): String? {
  if (directoryUri == null) {
    return null
  }
  return StorageUtil.getDisplayPath(context, Uri.parse(directoryUri))
}

private fun calculateLastBackupTimeString(context: Context, lastBackupTimestamp: Long): String {
  return if (lastBackupTimestamp > 0) {
    val relativeTime = DateUtils.getDatelessRelativeTimeSpanFormattedDate(
      context,
      Locale.getDefault(),
      lastBackupTimestamp
    )

    if (relativeTime.isRelative) {
      relativeTime.value
    } else {
      val day = DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), lastBackupTimestamp)
      val time = relativeTime.value

      context.getString(R.string.RemoteBackupsSettingsFragment__s_at_s, day, time)
    }
  } else {
    context.getString(R.string.RemoteBackupsSettingsFragment__never)
  }
}
