/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.banner.banners

import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
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
import com.red.sovereign.keyvalue.REDStore

class BubbleOptOutBanner(private val inBubble: Boolean, private val actionListener: (Boolean) -> Unit) : Banner<Unit>() {

  override val enabled: Boolean
    get() = inBubble && !REDStore.tooltips.hasSeenBubbleOptOutTooltip() && Build.VERSION.SDK_INT > 29

  override val dataFlow: Flow<Unit>
    get() = flowOf(Unit)

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) = Banner(contentPadding, actionListener)
}

@Composable
private fun Banner(contentPadding: PaddingValues, actionListener: (Boolean) -> Unit = {}) {
  DefaultBanner(
    title = null,
    body = stringResource(id = R.string.BubbleOptOutTooltip__description),
    actions = listOf(
      Action(R.string.BubbleOptOutTooltip__turn_off) {
        actionListener(true)
      },
      Action(R.string.BubbleOptOutTooltip__not_now) {
        actionListener(false)
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
