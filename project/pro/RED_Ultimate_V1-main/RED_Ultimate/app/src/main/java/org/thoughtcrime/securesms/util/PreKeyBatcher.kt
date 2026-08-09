/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.util

import com.red.sovereign.database.IdentityTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.storage.StorageSyncHelper.scheduleSyncForDataChange
import org.whispersystems.signalservice.api.keys.PreKeyRepository

/**
 * Helper to batch recipient updates and storage sync when doing a large prekey fetch.
 *
 * See [PreKeyRepository.BatchHelper] for additional details.
 */
object PreKeyBatcher : PreKeyRepository.BatchHelper {

  override fun batch(block: Runnable) {
    val affected: MutableSet<RecipientId> = HashSet()

    try {
      IdentityTable.SUPPRESS_RECIPIENT_REFRESH.set(affected)
      block.run()
      if (!affected.isEmpty()) {
        REDDatabase.recipients.markNeedsSyncWithoutRefresh(affected)
      }
    } finally {
      IdentityTable.SUPPRESS_RECIPIENT_REFRESH.remove()
    }

    if (!affected.isEmpty()) {
      AppDependencies.recipientCache.refresh(affected)
      scheduleSyncForDataChange()
    }
  }
}
