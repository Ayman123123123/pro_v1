/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.dependencies

import org.signal.core.util.CoreUtilDependencies
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.RemoteDeprecation

object CoreUtilDependenciesProvider : CoreUtilDependencies.Provider {
  override fun provideIsClientDeprecated(): Boolean {
    return REDStore.misc.isClientDeprecated
  }

  override fun provideTimeUntilRemoteDeprecation(currentTime: Long): Long {
    return RemoteDeprecation.getTimeUntilDeprecation(currentTime)
  }
}
