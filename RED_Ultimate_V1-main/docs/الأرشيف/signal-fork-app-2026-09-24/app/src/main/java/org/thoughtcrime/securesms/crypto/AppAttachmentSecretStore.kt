/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.crypto

import android.content.Context
import org.signal.core.util.crypto.AttachmentSecretStore
import com.red.sovereign.util.TextSecurePreferences

object AppAttachmentSecretStore : AttachmentSecretStore {
  override fun getAttachmentUnencryptedSecret(context: Context): String? {
    return TextSecurePreferences.getAttachmentUnencryptedSecret(context)
  }

  override fun getAttachmentEncryptedSecret(context: Context): String? {
    return TextSecurePreferences.getAttachmentEncryptedSecret(context)
  }

  override fun setAttachmentEncryptedSecret(context: Context, secret: String) {
    TextSecurePreferences.setAttachmentEncryptedSecret(context, secret)
  }

  override fun setAttachmentUnencryptedSecret(context: Context, secret: String?) {
    TextSecurePreferences.setAttachmentUnencryptedSecret(context, secret)
  }
}
