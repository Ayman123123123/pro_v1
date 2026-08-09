/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import com.red.sovereign.backup.v2.BackupRepository
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.keyvalue.REDStore
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Notifies the server that the backup for the local user is still being used.
 */
class BackupRefreshJob private constructor(
  parameters: Parameters
) : Job(parameters) {

  companion object {
    private val TAG = Log.tag(BackupRefreshJob::class)
    const val KEY = "BackupRefreshJob"

    private val TIME_BETWEEN_CHECKINS = 1.days

    @JvmStatic
    fun enqueueIfNecessary() {
      if (!canExecuteJob()) {
        return
      }

      val now = System.currentTimeMillis().milliseconds
      val lastCheckIn = REDStore.backup.lastCheckInMillis.milliseconds

      if ((now - lastCheckIn) >= TIME_BETWEEN_CHECKINS) {
        AppDependencies.jobManager.add(
          BackupRefreshJob(
            parameters = Parameters.Builder()
              .addConstraint(NetworkConstraint.KEY)
              .setMaxAttempts(Parameters.UNLIMITED)
              .setLifespan(1.days.inWholeMilliseconds)
              .setMaxInstancesForFactory(1)
              .build()
          )
        )
      } else {
        Log.i(TAG, "Do not need to refresh backups. Last refresh: ${lastCheckIn.inWholeMilliseconds}")
      }
    }

    private fun canExecuteJob(): Boolean {
      if (!REDStore.account.isRegistered) {
        Log.i(TAG, "Account not registered. Exiting.")
        return false
      }

      if (!REDStore.backup.areBackupsEnabled) {
        Log.i(TAG, "Backups have not been enabled on this device. Exiting.")
        return false
      }

      return true
    }
  }

  override fun run(): Result {
    if (!canExecuteJob()) {
      return Result.success()
    }

    val result = BackupRepository.refreshBackup()

    return when (result) {
      is NetworkResult.Success -> {
        REDStore.backup.lastCheckInMillis = System.currentTimeMillis()
        REDStore.backup.lastCheckInSnoozeMillis = 0
        Result.success()
      }
      is NetworkResult.NetworkError -> {
        Log.w(TAG, "Network error when refreshing backup.", result.getCause())
        Result.retry(defaultBackoff())
      }
      is NetworkResult.StatusCodeError -> {
        Log.w(TAG, "Status code error (${result.code}) when refreshing backup.", result.getCause())
        if (result.code == 429) {
          Result.retry(result.retryAfter()?.inWholeMilliseconds ?: defaultBackoff())
        } else {
          Result.failure()
        }
      }
      is NetworkResult.ApplicationError -> {
        Log.w(TAG, "Application error when refreshing backup.", result.throwable)
        Result.failure()
      }
    }
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  class Factory : Job.Factory<BackupRefreshJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BackupRefreshJob {
      return BackupRefreshJob(parameters)
    }
  }
}
