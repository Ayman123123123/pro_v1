/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.restore.local

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.theme.REDTheme
import com.red.sovereign.MainActivity
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.registration.ui.restore.EnterBackupKeyViewModel
import com.red.sovereign.registration.ui.restore.local.RestoreLocalBackupActivity
import com.red.sovereign.registration.ui.restore.local.RestoreLocalBackupCallback
import com.red.sovereign.registration.ui.restore.local.RestoreLocalBackupNavDisplay
import com.red.sovereign.registration.ui.restore.local.RestoreLocalBackupViewModel
import com.red.sovereign.registration.ui.restore.local.SelectableBackup
import com.red.sovereign.restore.RestoreViewModel
import com.red.sovereign.util.CommunicationActions
import com.red.sovereign.util.navigation.safeNavigate

/**
 * Post Registration restore fragment for V2 backups.
 */
class PostRegistrationRestoreLocalBackupFragment : ComposeFragment() {

  companion object {
    private const val LEARN_MORE_URL = "https://support.red.local/hc/articles/360007059752"
  }

  private val sharedViewModel: RestoreViewModel by activityViewModels()
  private val restoreLocalBackupViewModel by viewModels<RestoreLocalBackupViewModel>()
  private val enterBackupKeyViewModel by viewModels<EnterBackupKeyViewModel>()

  @Composable
  override fun FragmentContent() {
    val state by restoreLocalBackupViewModel.state.collectAsStateWithLifecycle()
    val enterBackupKeyState by enterBackupKeyViewModel.state.collectAsStateWithLifecycle()

    REDTheme {
      val activity = LocalActivity.current as FragmentActivity
      CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides activity) {
        RestoreLocalBackupNavDisplay(
          state = state,
          callback = remember { Callbacks() },
          isRegistrationInProgress = false,
          enterBackupKeyState = enterBackupKeyState,
          enteredText = enterBackupKeyViewModel.enteredText
        )
      }
    }
  }

  private inner class Callbacks : RestoreLocalBackupCallback {
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

      viewLifecycleOwner.lifecycleScope.launch {
        sharedViewModel.performStorageServiceAccountRestoreIfNeeded()

        withContext(Dispatchers.Main) {
          startActivity(MainActivity.clearTop(requireContext()))
          activity?.finish()
        }
      }
    }

    override fun confirmRestoreWithDifferentAccount() {
      launchRestore()
    }

    override fun denyRestoreWithDifferentAccount() {
      restoreLocalBackupViewModel.clearDialog()
    }

    override fun submitBackupKey() {
      AccountEntropyPool.parseOrNull(enterBackupKeyViewModel.backupKey) ?: return
      val selectedTimestamp = restoreLocalBackupViewModel.state.value.selectedBackup?.timestamp ?: -1L

      viewLifecycleOwner.lifecycleScope.launch {
        val belongsToCurrentAccount = restoreLocalBackupViewModel.backupBelongsToCurrentAccount(
          context = requireContext(),
          backupKey = enterBackupKeyViewModel.backupKey,
          timestamp = selectedTimestamp
        )

        if (belongsToCurrentAccount) {
          launchRestore()
        } else {
          restoreLocalBackupViewModel.displayDifferentAccountWarning()
        }
      }
    }

    private fun launchRestore() {
      val selectedTimestamp = restoreLocalBackupViewModel.state.value.selectedBackup?.timestamp ?: -1L
      REDStore.backup.localRestoreAccountEntropyPool = enterBackupKeyViewModel.backupKey
      REDStore.backup.newLocalBackupsSelectedSnapshotTimestamp = selectedTimestamp
      startActivity(RestoreLocalBackupActivity.getIntent(requireContext()))
      requireActivity().supportFinishAfterTransition()
    }

    override fun routeToLegacyBackupRestoration(uri: Uri) {
      sharedViewModel.setBackupFileUri(uri)
      findNavController().safeNavigate(PostRegistrationRestoreLocalBackupFragmentDirections.restoreLocalV1Backup())
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
