/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.devicetransfer.newdevice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.red.sovereign.database.model.databaseprotos.RestoreDecisionState
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.ReclaimUsernameAndLinkJob
import com.red.sovereign.keyvalue.Completed
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.registration.data.RegistrationRepository
import com.red.sovereign.registration.util.RegistrationUtil

class NewDeviceTransferViewModel : ViewModel() {
  fun onRestoreComplete(context: Context, onComplete: () -> Unit) {
    viewModelScope.launch {
      REDStore.registration.localRegistrationMetadata?.let { metadata ->
        RegistrationRepository.registerAccountLocally(context, metadata)
        REDStore.registration.localRegistrationMetadata = null
        RegistrationUtil.maybeMarkRegistrationComplete()

        REDStore.misc.needsUsernameRestore = true
        AppDependencies.jobManager.add(ReclaimUsernameAndLinkJob())
      }

      REDStore.registration.restoreDecisionState = RestoreDecisionState.Completed

      withContext(Dispatchers.Main) {
        onComplete()
      }
    }
  }
}
