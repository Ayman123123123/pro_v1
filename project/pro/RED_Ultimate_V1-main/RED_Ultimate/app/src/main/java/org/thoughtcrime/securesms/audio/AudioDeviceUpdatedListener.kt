/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.audio

/**
 * A listener for when audio devices are added or removed, for example if a wired headset is plugged/unplugged or Bluetooth connected/disconnected.
 */
interface AudioDeviceUpdatedListener {
  fun onAudioDeviceUpdated()
}
