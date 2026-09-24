/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.labs

sealed interface LabsSettingsEvents {
  data class ToggleIndividualChatPlaintextExport(val enabled: Boolean) : LabsSettingsEvents
  data class ToggleStoryArchive(val enabled: Boolean) : LabsSettingsEvents
  data class ToggleIncognito(val enabled: Boolean) : LabsSettingsEvents
  data class ToggleBetterSearch(val enabled: Boolean) : LabsSettingsEvents
  data class ToggleStarredMessages(val enabled: Boolean) : LabsSettingsEvents
  data class ToggleStickerReplies(val enabled: Boolean) : LabsSettingsEvents
  data class ToggleMuteBreakthroughNotifications(val enabled: Boolean) : LabsSettingsEvents
  data class ToggleImprovedMessageDeletion(val enabled: Boolean) : LabsSettingsEvents
}
