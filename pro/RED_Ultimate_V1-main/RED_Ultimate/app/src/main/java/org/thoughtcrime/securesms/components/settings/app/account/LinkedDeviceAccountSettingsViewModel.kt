/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.account

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import com.red.sovereign.components.settings.app.account.LinkedDeviceAccountSettingsState.OneTimeEvent
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.linkdevice.LinkDeviceRepository

/**
 * Drives the account settings screen shown on a linked (non-primary) device. Owns the screen state
 * and, on delete, makes a best-effort attempt to unlink this device from the server before the
 * fragment wipes local data.
 */
class LinkedDeviceAccountSettingsViewModel(
  private val selfDeviceId: () -> Int = { REDStore.account.deviceId },
  private val removeDevice: suspend (Int) -> Boolean = LinkDeviceRepository::removeDevice
) : EventDrivenViewModel<LinkedDeviceAccountSettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(LinkedDeviceAccountSettingsViewModel::class)
  }

  private val _state = MutableStateFlow(LinkedDeviceAccountSettingsState())
  val state: StateFlow<LinkedDeviceAccountSettingsState> = _state.asStateFlow()

  override suspend fun processEvent(event: LinkedDeviceAccountSettingsEvent) {
    applyEvent(_state.value, event) { _state.value = it }
  }

  @VisibleForTesting
  suspend fun applyEvent(state: LinkedDeviceAccountSettingsState, event: LinkedDeviceAccountSettingsEvent, stateEmitter: (LinkedDeviceAccountSettingsState) -> Unit) {
    when (event) {
      LinkedDeviceAccountSettingsEvent.LearnMoreClicked -> stateEmitter(state.copy(oneTimeEvent = OneTimeEvent.OpenLearnMore))
      LinkedDeviceAccountSettingsEvent.NavigateBackClicked -> stateEmitter(state.copy(oneTimeEvent = OneTimeEvent.NavigateBack))
      LinkedDeviceAccountSettingsEvent.DeleteAppDataClicked -> stateEmitter(state.copy(showDeleteConfirmationDialog = true))
      LinkedDeviceAccountSettingsEvent.DeleteDismissed -> stateEmitter(state.copy(showDeleteConfirmationDialog = false))
      LinkedDeviceAccountSettingsEvent.DeleteConfirmed -> {
        val deletingState = state.copy(showDeleteConfirmationDialog = false, deleting = true)
        stateEmitter(deletingState)
        withContext(Dispatchers.IO) { removeDevice(selfDeviceId()) }
        stateEmitter(deletingState.copy(oneTimeEvent = OneTimeEvent.WipeData))
      }
      LinkedDeviceAccountSettingsEvent.DataWipeFailed -> stateEmitter(state.copy(deleting = false, oneTimeEvent = OneTimeEvent.DeleteFailed))
      LinkedDeviceAccountSettingsEvent.ConsumeOneTimeEvent -> stateEmitter(state.copy(oneTimeEvent = null))
    }
  }
}
