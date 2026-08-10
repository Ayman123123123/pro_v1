package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.pin.SvrRepository
import com.red.sovereign.util.TextSecurePreferences
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Refresh KBS authentication credentials for talking to KBS during re-registration.
 */
class RefreshSvrCredentialsJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY = "RefreshKbsCredentialsJob"

    private val TAG = Log.tag(RefreshSvrCredentialsJob::class.java)
    private val FREQUENCY: Duration = 15.days

    @JvmStatic
    fun enqueueIfNecessary() {
      if (REDStore.svr.hasPin() && REDStore.account.isRegistered && !TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application)) {
        val lastTimestamp = REDStore.svr.lastRefreshAuthTimestamp
        if (lastTimestamp + FREQUENCY.inWholeMilliseconds < System.currentTimeMillis() || lastTimestamp > System.currentTimeMillis()) {
          AppDependencies.jobManager.add(RefreshSvrCredentialsJob())
        } else {
          Log.d(TAG, "Do not need to refresh credentials. Last refresh: $lastTimestamp")
        }
      }
    }
  }

  private constructor() : this(
    parameters = Parameters.Builder()
      .setQueue("RefreshKbsCredentials")
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForQueue(2)
      .setMaxAttempts(3)
      .setLifespan(1.days.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onRun() {
    if (!REDStore.account.isRegistered) {
      Log.w(TAG, "Not registered! Skipping.")
      return
    }

    if (TextSecurePreferences.isUnauthorizedReceived(context)) {
      Log.i(TAG, "No longer authorized. Ignoring.")
      return
    }

    SvrRepository.refreshAndStoreAuthorization()
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is IOException && e !is NonSuccessfulResponseCodeException
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<RefreshSvrCredentialsJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RefreshSvrCredentialsJob {
      return RefreshSvrCredentialsJob(parameters)
    }
  }
}
