/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.recipients

import com.red.sovereign.util.REDE164Util

@JvmInline
value class PhoneNumber(val value: String) {
  val displayText: String
    get() = REDE164Util.prettyPrint(value)
}
