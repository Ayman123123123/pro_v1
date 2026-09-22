/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversationlist

/**
 * Generic interface for the adapters to support updating the
 * timestamp in a given row as opposed to rebinding every item.
 */
interface TimestampPayloadSupport {
  fun notifyTimestampPayloadUpdate()
}
