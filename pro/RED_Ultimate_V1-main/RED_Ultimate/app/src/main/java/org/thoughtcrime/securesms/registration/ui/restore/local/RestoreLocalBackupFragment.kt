/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.ui.restore.local

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.theme.REDTheme
import org.signal.core.util.logging.Log
import com.red.sovereign.MainActivity
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.registration.data.network.RegisterAccountResult
import com.red.sovereign.registration.ui.RegistrationViewModel
import com.red.sovereign.registration.ui.phonenumber.EnterPhoneNumberMode
import com.red.sovereign.registration.ui.restore.EnterBackupKeyViewModel
import com.red.sovereign.restore.RestoreActivity
import com.red.sovereign.util.CommunicationActions
import com.red.sovereign.util.navigation.safeNavigate

/**
 * Restore an on-device backup during registration
 */
class RestoreLocalBackupFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(RestoreLocalBackupFragment::class)
    private const val LEARN_MORE_URL = "https://support.red.local/hc/articles/360007059752"
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val enterBackupKeyViewModel by viewModels<EnterBackupKeyViewModel>()
  private lateinit var restoreLocalBackupViewModel: RestoreLocalBackupViewModel

  private val localBackupRestore = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
    when (val resultCode = result.resultCode) {
      Activity.RESULT_OK -> {
        sharedViewModel.onBackupSuccessfullyRestored()
        findNavController().safeNavigate(RestoreLocalBackupFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.RESTART_AFTER_COLLECTION))
      }

      Activity.RESULT_CANCELED -> {
        Log.w(TAG, "Backup restoration canceled.")
      }

      else -> Log.w(TAG, "Backup restoration activity ended with unknown result code: $resultCode")
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        sharedViewModel
          .state
          .map { it.registerAccountError }
          .filterNotNull()
          .collect {
            sharedViewModel.registerAccountErrorShown()
            if (it is RegisterAccountResult.IncorrectRecoveryPassword) {
              restoreLocalBackupViewModel.displayDifferentAccountWarning()
            } else {
              enterBackupKeyViewModel.handleRegistrationFailure(it)
            }
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val viewModel = viewModel<RestoreLocalBackupViewModel>()
    restoreLocalBackupViewModel = viewModel
    val state by viewModel.state.collectAsStateWithLifecycle()
    val registrationState by sharedViewModel.state.collectAsStateWithLifecycle()
    val enterBackupKeyState by enterBackupKeyViewModel.state.collectAsStateWithLifecycle()

    REDTheme {
      val activity = LocalActivity.current as FragmentActivity
      CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides activity) {
        RestoreLocalBackupNavDisplay(
          state = state,
          callback = remember { RestoreBackupCallback() },
          isRegistrationInProgress = registrationState.inProgress,
          enterBackupKeyState = enterBackupKeyState,
          enteredText = enterBackupKeyViewModel.enteredText
        )
      }
    }
  }

  private inner class RestoreBackupCallback : RestoreLocalBackupCallback {
    override fun setSelectedBackup(backup: SelectableBackup) {
      restoreLocalBackupViewModel.setSelectedBackup(backup)
    }

    override suspend fun setSelectedBackupDirectory(context: Context, uri: Uri): Boolean {
      return restoreLocalBackupViewModel.setSelectedBackupDirectory(context, uri)
    }

    override fun displaySkipRestoreWarning() {
      restoreLocalBackupViewModel.displaySkipRestoreWarning()
    }

    override fun clearDialog() {
      restoreLocalBackupViewModel.clearDialog()
    }

    override fun skipRestore() {
      sharedViewModel.skipRestore()
      if (REDStore.account.isRegistered) {
        viewLifecycleOwner.lifecycleScope.launch {
          restoreLocalBackupViewModel.performStorageServiceAccountRestoreIfNeeded()
          startActivity(MainActivity.clearTop(requireContext()))
        }
      } else {
        findNavController().safeNavigate(RestoreLocalBackupFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.RESTART_AFTER_COLLECTION))
      }
    }

    override fun confirmRestoreWithDifferentAccount() {
      resetStateAfterFailedAccountMatch()
      findNavController().safeNavigate(
        RestoreLocalBackupFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.RESTART_AFTER_COLLECTION)
      )
    }

    override fun denyRestoreWithDifferentAccount() {
      resetStateAfterFailedAccountMatch()
      REDStore.backup.localRestoreAccountEntropyPool = null
      findNavController().safeNavigate(
        RestoreLocalBackupFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.COLLECT_FOR_LOCAL_V2_SIGNAL_BACKUPS_RESTORE)
      )
    }

    private fun resetStateAfterFailedAccountMatch() {
      REDStore.account.resetAccountEntropyPool()
      REDStore.account.resetAciAndPniIdentityKeysAfterFailedRestore()
      sharedViewModel.clearRecoveryPassword()
      enterBackupKeyViewModel.cancelRegistering()
      sharedViewModel.intendToRestore(hasOldDevice = false, fromRemote = false, fromLocalV2 = true)
    }

    override fun routeToLegacyBackupRestoration(uri: Uri) {
      sharedViewModel.intendToRestore(hasOldDevice = false, fromRemote = false, fromLocalV2 = false)
      localBackupRestore.launch(RestoreActivity.getLocalRestoreIntent(requireContext(), uri))
    }

    override fun submitBackupKey() {
      enterBackupKeyViewModel.registering()

      REDStore.backup.localRestoreAccountEntropyPool = enterBackupKeyViewModel.backupKey

      val selectedTimestamp = restoreLocalBackupViewModel.state.value.selectedBackup?.timestamp ?: -1L
      REDStore.backup.newLocalBackupsSelectedSnapshotTimestamp = selectedTimestamp

      sharedViewModel.registerWithBackupKey(
        context = requireContext(),
        backupKey = enterBackupKeyViewModel.backupKey,
        e164 = null,
        pin = null,
        aciIdentityKeyPair = null,
        pniIdentityKeyPair = null
      )
    }

    override fun onBackupKeyChanged(key: String) {
      enterBackupKeyViewModel.updateBackupKey(key)
      val timestamp = restoreLocalBackupViewModel.state.value.selectedBackup?.timestamp ?: return
      enterBackupKeyViewModel.verifyLocalBackupKey(timestamp)
    }

    override fun clearRegistrationError() {
      enterBackupKeyViewModel.clearRegistrationError()
    }

    override fun onBackupKeyHelp() {
      CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL)
    }
  }
}
