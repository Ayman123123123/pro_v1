/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.banner.banners

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import com.red.sovereign.R
import com.red.sovereign.banner.Banner
import com.red.sovereign.banner.ui.compose.Action
import com.red.sovereign.banner.ui.compose.DefaultBanner
import com.red.sovereign.banner.ui.compose.Importance
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.PlayStoreUtil

/**
 * Shown when a build is actively deprecated and unable to connect to the service.
 */
class DeprecatedBuildBanner : Banner<Unit>() {

  override val enabled: Boolean
    get() = REDStore.misc.isClientDeprecated

  override val dataFlow: Flow<Unit>
    get() = flowOf(Unit)

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) {
    val context = LocalContext.current
    Banner(
      contentPadding = contentPadding,
      onUpdateClicked = {
        PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context)
      }
    )
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, onUpdateClicked: () -> Unit = {}) {
  DefaultBanner(
    title = null,
    body = stringResource(id = R.string.ExpiredBuildReminder_this_version_of_signal_has_expired),
    importance = Importance.ERROR,
    actions = listOf(
      Action(R.string.ExpiredBuildReminder_update_now) {
        onUpdateClicked()
      }
    ),
    paddingValues = contentPadding
  )
}

@DayNightPreviews
@Composable
private fun BannerPreview() {
  Previews.Preview {
    Banner(contentPadding = PaddingValues(0.dp))
  }
}
