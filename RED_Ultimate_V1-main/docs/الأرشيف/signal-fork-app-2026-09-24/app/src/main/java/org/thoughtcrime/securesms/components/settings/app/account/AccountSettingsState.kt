package com.red.sovereign.components.settings.app.account

import com.red.sovereign.lock.v2.PinKeyboardType

data class AccountSettingsState(
  val hasPin: Boolean,
  val pinKeyboardType: PinKeyboardType,
  val hasRestoredAep: Boolean,
  val pinRemindersEnabled: Boolean,
  val registrationLockEnabled: Boolean,
  val userUnregistered: Boolean,
  val clientDeprecated: Boolean,
  val canTransferWhileUnregistered: Boolean
) {
  fun isNotDeprecatedOrUnregistered(): Boolean {
    return !(userUnregistered || clientDeprecated)
  }
}
