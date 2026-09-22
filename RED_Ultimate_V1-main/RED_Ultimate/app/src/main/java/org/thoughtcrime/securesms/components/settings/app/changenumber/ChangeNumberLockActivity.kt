/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.changenumber

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.logging.Log
import com.red.sovereign.MainActivity
import com.red.sovereign.PassphraseRequiredActivity
import com.red.sovereign.R
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.AccountConsistencyWorkerJob
import com.red.sovereign.jobs.PreKeysSyncJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.logsubmit.SubmitDebugLogActivity
import com.red.sovereign.util.DynamicNoActionBarTheme
import com.red.sovereign.util.DynamicTheme
import com.red.sovereign.util.REDE164Util
import com.red.sovereign.util.SystemWindowInsetsSetter

/**
 * A captive activity that can determine if an interrupted/erred change number request
 * caused a disparity between the server and our locally stored number.
 */
class ChangeNumberLockActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG: String = Log.tag(ChangeNumberLockActivity::class.java)

    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, ChangeNumberLockActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    }
  }

  private val viewModel: ChangeNumberViewModel by viewModels()
  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    dynamicTheme.onCreate(this)

    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          Log.d(TAG, "Back button press swallowed.")
        }
      }
    )

    setContentView(R.layout.activity_change_number_lock)

    SystemWindowInsetsSetter.attach(findViewById(R.id.change_number_lock_scroll), this, WindowInsetsCompat.Type.systemBars())

    reattemptChange()
  }

  private fun reattemptChange() {
    val metadata = REDStore.misc.pendingChangeNumberMetadata
    if (metadata != null && metadata.newE164 != "") {
      viewModel.reattemptChangeLocalNumber(::onChangeStatusConfirmed, ::onFailedToGetChangeNumberStatus)
    } else {
      onMissingChangeNumberMetadata()
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  private fun onChangeStatusConfirmed() {
    REDStore.misc.clearPendingChangeNumberMetadata()

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.ChangeNumberLockActivity__change_status_confirmed)
      .setMessage(getString(R.string.ChangeNumberLockActivity__your_number_has_been_confirmed_as_s, REDE164Util.prettyPrint(REDStore.account.e164!!)))
      .setPositiveButton(android.R.string.ok) { _, _ ->
        startActivity(MainActivity.clearTop(this))
        finish()
      }
      .setCancelable(false)
      .show()
  }

  private fun onFailedToGetChangeNumberStatus(error: Throwable) {
    Log.w(TAG, "Unable to determine status of change number", error)

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.ChangeNumberLockActivity__change_status_unconfirmed)
      .setMessage(getString(R.string.ChangeNumberLockActivity__we_could_not_determine_the_status_of_your_change_number_request, error.javaClass.simpleName))
      .setPositiveButton(R.string.ChangeNumberLockActivity__retry) { _, _ -> reattemptChange() }
      .setNegativeButton(R.string.ChangeNumberLockActivity__leave) { _, _ -> finish() }
      .setNeutralButton(R.string.ChangeNumberLockActivity__submit_debug_log) { _, _ ->
        startActivity(Intent(this, SubmitDebugLogActivity::class.java))
        finish()
      }
      .setCancelable(false)
      .show()
  }

  private fun onMissingChangeNumberMetadata() {
    Log.w(TAG, "Change number metadata is missing, gonna let it ride but this shouldn't happen")

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.ChangeNumberLockActivity__change_status_unconfirmed)
      .setMessage(getString(R.string.ChangeNumberLockActivity__we_could_not_determine_the_status_of_your_change_number_request, "MissingMetadata"))
      .setPositiveButton(android.R.string.ok) { _, _ ->
        REDStore.misc.unlockChangeNumber()

        AppDependencies
          .jobManager
          .startChain(PreKeysSyncJob.create())
          .then(AccountConsistencyWorkerJob())
          .enqueue()

        startActivity(MainActivity.clearTop(this))
        finish()
      }
      .setNeutralButton(R.string.ChangeNumberLockActivity__submit_debug_log) { _, _ ->
        startActivity(Intent(this, SubmitDebugLogActivity::class.java))
        finish()
      }
      .setCancelable(false)
      .show()
  }
}
