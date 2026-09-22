/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.attachments

/**
 * Thrown by jobs unable to rehydrate enough attachment information to download it.
 */
class InvalidAttachmentException : Exception {
  constructor(s: String?) : super(s)
  constructor(e: Exception?) : super(e)
}
