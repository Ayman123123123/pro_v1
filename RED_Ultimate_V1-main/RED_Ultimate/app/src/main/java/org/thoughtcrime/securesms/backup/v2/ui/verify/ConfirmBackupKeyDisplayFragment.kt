package com.red.sovereign.backup.v2.ui.verify

import android.app.Activity.RESULT_OK
import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import com.red.sovereign.backup.v2.ui.subscription.MessageBackupsKeyVerifyScreen
import com.red.sovereign.keyvalue.REDStore

/**
 * Fragment to confirm the backup key just shown after users forget it.
 */
class ConfirmBackupKeyDisplayFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    MessageBackupsKeyVerifyScreen(
      backupKey = REDStore.account.accountEntropyPool.displayValue,
      onNavigationClick = {
        requireActivity().supportFragmentManager.popBackStack()
      },
      onNextClick = {
        REDStore.backup.lastVerifyKeyTime = System.currentTimeMillis()
        REDStore.backup.hasVerifiedBefore = true
        REDStore.backup.hasSnoozedVerified = false
        requireActivity().setResult(RESULT_OK)
        requireActivity().finish()
      }
    )
  }
}
