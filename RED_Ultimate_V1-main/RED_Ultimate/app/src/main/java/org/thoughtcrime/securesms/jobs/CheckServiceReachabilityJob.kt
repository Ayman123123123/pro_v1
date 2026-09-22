package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import com.red.sovereign.BuildConfig
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.stories.Stories
import com.red.sovereign.util.TextSecurePreferences
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider
import org.whispersystems.signalservice.internal.websocket.OkHttpWebSocketConnection
import java.util.Optional
import java.util.concurrent.TimeUnit

/**
 * Checks to see if a censored user can establish a websocket connection with an uncensored network configuration.
 */
class CheckServiceReachabilityJob private constructor(params: Parameters) : BaseJob(params) {

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(TimeUnit.HOURS.toMillis(12))
      .setMaxAttempts(1)
      .build()
  )

  companion object {
    private val TAG = Log.tag(CheckServiceReachabilityJob::class.java)

    const val KEY = "CheckServiceReachabilityJob"

    @JvmStatic
    fun enqueueIfNecessary() {
      val isCensored = AppDependencies.signalServiceNetworkAccess.isCensored()
      val context = AppDependencies.application
      val timeSinceLastCheck = System.currentTimeMillis() - REDStore.misc.lastCensorshipServiceReachabilityCheckTime
      if (REDStore.account.isRegistered && !TextSecurePreferences.isUnauthorizedReceived(context) && isCensored && timeSinceLastCheck > TimeUnit.DAYS.toMillis(1)) {
        AppDependencies.jobManager.add(CheckServiceReachabilityJob())
      }
    }
  }

  override fun serialize(): ByteArray? {
    return null
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onRun() {
    if (!REDStore.account.isRegistered) {
      Log.w(TAG, "Not registered, skipping.")
      REDStore.misc.lastCensorshipServiceReachabilityCheckTime = System.currentTimeMillis()
      return
    }

    if (TextSecurePreferences.isUnauthorizedReceived(context)) {
      Log.w(TAG, "Unauthorized received, skipping.")
      REDStore.misc.lastCensorshipServiceReachabilityCheckTime = System.currentTimeMillis()
      return
    }

    if (!AppDependencies.signalServiceNetworkAccess.isCensored()) {
      Log.w(TAG, "Not currently censored, skipping.")
      REDStore.misc.lastCensorshipServiceReachabilityCheckTime = System.currentTimeMillis()
      return
    }

    REDStore.misc.lastCensorshipServiceReachabilityCheckTime = System.currentTimeMillis()

    val uncensoredWebsocket = OkHttpWebSocketConnection(
      "uncensored-test",
      AppDependencies.signalServiceNetworkAccess.uncensoredConfiguration,
      Optional.of(
        StaticCredentialsProvider(
          REDStore.account.aci,
          REDStore.account.pni,
          REDStore.account.e164,
          REDStore.account.deviceId,
          REDStore.account.servicePassword
        )
      ),
      BuildConfig.SIGNAL_AGENT,
      null,
      "",
      Stories.isFeatureEnabled()
    )

    try {
      val startTime = System.currentTimeMillis()

      val state: WebSocketConnectionState = uncensoredWebsocket.connect()
        .filter { it == WebSocketConnectionState.CONNECTED || it == WebSocketConnectionState.FAILED }
        .timeout(30, TimeUnit.SECONDS)
        .blockingFirst(WebSocketConnectionState.FAILED)

      if (state == WebSocketConnectionState.CONNECTED) {
        Log.i(TAG, "Established connection in ${System.currentTimeMillis() - startTime} ms! Service is reachable!")
        REDStore.misc.isServiceReachableWithoutCircumvention = true
      } else {
        Log.w(TAG, "Failed to establish a connection in ${System.currentTimeMillis() - startTime} ms.")
        REDStore.misc.isServiceReachableWithoutCircumvention = false
      }
    } catch (exception: Exception) {
      Log.w(TAG, "Failed to connect to the websocket.", exception)
      REDStore.misc.isServiceReachableWithoutCircumvention = false
    } finally {
      uncensoredWebsocket.disconnect()
    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return false
  }

  override fun onFailure() {
  }

  class Factory : Job.Factory<CheckServiceReachabilityJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CheckServiceReachabilityJob {
      return CheckServiceReachabilityJob(parameters)
    }
  }
}
