/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.changenumber

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.logging.LoggingFragment
import org.signal.core.util.logging.Log
import com.red.sovereign.R
import com.red.sovereign.components.ViewBinderDelegate
import com.red.sovereign.components.settings.app.changenumber.ChangeNumberUtil.changeNumberSuccess
import com.red.sovereign.databinding.FragmentChangeNumberEnterCodeBinding
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.registration.data.RegistrationRepository
import com.red.sovereign.registration.data.network.RegistrationResult
import com.red.sovereign.registration.data.network.VerificationCodeRequestResult
import com.red.sovereign.registration.fragments.ContactSupportBottomSheetFragment
import com.red.sovereign.registration.fragments.RegistrationViewDelegate
import com.red.sovereign.registration.fragments.REDStrengthPhoneStateListener
import com.red.sovereign.registration.sms.ReceivedSmsEvent
import com.red.sovereign.util.concurrent.AssertedSuccessListener
import com.red.sovereign.util.navigation.safeNavigate
import com.red.sovereign.util.visible
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

/**
 * Screen used to enter the registration code provided by the service.
 */
class ChangeNumberEnterCodeFragment : LoggingFragment(R.layout.fragment_change_number_enter_code) {

  companion object {
    private val TAG: String = Log.tag(ChangeNumberEnterCodeFragment::class.java)
    private const val BOTTOM_SHEET_TAG = "support_bottom_sheet"
  }

  private val viewModel by activityViewModels<ChangeNumberViewModel>()
  private val binding: FragmentChangeNumberEnterCodeBinding by ViewBinderDelegate(FragmentChangeNumberEnterCodeBinding::bind)
  private lateinit var phoneStateListener: REDStrengthPhoneStateListener

  private var autopilotCodeEntryActive = false

  private val bottomSheet = ContactSupportBottomSheetFragment()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.title = viewModel.number.fullFormattedNumber
    toolbar.setNavigationOnClickListener {
      Log.d(TAG, "Toolbar navigation clicked.")
      navigateUp()
    }

