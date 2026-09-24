/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.banner.banners

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.flow.flowOf
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import com.red.sovereign.R
import com.red.sovereign.banner.Banner
import com.red.sovereign.banner.ui.compose.Action
import com.red.sovereign.banner.ui.compose.DefaultBanner
import com.red.sovereign.banner.ui.compose.Importance
import com.red.sovereign.contacts.sync.CdsTemporaryErrorBottomSheet
import com.red.sovereign.keyvalue.REDStore

class CdsTemporaryErrorBanner(private val fragmentManager: FragmentManager) : Banner<Unit>() {

  override val enabled: Boolean
    get() {
      val timeUntilUnblock = REDStore.misc.cdsBlockedUtil - System.currentTimeMillis()
      return REDStore.misc.isCdsBlocked && timeUntilUnblock < CdsPermanentErrorBanner.PERMANENT_TIME_CUTOFF
    }

  override val dataFlow
    get() = flowOf(Unit)

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) {
    Banner(
      contentPadding = contentPadding,
      onLearnMoreClicked = { CdsTemporaryErrorBottomSheet.show(fragmentManager) }
    )
  }
}

@Composable
private fun Banner(contentPadding: PaddingValues, onLearnMoreClicked: () -> Unit = {}) {
  DefaultBanner(
    title = null,
    body = stringResource(id = R.string.reminder_cds_warning_body),
    importance = Importance.ERROR,
    actions = listOf(
      Action(R.string.reminder_cds_warning_learn_more) {
        onLearnMoreClicked()
      }
    ),
    paddingValues = contentPadding
  )
}

@DayNightPreviews
@Composable
private fun BannerPreview() {
  Previews.Preview {
    Banner(PaddingValues(0.dp))
  }
}
