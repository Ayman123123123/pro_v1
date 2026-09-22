/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.video.interfaces

import android.content.Context
import android.net.Uri
import okio.IOException

interface MediaInputFactory {
  @Throws(IOException::class)
  fun createForUri(context: Context, uri: Uri): MediaInput
}
