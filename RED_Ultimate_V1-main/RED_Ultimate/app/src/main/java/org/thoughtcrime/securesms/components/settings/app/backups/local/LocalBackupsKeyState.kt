/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.backups.local

import org.signal.core.models.AccountEntropyPool
import com.red.sovereign.components.settings.app.backups.remote.BackupKeySaveState
import com.red.sovereign.keyvalue.REDStore

data class LocalBackupsKeyState(
  val accountEntropyPool: AccountEntropyPool = REDStore.account.accountEntropyPool,
  val keySaveState: BackupKeySaveState? = null
)
