/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.dependencies

import android.net.Uri
import org.signal.glide.REDGlideDependencies
import org.signal.glide.common.io.InputStreamFactory
import com.red.sovereign.glide.DecryptableStreamFactory

object REDGlideDependenciesProvider : REDGlideDependencies.Provider {
  override fun getUriInputStreamFactory(uri: Uri, thumbnailTimeUs: Long): InputStreamFactory {
    return DecryptableStreamFactory(uri, thumbnailTimeUs)
  }
}
