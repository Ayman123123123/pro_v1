package com.red.sovereign.jobs

import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.REDNetwork
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.util.TextSecurePreferences
import org.whispersystems.signalservice.api.websocket.REDWebSocket
import kotlin.time.Duration.Companion.days

/**
 * Job to refresh remote configs. Utilizes eTags so a 304 is returned if content is unchanged since last fetch.
 */
class RemoteConfigRefreshJob private constructor(parameters: Parameters) : Job(parameters) {
  companion object {
    const val KEY: String = "RemoteConfigRefreshJob"
    private val TAG = Log.tag(RemoteConfigRefreshJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue(KEY)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(1.days.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? {
    return null
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    if (!REDStore.account.isRegistered) {
      Log.w(TAG, "Not registered. Skipping.")
      return Result.success()
    }

    if (TextSecurePreferences.isUnauthorizedReceived(context)) {
      Log.i(TAG, "No longer authorized. Ignoring.")
      return Result.success()
    }

    return when (val result = REDNetwork.remoteConfig.getRemoteConfig(REDStore.remoteConfig.eTag)) {
      is NetworkResult.Success -> {
        RemoteConfig.update(result.result.config)
        REDStore.misc.setLastKnownServerTime(result.result.serverEpochTimeMilliseconds, System.currentTimeMillis())
        if (result.result.eTag.isNotNullOrBlank()) {
          REDStore.remoteConfig.eTag = result.result.eTag
        }
        Result.success()
      }

      is NetworkResult.ApplicationError -> Result.failure()
      is NetworkResult.NetworkError -> Result.retry(defaultBackoff())
      is NetworkResult.StatusCodeError ->
        if (result.code == 304) {
          Log.i(TAG, "Remote config has not changed since last pull.")
          REDStore.remoteConfig.lastFetchTime = System.currentTimeMillis()
          REDStore.misc.setLastKnownServerTime(result.header(REDWebSocket.SERVER_DELIVERED_TIMESTAMP_HEADER)?.toLongOrNull() ?: System.currentTimeMillis(), System.currentTimeMillis())
          Result.success()
        } else {
          Result.retry(defaultBackoff())
        }
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<RemoteConfigRefreshJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RemoteConfigRefreshJob {
      return RemoteConfigRefreshJob(parameters)
    }
  }
}
