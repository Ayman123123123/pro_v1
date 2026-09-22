/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.ui.warning

sealed interface RecoveryKeyWarningSheetEvent {
  data object DoNotShareClick : RecoveryKeyWarningSheetEvent
  data object ShareKeyClick : RecoveryKeyWarningSheetEvent
  data object GotItClick : RecoveryKeyWarningSheetEvent
  data object LearnMoreClick : RecoveryKeyWarningSheetEvent
}
