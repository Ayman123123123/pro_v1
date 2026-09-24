/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.dependencies

import org.signal.camera.CameraDependencies
import com.red.sovereign.stories.Stories

object CameraDependenciesProvider : CameraDependencies.Provider {
  override fun isStoriesFeatureEnabled(): Boolean {
    return Stories.isFeatureEnabled()
  }
}
