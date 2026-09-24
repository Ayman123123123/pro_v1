/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import org.signal.core.util.Util
import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import com.red.sovereign.BuildConfig
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.REDNetwork
import org.whispersystems.signalservice.api.remoteconfig.RemoteConfigResult
import kotlin.time.Duration.Companion.days

/**
 * If we have reason to believe a build is expired, we run this job to double-check by fetching the server time. This prevents false positives from people
 * moving their clock forward in time.
 */
class BuildExpirationConfirmationJob private constructor(params: Parameters) : Job(params) {
  companion object {
    const val KEY = "BuildExpirationConfirmationJob"
    private val TAG = Log.tag(BuildExpirationConfirmationJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(2)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(1.days.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (Util.getTimeUntilBuildExpiry(REDStore.misc.estimatedServerTime) > 0) {
      Log.i(TAG, "Build not expired.", true)
      return Result.success()
    }

    if (REDStore.misc.isClientDeprecated) {
      Log.i(TAG, "Build already marked expired. Nothing to do.", true)
      return Result.success()
    }

    if (!REDStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Can't check the server time, so assuming deprecated.", true)
      REDStore.misc.isClientDeprecated = true
      return Result.success()
    }

    return when (val result: NetworkResult<RemoteConfigResult> = REDNetwork.remoteConfig.getRemoteConfig()) {
      is NetworkResult.Success -> {
        val serverTimeMs = result.result.serverEpochTimeMilliseconds
        REDStore.misc.setLastKnownServerTime(serverTimeMs, System.currentTimeMillis())

        if (Util.getTimeUntilBuildExpiry(serverTimeMs) <= 0) {
          Log.w(TAG, "Build confirmed expired! Server time: $serverTimeMs, Local time: ${System.currentTimeMillis()}, Build time: ${BuildConfig.BUILD_TIMESTAMP}, Time since expiry: ${serverTimeMs - BuildConfig.BUILD_TIMESTAMP}", true)
          REDStore.misc.isClientDeprecated = true
        } else {
          Log.w(TAG, "Build not actually expired! Likely bad local clock. Server time: $serverTimeMs, Local time: ${System.currentTimeMillis()}, Build time: ${BuildConfig.BUILD_TIMESTAMP}")
        }
        Result.success()
      }
      is NetworkResult.ApplicationError -> Result.retry(defaultBackoff())
      is NetworkResult.NetworkError -> Result.retry(defaultBackoff())
      is NetworkResult.StatusCodeError -> if (result.code < 500) Result.retry(defaultBackoff()) else Result.success()
    }
  }

  override fun onFailure() {
  }

  class Factory : Job.Factory<BuildExpirationConfirmationJob> {
    override fun create(params: Parameters, bytes: ByteArray?): BuildExpirationConfirmationJob {
      return BuildExpirationConfirmationJob(params)
    }
  }
}
