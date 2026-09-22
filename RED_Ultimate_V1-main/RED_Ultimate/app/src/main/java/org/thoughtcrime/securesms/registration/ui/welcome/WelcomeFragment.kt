/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.ui.welcome

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.logging.LoggingFragment
import org.signal.core.util.getSerializableCompat
import org.signal.core.util.logging.Log
import com.red.sovereign.BuildConfig
import com.red.sovereign.R
import com.red.sovereign.components.ViewBinderDelegate
import com.red.sovereign.databinding.FragmentRegistrationWelcomeV3Binding
import com.red.sovereign.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import com.red.sovereign.registration.fragments.WelcomePermissions
import com.red.sovereign.registration.ui.RegistrationCheckpoint
import com.red.sovereign.registration.ui.RegistrationViewModel
import com.red.sovereign.registration.ui.permissions.GrantPermissionsFragment
import com.red.sovereign.registration.ui.phonenumber.EnterPhoneNumberMode
import com.red.sovereign.util.BackupUtil
import com.red.sovereign.util.CommunicationActions
import com.red.sovereign.util.SystemWindowInsetsSetter
import com.red.sovereign.util.navigation.safeNavigate
import com.red.sovereign.util.visible

/**
 * First screen that is displayed on the very first app launch.
 */
class WelcomeFragment : LoggingFragment(R.layout.fragment_registration_welcome_v3) {
  companion object {
    private val TAG = Log.tag(WelcomeFragment::class.java)
    private const val TERMS_AND_CONDITIONS_URL = "https://red.local/legal"
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val binding: FragmentRegistrationWelcomeV3Binding by ViewBinderDelegate(FragmentRegistrationWelcomeV3Binding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    SystemWindowInsetsSetter.attach(view, viewLifecycleOwner)

    setDebugLogSubmitMultiTapView(binding.image)
    setDebugLogSubmitMultiTapView(binding.title)

    binding.welcomeContinueButton.setOnClickListener { onContinueClicked() }
    binding.welcomeTermsButton.setOnClickListener { onTermsClicked() }
    binding.welcomeTransferOrRestore.setOnClickListener { onRestoreOrTransferClicked() }
    binding.welcomeTransferOrRestore.visible = !sharedViewModel.isReregister

    if (BuildConfig.LINK_DEVICE_UX_ENABLED) {
      binding.image.setOnLongClickListener {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage("Link device?")
          .setPositiveButton("Link", { _, _ -> onLinkDeviceClicked() })
          .setNegativeButton(android.R.string.cancel, null)
          .show()
        true
      }
    }

    childFragmentManager.setFragmentResultListener(RestoreWelcomeBottomSheet.REQUEST_KEY, viewLifecycleOwner) { requestKey, bundle ->
      if (requestKey == RestoreWelcomeBottomSheet.REQUEST_KEY) {
        when (val userSelection = bundle.getSerializableCompat(RestoreWelcomeBottomSheet.REQUEST_KEY, WelcomeUserSelection::class.java)) {
          WelcomeUserSelection.RESTORE_WITH_OLD_PHONE,
          WelcomeUserSelection.RESTORE_WITH_NO_PHONE -> afterRestoreOrTransferClicked(userSelection)
          else -> Unit
        }
      }
    }

    parentFragmentManager.setFragmentResultListener(GrantPermissionsFragment.REQUEST_KEY, viewLifecycleOwner) { requestKey, bundle ->
      if (requestKey == GrantPermissionsFragment.REQUEST_KEY) {
        when (val userSelection = bundle.getSerializableCompat(GrantPermissionsFragment.REQUEST_KEY, WelcomeUserSelection::class.java)) {
          WelcomeUserSelection.RESTORE_WITH_OLD_PHONE,
          WelcomeUserSelection.RESTORE_WITH_NO_PHONE -> navigateToNextScreenViaRestore(userSelection)
          WelcomeUserSelection.CONTINUE -> navigateToNextScreenViaContinue()
          WelcomeUserSelection.LINK -> navigateToLinkDevice()
          null -> Unit
        }
      }
    }
  }

  private fun onLinkDeviceClicked() {
    if (!hasAllPermissions()) {
      findNavController().safeNavigate(WelcomeFragmentDirections.actionWelcomeFragmentToGrantPermissionsFragment(WelcomeUserSelection.LINK))
    } else {
      navigateToLinkDevice()
    }
  }

  private fun navigateToLinkDevice() {
    findNavController().safeNavigate(WelcomeFragmentDirections.goToLinkViaQr())
  }

  override fun onResume() {
    super.onResume()
    sharedViewModel.resetRestoreDecision()
  }

  private fun onContinueClicked() {
    if (!hasAllPermissions()) {
      findNavController().safeNavigate(WelcomeFragmentDirections.actionWelcomeFragmentToGrantPermissionsFragment(WelcomeUserSelection.CONTINUE))
    } else {
      navigateToNextScreenViaContinue()
    }
  }

  private fun navigateToNextScreenViaContinue() {
    sharedViewModel.maybePrefillE164(requireContext())
    findNavController().safeNavigate(WelcomeFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.NORMAL))
  }

  private fun onTermsClicked() {
    CommunicationActions.openBrowserLink(requireContext(), TERMS_AND_CONDITIONS_URL)
  }

  private fun onRestoreOrTransferClicked() {
    RestoreWelcomeBottomSheet().show(childFragmentManager, null)
  }

  private fun afterRestoreOrTransferClicked(userSelection: WelcomeUserSelection) {
    if (!hasAllPermissions()) {
      findNavController().safeNavigate(WelcomeFragmentDirections.actionWelcomeFragmentToGrantPermissionsFragment(userSelection))
    } else {
      navigateToNextScreenViaRestore(userSelection)
    }
  }

  private fun navigateToNextScreenViaRestore(userSelection: WelcomeUserSelection) {
    sharedViewModel.maybePrefillE164(requireContext())
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PERMISSIONS_GRANTED)

    when (userSelection) {
      WelcomeUserSelection.LINK,
      WelcomeUserSelection.CONTINUE -> throw IllegalArgumentException()
      WelcomeUserSelection.RESTORE_WITH_OLD_PHONE -> {
        sharedViewModel.intendToRestore(hasOldDevice = true, fromRemote = true)
        findNavController().safeNavigate(WelcomeFragmentDirections.goToRestoreViaQr())
      }
      WelcomeUserSelection.RESTORE_WITH_NO_PHONE -> {
        sharedViewModel.intendToRestore(hasOldDevice = false, fromRemote = true)
        findNavController().safeNavigate(WelcomeFragmentDirections.goToSelectRestoreMethod(userSelection))
      }
    }
  }

  private fun hasAllPermissions(): Boolean {
    val isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext())
    return WelcomePermissions.getWelcomePermissions(isUserSelectionRequired).all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }
  }
}
