/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

/**
 * Denotes that a thrown exception can be retried
 */
class InAppPaymentRetryException(
  cause: Throwable? = null
) : Exception(cause)
