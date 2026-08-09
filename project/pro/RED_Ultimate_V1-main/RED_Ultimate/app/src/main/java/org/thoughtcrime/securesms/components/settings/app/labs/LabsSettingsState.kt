/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.labs

import androidx.compose.runtime.Immutable

@Immutable
data class LabsSettingsState(
  val individualChatPlaintextExport: Boolean = false,
  val storyArchive: Boolean = false,
  val incognito: Boolean = false,
  val betterSearch: Boolean = false,
  val starredMessages: Boolean = false,
  val stickerReplies: Boolean = false,
  val muteBreakthroughNotifications: Boolean = false,
  val improvedMessageDeletion: Boolean = false
)
