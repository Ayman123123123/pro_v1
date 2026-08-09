/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.dependencies

import androidx.media3.exoplayer.ExoPlayer
import org.signal.core.util.contentproviders.BlobProvider
import org.signal.mediasend.MediaSendDependencies
import org.signal.mediasend.MediaSendQrRepository
import org.signal.mediasend.MediaSendRepository
import org.signal.mediasend.preupload.PreUploadRepository
import org.signal.video.exo.ExoPlayerPool
import com.red.sovereign.mediasend.v3.MediaSendV3PreUploadRepository
import com.red.sovereign.mediasend.v3.MediaSendV3QrRepository
import com.red.sovereign.mediasend.v3.MediaSendV3Repository

object MediaSendDependenciesProvider : MediaSendDependencies.Provider {
  override fun provideMediaSendRepository(): MediaSendRepository = MediaSendV3Repository

  override fun providePreUploadRepository(): PreUploadRepository = MediaSendV3PreUploadRepository

  override fun provideQrRepository(): MediaSendQrRepository = MediaSendV3QrRepository

  override fun provideExoPlayerPool(): ExoPlayerPool<ExoPlayer> = AppDependencies.exoPlayerPool

  override fun provideBlobs(): BlobProvider = AppDependencies.blobs
}
