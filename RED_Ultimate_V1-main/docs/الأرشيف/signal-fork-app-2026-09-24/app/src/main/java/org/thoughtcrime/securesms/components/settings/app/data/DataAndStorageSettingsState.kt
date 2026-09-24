package com.red.sovereign.components.settings.app.data

import org.signal.mediasend.SentMediaQuality
import com.red.sovereign.webrtc.CallDataMode

data class DataAndStorageSettingsState(
  val totalStorageUse: Long,
  val mobileAutoDownloadValues: Set<String>,
  val wifiAutoDownloadValues: Set<String>,
  val roamingAutoDownloadValues: Set<String>,
  val callDataMode: CallDataMode,
  val isProxyEnabled: Boolean,
  val sentMediaQuality: SentMediaQuality,
  val forceWebsocketMode: Boolean,
  val playServicesAvailable: Boolean,
  val showStayConnectedDialog: Boolean
)
