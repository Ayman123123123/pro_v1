/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.mutiselect.forward

sealed interface MultiselectForwardBottomBarEvent {
  data class AddMessageUpdate(val message: String) : MultiselectForwardBottomBarEvent
  data object SendClick : MultiselectForwardBottomBarEvent
}
