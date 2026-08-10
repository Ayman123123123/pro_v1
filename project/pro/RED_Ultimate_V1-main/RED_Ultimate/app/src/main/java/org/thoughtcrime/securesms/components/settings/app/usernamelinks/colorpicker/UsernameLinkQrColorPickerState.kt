package com.red.sovereign.components.settings.app.usernamelinks.colorpicker

import kotlinx.collections.immutable.ImmutableList
import com.red.sovereign.components.settings.app.usernamelinks.QrCodeState
import com.red.sovereign.components.settings.app.usernamelinks.UsernameQrCodeColorScheme

data class UsernameLinkQrColorPickerState(
  val username: String,
  val qrCodeData: QrCodeState,
  val colorSchemes: ImmutableList<UsernameQrCodeColorScheme>,
  val selectedColorScheme: UsernameQrCodeColorScheme
)
