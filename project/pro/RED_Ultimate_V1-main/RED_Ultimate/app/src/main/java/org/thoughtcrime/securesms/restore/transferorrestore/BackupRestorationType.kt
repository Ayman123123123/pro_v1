/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.restore.transferorrestore

/**
 *  What kind of backup restore the user wishes to perform.
 */
enum class BackupRestorationType {
  DEVICE_TRANSFER,
  LOCAL_BACKUP,
  NONE
}
