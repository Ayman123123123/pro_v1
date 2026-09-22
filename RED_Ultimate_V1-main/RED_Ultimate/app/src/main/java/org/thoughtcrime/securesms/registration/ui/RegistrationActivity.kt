/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.ActivityNavigator
import org.signal.registration.RegistrationRoute
import com.red.sovereign.BaseActivity
import com.red.sovereign.MainActivity
import com.red.sovereign.R
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.registration.sms.SmsRetrieverReceiver
import com.red.sovereign.registration.util.RegistrationUtil
import com.red.sovereign.util.DynamicNoActionBarTheme
import com.red.sovereign.util.Environment

/**
 * Activity to hold the entire registration process.
 */
class RegistrationActivity : BaseActivity() {

  private val dynamicTheme = DynamicNoActionBarTheme()
  val sharedViewModel: RegistrationViewModel by viewModels()

  private var smsRetrieverReceiver: SmsRetrieverReceiver? = null

  init {
    lifecycle.addObserver(SmsRetrieverObserver())
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    dynamicTheme.onCreate(this)

    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_registration_navigation_v3)

    sharedViewModel.isReregister = intent.getBooleanExtra(RE_REGISTRATION_EXTRA, false)

    sharedViewModel.checkpoint.observe(this) {
      if (it >= RegistrationCheckpoint.LOCAL_REGISTRATION_COMPLETE) {
        RegistrationUtil.maybeMarkRegistrationComplete()
        handleSuccessfulVerify()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  private fun handleSuccessfulVerify() {
    if (REDStore.account.isPrimaryDevice && REDStore.account.isMultiDevice) {
      REDStore.misc.shouldShowLinkedDevicesReminder = sharedViewModel.isReregister
    }

    startActivity(MainActivity.clearTop(this))
    finish()
    ActivityNavigator.applyPopAnimationsToPendingTransition(this)
  }

  private inner class SmsRetrieverObserver : DefaultLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
      smsRetrieverReceiver = SmsRetrieverReceiver(application)
      smsRetrieverReceiver?.registerReceiver()
    }

    override fun onDestroy(owner: LifecycleOwner) {
      smsRetrieverReceiver?.unregisterReceiver()
      smsRetrieverReceiver = null
    }
  }

  companion object {
    const val RE_REGISTRATION_EXTRA: String = "re_registration"

    @JvmStatic
    fun newIntentForNewRegistration(context: Context, originalIntent: Intent): Intent {
      return if (Environment.USE_NEW_REGISTRATION) {
        org.signal.registration.RegistrationActivity.createIntent(context, nextIntent = MainActivity.clearTop(context))
      } else {
        Intent(context, RegistrationActivity::class.java).apply {
          putExtra(RE_REGISTRATION_EXTRA, false)
          setData(originalIntent.data)
        }
      }
    }

    @JvmStatic
    fun newIntentForReRegistration(context: Context): Intent {
      return if (Environment.USE_NEW_REGISTRATION) {
        org.signal.registration.RegistrationActivity.createIntent(context, nextIntent = MainActivity.clearTop(context), startFresh = true)
      } else {
        Intent(context, RegistrationActivity::class.java).apply {
          putExtra(RE_REGISTRATION_EXTRA, true)
        }
      }
    }

    @JvmStatic
    fun newIntentForReLinkDevice(context: Context): Intent {
      return if (Environment.USE_NEW_REGISTRATION) {
        org.signal.registration.RegistrationActivity.createIntent(
          context = context,
          nextIntent = MainActivity.clearTop(context),
          startDestination = RegistrationRoute.LinkAccount(showCreateAccount = false)
        )
      } else {
        Intent(context, RegistrationActivity::class.java)
      }
    }
  }
}
