package com.red.sovereign.mms

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.signal.mediasend.SentMediaQuality
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.video.TranscodingConfig

/**
 * Gets corresponding configs depending on locale and sent media quality
 */
object TranscodingConfigProvider {
  @JvmStatic
  fun getAllConfigs(): TranscodingConfig.TranscodeConfig {
    val countryCode = PhoneNumberUtil.getInstance().parse(REDStore.account.e164, "").countryCode
    return TranscodingConfig.getTranscodeConfig(RemoteConfig.transcodeConfig, countryCode)
  }

  @JvmStatic
  fun getConfigsForMediaQuality(quality: SentMediaQuality): List<TranscodingConfig.QualityTier> {
    val config = getAllConfigs()
    return when (quality) {
      SentMediaQuality.STANDARD -> config.standard
      SentMediaQuality.HIGH -> config.high
    }
  }
}
