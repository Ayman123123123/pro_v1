/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.v2.computed

data class FormattedDate(
  val isRelative: Boolean,
  val isNow: Boolean,
  val value: String,
  val contentDescValue: String
)
