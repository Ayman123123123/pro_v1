/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.video.videoconverter

data class MediaConverterState(val videoTrack: VideoTrackConverterState?, val audioTrack: AudioTrackConverterState?, val muxing: Boolean)

data class VideoTrackConverterState(val extractedCount: Long, val extractedDone: Boolean, val decodedCount: Long, val decodedDone: Boolean, val encodedCount: Long, val encodedDone: Boolean, val muxing: Boolean, val trackIndex: Int)

data class AudioTrackConverterState(val extractedCount: Long, val extractedDone: Boolean, val decodedCount: Long, val decodedDone: Boolean, val encodedCount: Long, val encodedDone: Boolean, val pendingBufferIndex: Int, val muxing: Boolean, val trackIndex: Int)
