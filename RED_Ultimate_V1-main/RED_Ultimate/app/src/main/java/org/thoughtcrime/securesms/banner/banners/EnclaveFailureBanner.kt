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
import com.red.sovereign.util.PlayStoreUtil

class EnclaveFailureBanner(private val enclaveFailed: Boolean) : Banner<Unit>() {
  override val enabled: Boolean
    get() = enclaveFailed

  override val dataFlow: Flow<Unit>
    get() = flowOf(Unit)

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) {
    val context = LocalContext.current

    Banner(
      contentPadding = contentPadding,
      onUpdateNow = {
        PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context)
      }
    )
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, onUpdateNow: () -> Unit = {}) {
  DefaultBanner(
    title = null,
    body = stringResource(id = R.string.EnclaveFailureReminder_update_signal),
    importance = Importance.ERROR,
    actions = listOf(
      Action(R.string.ExpiredBuildReminder_update_now) {
        onUpdateNow()
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
