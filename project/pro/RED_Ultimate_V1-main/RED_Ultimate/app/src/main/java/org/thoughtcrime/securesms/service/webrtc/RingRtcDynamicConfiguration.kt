package com.red.sovereign.service.webrtc

import android.os.Build
import org.signal.core.util.asListContains
import org.signal.core.util.isNotNullOrBlank
import org.signal.ringrtc.AudioConfig
import org.signal.ringrtc.VideoConfig
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.webrtc.audio.AudioDeviceConfig
import java.io.File

/**
 * Utility class to determine the audio configuration that RingRTC should use.
 */
object RingRtcDynamicConfiguration {
  private var lastFetchTime: Long = 0

  fun isTelecomAllowedForDevice(): Boolean {
    return RemoteConfig.telecomManufacturerAllowList.lowercase().asListContains(Build.MANUFACTURER.lowercase()) &&
      !RemoteConfig.telecomModelBlocklist.lowercase().asListContains(Build.MODEL.lowercase())
  }

  @JvmStatic
  fun getAudioConfig(): AudioConfig {
    if (RemoteConfig.internalUser && REDStore.internal.callingSetAudioConfig) {
      // Use the internal audio settings.
      var audioConfig = AudioConfig()
      audioConfig.useOboe = REDStore.internal.callingUseOboeAdm
      audioConfig.useSoftwareAec = REDStore.internal.callingUseSoftwareAec
      audioConfig.useSoftwareNs = REDStore.internal.callingUseSoftwareNs
      audioConfig.useInputLowLatency = REDStore.internal.callingUseInputLowLatency
      audioConfig.useInputVoiceComm = REDStore.internal.callingUseInputVoiceComm

      return audioConfig
    }

    // Use the audio settings provided by the remote configuration.
    if (lastFetchTime != REDStore.remoteConfig.lastFetchTime) {
      // The remote config has been updated.
      AudioDeviceConfig.refresh()
      lastFetchTime = REDStore.remoteConfig.lastFetchTime
    }
    return AudioDeviceConfig.getCurrentConfig()
  }

  @JvmStatic
  fun getVideoConfig(): VideoConfig {
    if (RemoteConfig.internalUser && REDStore.internal.callingSetVideoConfig) {
      val videoConfig = VideoConfig()
      videoConfig.enableHardwareVp9Encode = REDStore.internal.callingUseHardwareVp9Encode
      videoConfig.enableHardwareVp9Decode = REDStore.internal.callingUseHardwareVp9Decode
      videoConfig.enableSoftwareVp9Encode = REDStore.internal.callingUseSoftwareVp9Encode
      videoConfig.enableSoftwareVp9Decode = REDStore.internal.callingUseSoftwareVp9Decode
      return videoConfig
    }

    val (manufacturer, model) = getSoCInfo()
    val soc = "${manufacturer.lowercase()}-${model.lowercase()}"
    val videoConfig = VideoConfig()
    videoConfig.enableHardwareVp9Encode = !RemoteConfig.disableHardwareVp9EncodeSocList.contains(soc) && !RemoteConfig.disableHardwareVp9EncodeSocList.contains(model)
    videoConfig.enableHardwareVp9Decode = !RemoteConfig.disableHardwareVp9DecodeSocList.contains(soc) && !RemoteConfig.disableHardwareVp9DecodeSocList.contains(model)
    videoConfig.enableSoftwareVp9Encode = RemoteConfig.enableSoftwareVp9EncodeSoCList.contains(soc) || RemoteConfig.enableSoftwareVp9EncodeSoCList.contains(model)
    videoConfig.enableSoftwareVp9Decode = RemoteConfig.enableSoftwareVp9Decode || RemoteConfig.enableSoftwareVp9DecodeSoCList.contains(soc) || RemoteConfig.enableSoftwareVp9DecodeSoCList.contains(model)

    return videoConfig
  }

  private fun getSoCInfo(): Pair<String, String> {
    // 1. Native API Method (Android 12 / API 31+)
    if (Build.VERSION.SDK_INT >= 31) {
      return Build.SOC_MANUFACTURER to Build.SOC_MODEL
    } else {
      // 2. Fallback Method for Older Devices (Android 11 and below)
      val socModel = getSystemProperty("ro.board.platform").takeIf { it.isNotNullOrBlank() && !it.equals("unknown", ignoreCase = true) }
        ?: parseCpuInfoForHardware().takeIf { it.isNotNullOrBlank() }
        ?: "unknown"
      return "unknown" to socModel
    }
  }

  private fun getSystemProperty(key: String): String {
    return try {
      val systemProperties = Class.forName("android.os.SystemProperties")
      val get = systemProperties.getMethod("get", String::class.java)
      get.invoke(null, key) as String
    } catch (_: Exception) {
      ""
    }
  }

  /**
   * Helper to parse /proc/cpuinfo for the "Hardware" field
   */
  private fun parseCpuInfoForHardware(): String {
    return try {
      File("/proc/cpuinfo").useLines { lines ->
        lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
          ?.substringAfter(':', "")
          ?.trim()
          ?: ""
      }
    } catch (_: Exception) {
      ""
    }
  }
}
