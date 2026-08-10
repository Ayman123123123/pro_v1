/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.changenumber

import androidx.fragment.app.activityViewModels
import com.red.sovereign.registration.ui.captcha.CaptchaFragment

/**
 * Screen visible to the user when they are to solve a captcha. @see [CaptchaFragment]
 */
class ChangeNumberCaptchaFragment : CaptchaFragment() {
  private val viewModel by activityViewModels<ChangeNumberViewModel>()

  override fun handleCaptchaToken(token: String) {
    viewModel.setCaptchaResponse(token)
  }
}
