package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import org.signal.libsignal.net.KeyTransparency.CheckMode
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.usernames.Username
import com.red.sovereign.crypto.ProfileKeyUtil
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.KeyTransparencyStore
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.CoroutineJob
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobs.protos.CheckKeyTransparencyJobData
import com.red.sovereign.keyvalue.AccountValues
import com.red.sovereign.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.REDNetwork
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.util.TextSecurePreferences
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Checks verification of our own identifiers using key transparency.
 */
class CheckKeyTransparencyJob private constructor(
  private val showFailure: Boolean,
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(CheckKeyTransparencyJob::class)
    const val KEY = "CheckKeyTransparencyJob"

    private val TIME_BETWEEN_CHECK = 7.days

    @JvmStatic
    fun enqueueIfNecessary(addDelay: Boolean, force: Boolean = false) {
      if (!canRunJob()) {
        return
      }

      if (REDStore.misc.nextKeyTransparencyTime == 0L) {
        val nextTime = System.currentTimeMillis() + getRandomDelay(maxHours = 168)
        Log.i(TAG, "Initializing next key transparency time to $nextTime")
        REDStore.misc.nextKeyTransparencyTime = nextTime
      }

      if (force || System.currentTimeMillis() > REDStore.misc.nextKeyTransparencyTime) {
        AppDependencies.jobManager.add(
          CheckKeyTransparencyJob(
            showFailure = false,
            parameters = Parameters.Builder()
              .addConstraint(NetworkConstraint.KEY)
              .setInitialDelay(if (addDelay) 5.minutes.inWholeMilliseconds else 0.minutes.inWholeMilliseconds)
              .setGlobalPriority(Parameters.PRIORITY_LOWER)
              .setMaxInstancesForFactory(2)
              .build()
          )
        )
      }
    }

    /**
     * Following a failure, runs another job that will now show an error if it fails again.
     */
    fun enqueueFollowingFailure() {
      if (!canRunJob()) {
        return
      }

      AppDependencies.jobManager.add(
        CheckKeyTransparencyJob(
          showFailure = true,
          parameters = Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setInitialDelay(1.days.inWholeMilliseconds)
            .setGlobalPriority(Parameters.PRIORITY_LOWER)
            .build()
        )
      )
    }

    private fun canRunJob(): Boolean {
      return if (!REDStore.account.isRegistered) {
        Log.i(TAG, "Account not registered. Exiting.")
        false
      } else if (!REDStore.registration.isRegistrationComplete) {
        Log.i(TAG, "Registration is not complete. Exiting.")
        false
      } else if (TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application)) {
        Log.i(TAG, "Account is unauthorized. Exiting.")
        false
      } else if (!REDStore.settings.automaticVerificationEnabled) {
        Log.i(TAG, "Automatic verification disabled. Exiting.")
        false
      } else if (REDStore.account.usernameSyncState != AccountValues.UsernameSyncState.IN_SYNC) {
        Log.i(TAG, "Username is in a bad state. Exiting.")
        false
      } else if (!Recipient.self().hasAci || !Recipient.self().hasE164) {
        Log.i(TAG, "Missing an ACI or E164. Exiting.")
        false
      } else {
        true
      }
    }

    /**
     * Generates a random delay between 0 - maxHours in milliseconds
     */
    private fun getRandomDelay(maxHours: Int): Long {
      val delay = Random.nextInt(0, maxHours)
      return delay.hours.inWholeMilliseconds
    }
  }

  override suspend fun doRun(): Result {
    if (!canRunJob()) {
      return Result.failure()
    }

    REDStore.misc.nextKeyTransparencyTime = System.currentTimeMillis() + TIME_BETWEEN_CHECK.inWholeMilliseconds + getRandomDelay(maxHours = 8)

    val recipient = REDDatabase.recipients.getRecord(Recipient.self().id)

    val result = REDNetwork.keyTransparency.check(
      checkMode = CheckMode.Self(isE164Discoverable = REDStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.DISCOVERABLE),
      aci = recipient.aci!!.libREDAci,
      aciIdentityKey = REDStore.account.aciIdentityKey.publicKey,
      e164 = recipient.e164!!,
      unidentifiedAccessKey = ProfileKeyUtil.profileKeyOrNull(recipient.profileKey).let { UnidentifiedAccess.deriveAccessKeyFrom(it) },
      usernameHash = REDStore.account.username?.let { Username(it).hash }.takeIf { Recipient.self().usernameSyncMessagesCapability.isSupported },
      keyTransparencyStore = KeyTransparencyStore
    )

    Log.i(TAG, "Key transparency complete, result: $result. Included username in check: ${Recipient.self().usernameSyncMessagesCapability.isSupported}, discoverability: ${REDStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode}, next check time: ${REDStore.misc.nextKeyTransparencyTime}")
    return when (result) {
      is RequestResult.Success -> {
        REDStore.misc.hasKeyTransparencyFailure = false
        REDStore.misc.hasSeenKeyTransparencyFailure = false
        Result.success()
      }

      is RequestResult.NonSuccess -> {
        if (!showFailure && !REDStore.misc.hasKeyTransparencyFailure) {
          Log.w(TAG, "Verification failure. Enqueuing this job again to run again a day.")
          AppDependencies.jobManager.add(StorageSyncJob.forRemoteChange())
          AppDependencies.jobManager.add(RefreshAttributesJob())
          enqueueFollowingFailure()
        } else {
          Log.w(TAG, "Second verification failure. Showing failure sheet.")
          markFailure()
        }
        Result.failure()
      }
      is RequestResult.RetryableNetworkError -> {
        if (result.retryAfter != null) {
          Result.retry(result.retryAfter!!.toMillis())
        } else {
          Result.retry(defaultBackoff())
        }
      }
      is RequestResult.ApplicationError -> {
        if (result.cause is IllegalArgumentException) {
          Log.w(TAG, "KT store was corrupted. Restarting and then retrying.")
          REDStore.account.distinguishedHead = null
          REDDatabase.recipients.clearSelfKeyTransparencyData()
          Result.retry(defaultBackoff())
        } else {
          Log.w(TAG, "Unknown application failure. Showing failure sheet.")
          markFailure()
          Result.failure()
        }
      }
    }
  }

  /**
   * Flags a failure in key transparency. For internal users, always force it to be shown.
   * For others, it will only show once and only be cleared on the next successful verification.
   */
  private fun markFailure() {
    if (REDStore.account.isRegistered && !TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application)) {
      REDStore.misc.hasKeyTransparencyFailure = true
      if (RemoteConfig.internalUser) {
        REDStore.misc.hasSeenKeyTransparencyFailure = false
      }
    }
  }

  override fun serialize(): ByteArray {
    return CheckKeyTransparencyJobData(showFailure).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  class Factory : Job.Factory<CheckKeyTransparencyJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CheckKeyTransparencyJob {
      val jobData = CheckKeyTransparencyJobData.ADAPTER.decode(serializedData!!)
      return CheckKeyTransparencyJob(jobData.showFailure, parameters)
    }
  }
}
