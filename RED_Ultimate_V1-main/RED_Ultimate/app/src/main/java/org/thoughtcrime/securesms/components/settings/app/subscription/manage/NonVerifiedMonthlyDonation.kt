/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.subscription.manage

import org.signal.core.util.money.FiatMoney

/**
 * Represents a monthly donation via iDEAL that is still pending user verification in
 * their 3rd party app.
 */
data class NonVerifiedMonthlyDonation(
  val timestamp: Long,
  val price: FiatMoney,
  val level: Int,
  val checkedVerification: Boolean
)
