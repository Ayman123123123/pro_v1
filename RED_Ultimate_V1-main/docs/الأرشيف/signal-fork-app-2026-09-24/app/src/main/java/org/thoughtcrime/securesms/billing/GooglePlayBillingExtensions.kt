/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.billing

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.red.sovereign.R
import com.red.sovereign.dependencies.GooglePlayBillingDependencies

/**
 * Launches user to the Google Play backups management screen.
 */
fun Fragment.launchManageBackupsSubscription() {
  lifecycleScope.launch(Dispatchers.Main) {
    val uri = withContext(Dispatchers.Default) {
      Uri.parse(
        getString(
          R.string.backup_subscription_management_url,
          GooglePlayBillingDependencies.getProductId(),
          requireContext().applicationInfo.packageName
        )
      )
    }

    val intent = Intent(Intent.ACTION_VIEW, uri)
    startActivity(intent)
  }
}
