/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2

import org.signal.core.util.ByteSize

class RestoreV2Event(val type: Type, val count: ByteSize, val estimatedTotalCount: ByteSize) {
  enum class Type {
    PROGRESS_DOWNLOAD,
    PROGRESS_RESTORE,
    PROGRESS_FINALIZING
  }

  fun getProgress(): Float {
    if (estimatedTotalCount.inWholeBytes == 0L) {
      return 0f
    }
    return count.inWholeBytes.toFloat() / estimatedTotalCount.inWholeBytes.toFloat()
  }
}
