/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.updates

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.red.sovereign.keyvalue.REDStore
import kotlin.time.Duration.Companion.milliseconds

class AppUpdatesSettingsViewModel : ViewModel() {
  private val internalState = MutableStateFlow(getState())

  val state: StateFlow<AppUpdatesSettingsState> = internalState

  fun refresh() {
    internalState.update { getState() }
  }

  private fun getState(): AppUpdatesSettingsState {
    return AppUpdatesSettingsState(
      lastCheckedTime = REDStore.apkUpdate.lastSuccessfulCheck.milliseconds,
      autoUpdateEnabled = REDStore.apkUpdate.autoUpdate
    )
  }
}
