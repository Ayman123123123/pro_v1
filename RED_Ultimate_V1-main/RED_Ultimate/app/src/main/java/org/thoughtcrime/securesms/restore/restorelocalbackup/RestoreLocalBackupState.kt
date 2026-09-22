/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.restore.restorelocalbackup

import android.net.Uri
import com.red.sovereign.restore.RestoreRepository
import com.red.sovereign.util.BackupUtil
import com.red.sovereign.util.BackupUtil.BackupInfo

/**
 * State holder for a backup restore.
 */
data class RestoreLocalBackupState(
  val uri: Uri,
  val backupInfo: BackupInfo? = null,
  val backupFileStateError: BackupUtil.BackupFileState? = null,
  val backupPassphrase: String = "",
  val restoreInProgress: Boolean = false,
  val backupVerifyingInProgress: Boolean = false,
  val backupProgressCount: Long = -1,
  val backupEstimatedTotalCount: Long = -1,
  val backupImportResult: RestoreRepository.BackupImportResult? = null,
  val abort: Boolean = false
)
