/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.labs

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.red.sovereign.keyvalue.REDStore

class LabsSettingsViewModel : ViewModel() {

  private val _state = MutableStateFlow(loadState())
  val state: StateFlow<LabsSettingsState> = _state

  fun onEvent(event: LabsSettingsEvents) {
    when (event) {
      is LabsSettingsEvents.ToggleIndividualChatPlaintextExport -> {
        REDStore.labs.individualChatPlaintextExport = event.enabled
        _state.value = _state.value.copy(individualChatPlaintextExport = event.enabled)
      }
      is LabsSettingsEvents.ToggleStoryArchive -> {
        REDStore.labs.storyArchive = event.enabled
        _state.value = _state.value.copy(storyArchive = event.enabled)
      }
      is LabsSettingsEvents.ToggleIncognito -> {
        REDStore.labs.incognito = event.enabled
        _state.value = _state.value.copy(incognito = event.enabled)
      }
      is LabsSettingsEvents.ToggleBetterSearch -> {
        REDStore.labs.betterSearch = event.enabled
        _state.value = _state.value.copy(betterSearch = event.enabled)
      }
      is LabsSettingsEvents.ToggleStarredMessages -> {
        REDStore.labs.starredMessages = event.enabled
        _state.value = _state.value.copy(starredMessages = event.enabled)
      }
      is LabsSettingsEvents.ToggleStickerReplies -> {
        REDStore.labs.stickerReplies = event.enabled
        _state.value = _state.value.copy(stickerReplies = event.enabled)
      }
      is LabsSettingsEvents.ToggleMuteBreakthroughNotifications -> {
        REDStore.labs.muteBreakthroughNotifications = event.enabled
        _state.value = _state.value.copy(muteBreakthroughNotifications = event.enabled)
      }
      is LabsSettingsEvents.ToggleImprovedMessageDeletion -> {
        REDStore.labs.improvedMessageDeletion = event.enabled
        _state.value = _state.value.copy(improvedMessageDeletion = event.enabled)
      }
    }
  }

  private fun loadState(): LabsSettingsState {
    return LabsSettingsState(
      individualChatPlaintextExport = REDStore.labs.individualChatPlaintextExport,
      storyArchive = REDStore.labs.storyArchive,
      incognito = REDStore.labs.incognito,
      betterSearch = REDStore.labs.betterSearch,
      starredMessages = REDStore.labs.starredMessages,
      stickerReplies = REDStore.labs.stickerReplies,
      muteBreakthroughNotifications = REDStore.labs.muteBreakthroughNotifications,
      improvedMessageDeletion = REDStore.labs.improvedMessageDeletion
    )
  }
}