    binding.codeEntryLayout.verifyHeader.setOnClickListener(null)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          Log.d(TAG, "onBackPressed")
          navigateUp()
        }
      }
    )

    RegistrationViewDelegate.setDebugLogSubmitMultiTapView(binding.codeEntryLayout.verifyHeader)

    phoneStateListener = REDStrengthPhoneStateListener(this, PhoneStateCallback())

    binding.codeEntryLayout.wrongNumber.setOnClickListener {
      navigateUp()
    }

    binding.codeEntryLayout.code.setOnCompleteListener {
      viewModel.verifyCodeWithoutRegistrationLock(requireContext(), it, ::handleSessionErrorResponse, ::handleChangeNumberErrorResponse)
    }

    binding.codeEntryLayout.havingTroubleButton.setOnClickListener {
      bottomSheet.showSafely(childFragmentManager, BOTTOM_SHEET_TAG)
    }

    binding.codeEntryLayout.callMeCountDown.apply {
      setTextResources(R.string.RegistrationActivity_call, R.string.RegistrationActivity_call_me_instead_available_in)
      setOnClickListener {
        viewModel.initiateChangeNumberSession(requireContext(), RegistrationRepository.E164VerificationMode.PHONE_CALL)
      }
    }

    binding.codeEntryLayout.resendSmsCountDown.apply {
      setTextResources(R.string.RegistrationActivity_resend_code, R.string.RegistrationActivity_resend_sms_available_in)
      setOnClickListener {
        viewModel.initiateChangeNumberSession(requireContext(), RegistrationRepository.E164VerificationMode.SMS_WITHOUT_LISTENER)
      }
    }

    binding.codeEntryLayout.keyboard.setOnKeyPressListener { key ->
      if (!autopilotCodeEntryActive) {
        if (key >= 0) {
          binding.codeEntryLayout.code.append(key)
        } else {
          binding.codeEntryLayout.code.delete()
        }
      }
    }

    viewModel.incorrectCodeAttempts.observe(viewLifecycleOwner) { attempts: Int ->
      if (attempts >= 3) {
        binding.codeEntryLayout.havingTroubleButton.visible = true
      }
    }

    viewModel.uiState.observe(viewLifecycleOwner, ::onStateUpdate)
  }

  private fun onStateUpdate(state: ChangeNumberState) {
    binding.codeEntryLayout.resendSmsCountDown.startCountDownTo(state.nextSmsTimestamp.milliseconds)
    binding.codeEntryLayout.callMeCountDown.startCountDownTo(state.nextCallTimestamp.milliseconds)
    when (val outcome = state.changeNumberOutcome) {
      is ChangeNumberOutcome.Succeeded -> changeNumberSuccess()

      is ChangeNumberOutcome.ChangeNumberRequestOutcome -> if (!state.inProgress && !outcome.result.isSuccess()) {
        if (outcome.result is VerificationCodeRequestResult.RequestVerificationCodeRateLimited) {
          Log.i(TAG, "Verification code request rate limited; staying on code entry screen.")
        } else {
          presentGenericError(outcome.result)
        }
      }

      null -> Unit
    }
    if (state.inProgress) {
      binding.codeEntryLayout.keyboard.displayProgress()
    } else {
      binding.codeEntryLayout.keyboard.displayKeyboard()
    }
  }

  private fun navigateUp() {
    if (REDStore.misc.isChangeNumberLocked) {
      Log.d(TAG, "Change number locked, navigateUp")
      startActivity(ChangeNumberLockActivity.createIntent(requireContext()))
    } else {
      Log.d(TAG, "navigateUp")
      findNavController().navigateUp()
    }
  }

  private fun handleSessionErrorResponse(result: VerificationCodeRequestResult) {
    when (result) {
      is VerificationCodeRequestResult.Success -> binding.codeEntryLayout.keyboard.displaySuccess()
      is VerificationCodeRequestResult.RateLimited -> presentRateLimitedDialog()
      is VerificationCodeRequestResult.RequestVerificationCodeRateLimited -> presentRateLimitedDialog(retryAfterSeconds = (result.nextSmsTimestamp - System.currentTimeMillis().milliseconds).inWholeSeconds.coerceAtLeast(0))
      is VerificationCodeRequestResult.SubmitVerificationCodeRateLimited -> presentRateLimitedDialog()
      is VerificationCodeRequestResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      else -> presentGenericError(result)
    }
  }

  private fun handleChangeNumberErrorResponse(result: ChangeNumberResult) {
    when (result) {
      is ChangeNumberResult.Success -> binding.codeEntryLayout.keyboard.displaySuccess()
      is ChangeNumberResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      is ChangeNumberResult.AuthorizationFailed -> presentIncorrectCodeDialog()
      is ChangeNumberResult.AttemptsExhausted -> presentAccountLocked()
      is ChangeNumberResult.RateLimited -> presentRateLimitedDialog(result.timeRemaining)

      else -> presentGenericError(result)
    }
  }

  private fun presentAccountLocked() {
    binding.codeEntryLayout.keyboard.displayLocked().addListener(
      object : AssertedSuccessListener<Boolean>() {
        override fun onSuccess(result: Boolean?) {
          findNavController().safeNavigate(ChangeNumberEnterCodeFragmentDirections.actionChangeNumberEnterCodeFragmentToChangeNumberAccountLocked())
        }
      }
    )
  }

  private fun presentRegistrationLocked(timeRemaining: Long) {
    binding.codeEntryLayout.keyboard.displayLocked().addListener(
      object : AssertedSuccessListener<Boolean>() {
        override fun onSuccess(result: Boolean?) {
          Log.i(TAG, "Account is registration locked, cannot register.")
          findNavController().safeNavigate(ChangeNumberEnterCodeFragmentDirections.actionChangeNumberEnterCodeFragmentToChangeNumberRegistrationLock(timeRemaining))
        }
      }
    )
  }

  private fun presentRateLimitedDialog(retryAfterSeconds: Long = 0) {
    binding.codeEntryLayout.keyboard.displayFailure().addListener(
      object : AssertedSuccessListener<Boolean?>() {
        override fun onSuccess(result: Boolean?) {
          MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle(R.string.RegistrationActivity_too_many_attempts)
            if (retryAfterSeconds > 0) {
              val minutes = ceil(retryAfterSeconds / 60.0).toInt().coerceAtLeast(1)
              setMessage(
                if (minutes >= 60) {
                  val hours = ceil(minutes / 60.0).toInt()
                  resources.getQuantityString(R.plurals.ChangeNumberEnterCodeFragment__too_many_attempts_try_again_in_hours, hours, hours)
                } else {
                  resources.getQuantityString(R.plurals.ChangeNumberEnterCodeFragment__too_many_attempts_try_again_in_minutes, minutes, minutes)
                }
              )
            } else {
              setMessage(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
            }
            setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
              binding.codeEntryLayout.callMeCountDown.visibility = View.VISIBLE
              binding.codeEntryLayout.resendSmsCountDown.visibility = View.VISIBLE
              binding.codeEntryLayout.wrongNumber.visibility = View.VISIBLE
              binding.codeEntryLayout.code.clear()
              binding.codeEntryLayout.keyboard.displayKeyboard()
            }
            show()
          }
        }
      }
    )
  }

  private fun presentIncorrectCodeDialog() {
    viewModel.incrementIncorrectCodeAttempts()

    Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_code, Toast.LENGTH_LONG).show()
    binding.codeEntryLayout.keyboard.displayFailure().addListener(object : AssertedSuccessListener<Boolean?>() {
      override fun onSuccess(result: Boolean?) {
        binding.codeEntryLayout.callMeCountDown.setVisibility(View.VISIBLE)
        binding.codeEntryLayout.resendSmsCountDown.setVisibility(View.VISIBLE)
        binding.codeEntryLayout.wrongNumber.setVisibility(View.VISIBLE)
        binding.codeEntryLayout.code.clear()
        binding.codeEntryLayout.keyboard.displayKeyboard()
      }
    })
  }

  private fun presentGenericError(requestResult: RegistrationResult) {
    binding.codeEntryLayout.keyboard.displayFailure().addListener(
      object : AssertedSuccessListener<Boolean>() {
        override fun onSuccess(result: Boolean?) {
          Log.w(TAG, "Encountered unexpected error!", requestResult.getCause())
          MaterialAlertDialogBuilder(requireContext()).apply {
            null?.let<String, MaterialAlertDialogBuilder> {
              setTitle(it)
            }
            setMessage(getString(R.string.RegistrationActivity_error_connecting_to_service))
            setPositiveButton(android.R.string.ok) { _, _ ->
              navigateUp()
              viewModel.resetLocalSessionState()
            }
            show()
          }
        }
      }
    )
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onVerificationCodeReceived(event: ReceivedSmsEvent) {
    binding.codeEntryLayout.code.clear()

    if (event.code.isBlank() || event.code.length != ReceivedSmsEvent.CODE_LENGTH) {
      Log.i(TAG, "Received invalid code of length ${event.code.length}. Ignoring.")
      return
    }

    val finalIndex = ReceivedSmsEvent.CODE_LENGTH - 1
    autopilotCodeEntryActive = true
    try {
      event.code
        .map { it.digitToInt() }
        .forEachIndexed { i, digit ->
          binding.codeEntryLayout.code.postDelayed({
            binding.codeEntryLayout.code.append(digit)
            if (i == finalIndex) {
              autopilotCodeEntryActive = false
            }
          }, i * 200L)
        }
    } catch (notADigit: IllegalArgumentException) {
      Log.w(TAG, "Failed to convert code into digits.", notADigit)
      autopilotCodeEntryActive = false
    }
  }

  private inner class PhoneStateCallback : REDStrengthPhoneStateListener.Callback {
    override fun onNoCellREDPresent() {
      bottomSheet.showSafely(childFragmentManager, BOTTOM_SHEET_TAG)
    }

    override fun onCellREDPresent() {
      if (bottomSheet.isResumed) {
        bottomSheet.dismiss()
      }
    }
  }
}
