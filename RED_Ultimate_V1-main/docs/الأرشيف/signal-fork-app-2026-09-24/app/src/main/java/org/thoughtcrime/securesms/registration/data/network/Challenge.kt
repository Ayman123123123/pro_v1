/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.data.network

import org.signal.core.util.logging.Log

enum class Challenge(val key: String) {
  CAPTCHA("captcha"),
  PUSH("pushChallenge");

  companion object {
    private val TAG = Log.tag(Challenge::class)

    fun parse(strings: List<String>): List<Challenge> {
      return strings.mapNotNull {
        when (it) {
          CAPTCHA.key -> CAPTCHA
          PUSH.key -> PUSH
          else -> {
            Log.i(TAG, "Encountered unknown challenge type: $it")
            null
          }
        }
      }
    }
  }
}
