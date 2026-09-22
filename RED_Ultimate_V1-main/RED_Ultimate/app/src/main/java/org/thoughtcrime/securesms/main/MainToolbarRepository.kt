/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.main

import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.notifications.MarkReadReceiver

object MainToolbarRepository {
  /**
   * Mark all unread messages in the local database as read.
   */
  fun markAllMessagesRead() {
    REDExecutors.BOUNDED.execute {
      val messageIds = REDDatabase.threads.setAllThreadsRead()
      AppDependencies.messageNotifier.updateNotification(AppDependencies.application)
      MarkReadReceiver.process(messageIds)
    }
  }
}
