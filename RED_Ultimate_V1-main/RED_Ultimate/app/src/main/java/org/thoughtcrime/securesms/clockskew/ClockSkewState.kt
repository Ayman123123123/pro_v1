/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.clockskew

/**
 * View state for [ClockSkewScreen].
 */
data class ClockSkewState(
  /** The current device date and time, formatted for display (including the time zone). */
  val deviceDateTime: String = ""
)
