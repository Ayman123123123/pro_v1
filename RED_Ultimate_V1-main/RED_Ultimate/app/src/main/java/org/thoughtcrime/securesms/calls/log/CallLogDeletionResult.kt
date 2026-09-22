/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.calls.log

sealed interface CallLogDeletionResult {
  object Success : CallLogDeletionResult

  object Empty : CallLogDeletionResult
  data class FailedToRevoke(val failedRevocations: Int) : CallLogDeletionResult
  data class UnknownFailure(val reason: Throwable) : CallLogDeletionResult
}
