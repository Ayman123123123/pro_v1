/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.recipients.ui.findby

import com.red.sovereign.recipients.RecipientId

sealed interface FindByResult {
  data class Success(val recipientId: RecipientId) : FindByResult
  object InvalidEntry : FindByResult
  data class NotFound(val recipientId: RecipientId = RecipientId.UNKNOWN) : FindByResult
  object NetworkError : FindByResult
}
