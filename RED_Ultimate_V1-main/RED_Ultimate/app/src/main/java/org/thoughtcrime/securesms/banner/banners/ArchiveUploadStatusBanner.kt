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
import kotlinx.coroutines.flow.map
import org.signal.core.util.bytes
import com.red.sovereign.backup.ArchiveUploadProgress
import com.red.sovereign.backup.v2.ui.status.ArchiveUploadStatusBannerView
import com.red.sovereign.backup.v2.ui.status.ArchiveUploadStatusBannerViewEvents
import com.red.sovereign.backup.v2.ui.status.ArchiveUploadStatusBannerViewState
import com.red.sovereign.banner.Banner
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.protos.ArchiveUploadProgressState
import com.red.sovereign.util.NetworkUtil

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveUploadStatusBanner(private val listener: UploadProgressBannerListener) : Banner<ArchiveUploadStatusBannerViewState>() {

  override val enabled: Boolean
    get() = REDStore.backup.uploadBannerVisible

  override val dataFlow: Flow<ArchiveUploadStatusBannerViewState> by lazy {
    ArchiveUploadProgress
      .progress
      .map {
        val hasMobileData = NetworkUtil.isConnectedMobile(AppDependencies.application)
        val hasWifi = NetworkUtil.isConnectedWifi(AppDependencies.application)
        val canUploadOnCellular = REDStore.backup.backupWithCellular

        if (hasWifi || (hasMobileData && canUploadOnCellular)) {
          when (it.state) {
            ArchiveUploadProgressState.State.None -> ArchiveUploadStatusBannerViewState.Finished(it.completedSize.bytes.toUnitString(maxPlaces = 1))
            ArchiveUploadProgressState.State.Export -> ArchiveUploadStatusBannerViewState.CreatingBackupFile
            ArchiveUploadProgressState.State.UploadBackupFile,
            ArchiveUploadProgressState.State.UploadMedia -> {
              if (NetworkConstraint.isMet(AppDependencies.application)) {
                ArchiveUploadStatusBannerViewState.Uploading(
                  completedSize = it.completedSize.bytes.toUnitString(maxPlaces = 1),
                  totalSize = it.totalSize.bytes.toUnitString(maxPlaces = 1),
                  progress = it.completedSize / it.totalSize.toFloat()
                )
              } else {
                ArchiveUploadStatusBannerViewState.PausedNoInternet
              }
            }
            ArchiveUploadProgressState.State.UserCanceled -> ArchiveUploadStatusBannerViewState.CreatingBackupFile
          }
        } else if (hasMobileData) {
          when (it.state) {
            ArchiveUploadProgressState.State.None,
            ArchiveUploadProgressState.State.Export,
            ArchiveUploadProgressState.State.UploadBackupFile,
            ArchiveUploadProgressState.State.UploadMedia -> {
              ArchiveUploadStatusBannerViewState.PausedMissingWifi
            }
            ArchiveUploadProgressState.State.UserCanceled -> ArchiveUploadStatusBannerViewState.CreatingBackupFile
          }
        } else {
          when (it.state) {
            ArchiveUploadProgressState.State.None,
            ArchiveUploadProgressState.State.Export,
            ArchiveUploadProgressState.State.UploadBackupFile,
            ArchiveUploadProgressState.State.UploadMedia -> {
              ArchiveUploadStatusBannerViewState.PausedNoInternet
            }
            ArchiveUploadProgressState.State.UserCanceled -> ArchiveUploadStatusBannerViewState.CreatingBackupFile
          }
        }
      }
  }

  override val stateUpdates: Flow<Unit>
    get() = ArchiveUploadProgress.progress
      .map { enabled }
      .distinctUntilChanged()
      .map { }

  @Composable
  override fun DisplayBanner(model: ArchiveUploadStatusBannerViewState, contentPadding: PaddingValues) {
    ArchiveUploadStatusBannerView(
      state = model,
      emitter = { event ->
        when (event) {
          ArchiveUploadStatusBannerViewEvents.BannerClicked -> {
            listener.onBannerClick()
          }
          ArchiveUploadStatusBannerViewEvents.CancelClicked -> {
            listener.onCancelClicked()
          }
          ArchiveUploadStatusBannerViewEvents.HideClicked -> {
            REDStore.backup.uploadBannerVisible = false
            ArchiveUploadProgress.triggerUpdate()
          }
        }
      }
    )
  }

  private val ArchiveUploadProgressState.completedSize get() = this.mediaUploadedBytes + this.backupFileUploadedBytes
  private val ArchiveUploadProgressState.totalSize get() = this.mediaTotalBytes + this.backupFileTotalBytes

  interface UploadProgressBannerListener {
    fun onBannerClick()
    fun onCancelClicked()
  }
}
