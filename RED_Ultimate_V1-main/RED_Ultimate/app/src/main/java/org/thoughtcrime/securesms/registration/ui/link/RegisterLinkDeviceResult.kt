/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.ui.link

import kotlin.time.Duration

sealed interface RegisterLinkDeviceResult {
  data object Success : RegisterLinkDeviceResult
  data object IncorrectVerification : RegisterLinkDeviceResult
  data object MissingCapability : RegisterLinkDeviceResult
  data object MaxLinkedDevices : RegisterLinkDeviceResult
  data object InvalidRequest : RegisterLinkDeviceResult
  data class RateLimited(val retryAfter: Duration?) : RegisterLinkDeviceResult
  data class NetworkException(val t: Throwable) : RegisterLinkDeviceResult
  data class UnexpectedException(val t: Throwable) : RegisterLinkDeviceResult
}
