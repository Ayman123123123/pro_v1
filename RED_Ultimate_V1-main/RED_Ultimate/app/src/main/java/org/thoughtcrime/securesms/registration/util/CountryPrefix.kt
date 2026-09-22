/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.util

data class CountryPrefix(val digits: Int, val regionCode: String) {
  override fun toString(): String {
    return "+$digits"
  }
}
