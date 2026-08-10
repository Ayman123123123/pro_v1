/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.olddevice.transferaccount

sealed interface TransferScreenEvents {
  data object TransferClicked : TransferScreenEvents
  data object ContinueOnOtherDeviceDismiss : TransferScreenEvents
  data object ErrorDialogDismissed : TransferScreenEvents
  data object NavigateBack : TransferScreenEvents
}
