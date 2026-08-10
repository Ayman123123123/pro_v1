/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.updates

import kotlin.time.Duration

data class AppUpdatesSettingsState(
  val lastCheckedTime: Duration,
  val autoUpdateEnabled: Boolean
)
