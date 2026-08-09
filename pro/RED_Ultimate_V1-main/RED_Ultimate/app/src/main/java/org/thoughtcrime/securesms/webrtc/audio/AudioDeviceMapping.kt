package com.red.sovereign.webrtc.audio

import android.media.AudioDeviceInfo
import androidx.annotation.RequiresApi

@RequiresApi(31)
object AudioDeviceMapping {

  private val systemDeviceTypeMap: Map<REDAudioManager.AudioDevice, List<Int>> = mapOf(
    REDAudioManager.AudioDevice.BLUETOOTH to listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID),
    REDAudioManager.AudioDevice.EARPIECE to listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE),
    REDAudioManager.AudioDevice.SPEAKER_PHONE to listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE),
    REDAudioManager.AudioDevice.WIRED_HEADSET to listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET),
    REDAudioManager.AudioDevice.NONE to emptyList()
  )

  @JvmStatic
  fun getEquivalentPlatformTypes(audioDevice: REDAudioManager.AudioDevice): List<Int> {
    return systemDeviceTypeMap[audioDevice]!!
  }

  @JvmStatic
  fun fromPlatformType(type: Int): REDAudioManager.AudioDevice {
    for (kind in REDAudioManager.AudioDevice.entries) {
      if (getEquivalentPlatformTypes(kind).contains(type)) return kind
    }
    return REDAudioManager.AudioDevice.NONE
  }
}
