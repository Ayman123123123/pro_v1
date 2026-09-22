/**
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.calls.links.details

import com.red.sovereign.database.CallLinkTable
import com.red.sovereign.service.webrtc.CallLinkPeekInfo

data class CallLinkDetailsState(
  val displayRevocationDialog: Boolean = false,
  val isLoadingAdminApprovalChange: Boolean = false,
  val callLink: CallLinkTable.CallLink? = null,
  val peekInfo: CallLinkPeekInfo? = null,
  val failureSnackbar: FailureSnackbar? = null
) {
  enum class FailureSnackbar {
    COULD_NOT_DELETE_CALL_LINK,
    COULD_NOT_SAVE_CHANGES,
    COULD_NOT_UPDATE_ADMIN_APPROVAL
  }
}
