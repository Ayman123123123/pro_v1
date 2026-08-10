/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.banner.banners

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import com.red.sovereign.backup.v2.ArchiveRestoreProgress
import com.red.sovereign.backup.v2.ArchiveRestoreProgressState
import com.red.sovereign.backup.v2.ArchiveRestoreProgressState.RestoreStatus
import com.red.sovereign.backup.v2.ui.status.ArchiveRestoreStatusBanner
import com.red.sovereign.banner.Banner

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveRestoreStatusBanner(private val listener: RestoreProgressBannerListener) : Banner<ArchiveRestoreProgressState>() {

  override val enabled: Boolean
    get() = ArchiveRestoreProgress.state.let { it.restoreState.isMediaRestoreOperation || it.restoreStatus == RestoreStatus.FINISHED }

  override val dataFlow: Flow<ArchiveRestoreProgressState> by lazy {
    ArchiveRestoreProgress
      .stateFlow
      .onStart { ArchiveRestoreProgress.checkForStalledRestore() }
      .filter {
        it.restoreStatus != RestoreStatus.NONE && (it.restoreState.isMediaRestoreOperation || it.restoreStatus == RestoreStatus.FINISHED)
      }
  }

  override val stateUpdates: Flow<Unit>
    get() = ArchiveRestoreProgress.stateFlow
      .map { enabled }
      .distinctUntilChanged()
      .map { }

  @Composable
  override fun DisplayBanner(model: ArchiveRestoreProgressState, contentPadding: PaddingValues) {
    ArchiveRestoreStatusBanner(
      data = model,
      onBannerClick = listener::onBannerClick,
      onActionClick = listener::onActionClick,
      onDismissClick = {
        ArchiveRestoreProgress.clearFinishedStatus()
      }
    )
  }

  interface RestoreProgressBannerListener {
    fun onBannerClick()
    fun onActionClick(data: ArchiveRestoreProgressState)
  }
}
