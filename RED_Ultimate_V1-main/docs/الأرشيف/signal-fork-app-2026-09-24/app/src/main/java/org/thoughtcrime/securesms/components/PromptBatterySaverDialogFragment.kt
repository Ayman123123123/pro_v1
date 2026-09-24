/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.FixedRoundedCornerBottomSheetDialogFragment
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import com.red.sovereign.R
import com.red.sovereign.databinding.PromptBatterySaverBottomSheetBinding
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.notifications.DeviceSpecificNotificationConfig
import com.red.sovereign.util.LocalMetrics
import com.red.sovereign.util.PowerManagerCompat
import org.signal.core.ui.R as CoreUiR

class PromptBatterySaverDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  companion object {
    private val TAG = Log.tag(PromptBatterySaverDialogFragment::class.java)
    private const val ARG_LEARN_MORE_LINK = "arg.learn.more.link"

    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      if (fragmentManager.findFragmentByTag(BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG) == null) {
        val dialog = PromptBatterySaverDialogFragment().apply {
          arguments = bundleOf(
            ARG_LEARN_MORE_LINK to DeviceSpecificNotificationConfig.currentConfig.link
          )
        }
        BottomSheetUtil.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG, dialog)
        REDStore.uiHints.lastBatterySaverPrompt = System.currentTimeMillis()
      }
    }
  }

  override val peekHeightPercentage: Float = 0.66f
  override val themeResId: Int = R.style.Widget_RED_FixedRoundedCorners_Messages

  private val binding by ViewBinderDelegate(PromptBatterySaverBottomSheetBinding::bind)

  private val disposables: LifecycleDisposable = LifecycleDisposable()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.prompt_battery_saver_bottom_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    disposables.bindTo(viewLifecycleOwner)

    val learnMoreLink = arguments?.getString(ARG_LEARN_MORE_LINK) ?: getString(R.string.PromptBatterySaverBottomSheet__learn_more_url)
    binding.message.setLearnMoreVisible(true)
    binding.message.setLinkColor(ContextCompat.getColor(requireContext(), CoreUiR.color.signal_colorPrimary))
    binding.message.setLink(learnMoreLink)

    binding.continueButton.setOnClickListener {
      PowerManagerCompat.requestIgnoreBatteryOptimizations(requireContext())
      Log.i(TAG, "Requested to ignore battery optimizations, clearing local metrics.")
      LocalMetrics.clear()
      REDStore.uiHints.markDismissedBatterySaverPrompt()
      dismiss()
    }
    binding.dismissButton.setOnClickListener {
      Log.i(TAG, "User denied request to ignore battery optimizations.")
      REDStore.uiHints.markDismissedBatterySaverPrompt()
      dismiss()
    }
  }
}
