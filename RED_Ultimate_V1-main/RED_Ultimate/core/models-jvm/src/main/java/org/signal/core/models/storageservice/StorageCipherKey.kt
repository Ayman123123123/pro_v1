/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.storageservice

interface StorageCipherKey {
  fun serialize(): ByteArray
}
