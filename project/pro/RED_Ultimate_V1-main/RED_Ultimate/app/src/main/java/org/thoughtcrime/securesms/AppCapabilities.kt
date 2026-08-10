package com.red.sovereign

import org.whispersystems.signalservice.api.account.AccountAttributes

object AppCapabilities {
  /**
   * @param storageCapable Whether or not the user can use storage service. This is another way of
   * asking if the user has set a RED PIN or not.
   */
  @JvmStatic
  fun getCapabilities(storageCapable: Boolean): AccountAttributes.Capabilities {
    return AccountAttributes.Capabilities(
      storage = storageCapable,
      versionedExpirationTimer = true,
      attachmentBackfill = true,
      spqr = true,
      usernameChangeSyncMessage = true
    )
  }
}
