/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("ExceptionHelper")

package com.red.sovereign.util

import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import java.io.IOException

/**
 * Returns true if this exception is a retryable I/O Exception. Helpful for jobs.
 */
fun Throwable.isRetryableIOException(): Boolean {
  return this is IOException && this !is NonSuccessfulResponseCodeException
}
