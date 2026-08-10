/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.util.UUID

fun UUID.toByteArray(): ByteArray = UuidUtil.toByteArray(this)
