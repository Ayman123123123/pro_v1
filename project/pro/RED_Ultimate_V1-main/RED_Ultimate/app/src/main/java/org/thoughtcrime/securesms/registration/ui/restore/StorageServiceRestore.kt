/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.ui.restore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.enqueueBlocking
import com.red.sovereign.jobmanager.runJobBlocking
import com.red.sovereign.jobs.ProfileUploadJob
import com.red.sovereign.jobs.ReclaimUsernameAndLinkJob
import com.red.sovereign.jobs.StorageAccountRestoreJob
import com.red.sovereign.jobs.StorageSyncJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.registration.data.RegistrationRepository
import com.red.sovereign.registration.util.RegistrationUtil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object StorageServiceRestore {
  private val TAG = Log.tag(StorageServiceRestore::class)

  /**
   * Restore account data from Storage Service in a quasi-blocking manner. Uses existing jobs
   * to perform the restore but will not wait indefinitely for them to finish so may return prior
   * to completing the restore.
   */
  suspend fun restore() {
    withContext(Dispatchers.IO) {
      val stopwatch = Stopwatch("storage-service-restore")

      REDStore.storageService.needsAccountRestore = false

      AppDependencies.jobManager.runJobBlocking(StorageAccountRestoreJob(), StorageAccountRestoreJob.LIFESPAN.milliseconds)
      stopwatch.split("account-restore")

      AppDependencies
        .jobManager
        .startChain(StorageSyncJob.forAccountRestore())
        .then(ReclaimUsernameAndLinkJob())
        .enqueueBlocking(10.seconds)
      stopwatch.split("storage-sync-restore")

      stopwatch.stop(TAG)

      val isMissingProfileData = RegistrationRepository.isMissingProfileData()

      RegistrationUtil.maybeMarkRegistrationComplete()
      if (!isMissingProfileData && REDStore.account.isPrimaryDevice) {
        AppDependencies.jobManager.add(ProfileUploadJob())
      }
    }
  }
}
