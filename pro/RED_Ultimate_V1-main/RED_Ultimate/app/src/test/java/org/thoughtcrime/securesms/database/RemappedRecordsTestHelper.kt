/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database

/**
 * Bridge to package-private [RemappedRecords] internals for use from test rules.
 */
object RemappedRecordsTestHelper {
  fun resetInstance() {
    RemappedRecords.getInstance().resetCache()
  }
}
