/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.data

data class RegisterAsLinkedDeviceResponse(
  val deviceId: Int,
  val accountRegistrationResult: AccountRegistrationResult
)
