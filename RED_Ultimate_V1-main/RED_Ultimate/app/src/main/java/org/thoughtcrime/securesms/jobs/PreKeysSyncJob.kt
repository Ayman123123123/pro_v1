package com.red.sovereign.jobs

import androidx.annotation.VisibleForTesting
import org.signal.core.models.ServiceId
import org.signal.core.util.logging.Log
import org.signal.core.util.roundedString
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.REDProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.network.NetworkResult
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import com.red.sovereign.crypto.PreKeyUtil
import com.red.sovereign.crypto.storage.PreKeyMetadataStore
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobs.protos.PreKeysSyncJobData
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.REDNetwork
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.util.isRetryableIOException
import org.whispersystems.signalservice.api.REDServiceAccountDataStore
import org.whispersystems.signalservice.api.account.PreKeyUpload
import org.whispersystems.signalservice.api.push.ServiceIdType
import java.io.IOException
import java.net.ProtocolException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * Regardless of the current state of affairs with respect to prekeys for either ACI or PNI identities, will
 * attempt to make the state valid.
 *
 * It will rotate/create signed prekeys for both ACI and PNI identities, as well as ensure that the user
 * has a sufficient number of one-time EC prekeys available on the service.
 *
 * It will also rotate/create last-resort kyber prekeys for both ACI and PNI identities, as well as ensure
 * that the user has a sufficient number of one-time kyber prekeys available on the service.
 */
class PreKeysSyncJob private constructor(
  parameters: Parameters,
  private val forceRotationRequested: Boolean
) : BaseJob(parameters) {

  companion object {
    const val KEY = "PreKeysSyncJob"

    private val TAG = Log.tag(PreKeysSyncJob::class.java)

    /** The minimum number of one-time prekeys we want to the service to have. If we have less than this, refill. Applies to both EC and kyber prekeys. */
    private const val ONE_TIME_PREKEY_MINIMUM = 10

    /** How often we want to rotate signed prekeys and last-resort kyber prekeys. */
    @JvmField
    val REFRESH_INTERVAL = 2.days.inWholeMilliseconds

    /** If signed prekeys or last-resort kyber keys are older than this, we will require rotation before sending messages. */
    @JvmField
    val MAXIMUM_ALLOWED_SIGNED_PREKEY_AGE = 14.days.inWholeMilliseconds

    /**
     * @param forceRotationRequested If true, this will force the rotation of all keys, provided we haven't already done a forced refresh recently.
     */
    @JvmOverloads
    @JvmStatic
    fun create(forceRotationRequested: Boolean = false): PreKeysSyncJob {
      return PreKeysSyncJob(forceRotationRequested)
    }

    @JvmStatic
    fun enqueue() {
      AppDependencies.jobManager.add(create())
    }

    @JvmStatic
    fun enqueueIfNeeded() {
      if (!REDStore.account.aciPreKeys.isSignedPreKeyRegistered || !REDStore.account.pniPreKeys.isSignedPreKeyRegistered) {
        Log.i(TAG, "Some signed/last-resort prekeys aren't registered yet. Enqueuing a job. ACI: ${REDStore.account.aciPreKeys.isSignedPreKeyRegistered} PNI: ${REDStore.account.pniPreKeys.isSignedPreKeyRegistered}")
        AppDependencies.jobManager.add(PreKeysSyncJob())
      } else if (REDStore.account.aciPreKeys.activeSignedPreKeyId < 0 || REDStore.account.pniPreKeys.activeSignedPreKeyId < 0) {
        Log.i(TAG, "Some signed prekeys aren't active yet. Enqueuing a job. ACI: ${REDStore.account.aciPreKeys.activeSignedPreKeyId >= 0} PNI: ${REDStore.account.pniPreKeys.activeSignedPreKeyId >= 0}")
        AppDependencies.jobManager.add(PreKeysSyncJob())
      } else if (REDStore.account.aciPreKeys.lastResortKyberPreKeyId < 0 || REDStore.account.pniPreKeys.lastResortKyberPreKeyId < 0) {
        Log.i(TAG, "Some last-resort kyber prekeys aren't active yet. Enqueuing a job. ACI: ${REDStore.account.aciPreKeys.lastResortKyberPreKeyId >= 0} PNI: ${REDStore.account.pniPreKeys.lastResortKyberPreKeyId >= 0}")
        AppDependencies.jobManager.add(PreKeysSyncJob())
      } else {
        val timeSinceLastFullRefresh = System.currentTimeMillis() - REDStore.misc.lastFullPrekeyRefreshTime

        if (timeSinceLastFullRefresh >= REFRESH_INTERVAL || timeSinceLastFullRefresh < 0) {
          Log.i(TAG, "Scheduling a prekey refresh. Time since last full refresh: $timeSinceLastFullRefresh ms")
          AppDependencies.jobManager.add(PreKeysSyncJob())
        } else {
          Log.d(TAG, "No prekey job needed. Time since last full refresh: $timeSinceLastFullRefresh ms")
        }
      }
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  constructor(forceRotation: Boolean = false) : this(
    Parameters.Builder()
      .setQueue("PreKeysSyncJob")
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(TimeUnit.DAYS.toMillis(30))
      .build(),
    forceRotation
  )

  override fun getFactoryKey(): String = KEY

  override fun serialize(): ByteArray {
    return PreKeysSyncJobData(forceRotationRequested).encode()
  }

  override fun onRun() {
    if (!REDStore.account.isRegistered || REDStore.account.aci == null || REDStore.account.pni == null) {
      warn(TAG, "Not yet registered")
      return
    }

    val pniRotationOverride = REDStore.misc.forcePniSignedPreKeyRotation
    if (pniRotationOverride) {
      warn(TAG, ServiceIdType.PNI, "Forced PNI prekey rotation pending after PniChangeNumber sync. Bypassing dedup/interval gating for PNI.")
    }

    val forceRotation = if (forceRotationRequested) {
      warn(TAG, "Forced rotation was requested.")
      warn(TAG, ServiceIdType.ACI, "Active Signed EC: ${REDStore.account.aciPreKeys.activeSignedPreKeyId}, Last Resort Kyber: ${REDStore.account.aciPreKeys.lastResortKyberPreKeyId}")
      warn(TAG, ServiceIdType.PNI, "Active Signed EC: ${REDStore.account.pniPreKeys.activeSignedPreKeyId}, Last Resort Kyber: ${REDStore.account.pniPreKeys.lastResortKyberPreKeyId}")

      if (!checkPreKeyConsistency(ServiceIdType.ACI, AppDependencies.protocolStore.aci(), REDStore.account.aciPreKeys)) {
        warn(TAG, ServiceIdType.ACI, "Prekey consistency check failed! Must rotate keys!")
        true
      } else if (!checkPreKeyConsistency(ServiceIdType.PNI, AppDependencies.protocolStore.pni(), REDStore.account.pniPreKeys)) {
        warn(TAG, ServiceIdType.PNI, "Prekey consistency check failed! Must rotate keys! (ACI consistency check must have passed)")
        true
      } else {
        warn(TAG, "Forced rotation was requested, but the consistency checks passed!")
        val timeSinceLastForcedRotation = System.currentTimeMillis() - REDStore.misc.lastForcedPreKeyRefresh
        // We check < 0 in case someone changed their clock and had a bad value set
        timeSinceLastForcedRotation > RemoteConfig.preKeyForceRefreshInterval || timeSinceLastForcedRotation < 0
      }
    } else {
      false
    }

    val forcePniRotation = forceRotation || pniRotationOverride

    if (forcePniRotation) {
      warn(TAG, "Forcing prekey rotation. ACI=$forceRotation PNI=$forcePniRotation")
    } else if (forceRotationRequested) {
      warn(TAG, "Forced prekey rotation was requested, but we already did a forced refresh ${System.currentTimeMillis() - REDStore.misc.lastForcedPreKeyRefresh} ms ago. Ignoring.")
    }

    syncPreKeys(ServiceIdType.ACI, REDStore.account.aci, AppDependencies.protocolStore.aci(), REDStore.account.aciPreKeys, forceRotation)
    syncPreKeys(ServiceIdType.PNI, REDStore.account.pni, AppDependencies.protocolStore.pni(), REDStore.account.pniPreKeys, forcePniRotation)
    REDStore.misc.lastFullPrekeyRefreshTime = System.currentTimeMillis()

    if (forcePniRotation) {
      REDStore.misc.lastForcedPreKeyRefresh = System.currentTimeMillis()
    }

    if (pniRotationOverride) {
      // Cleared only after both syncPreKeys calls completed without throwing; a thrown upload leaves the flag set for the next attempt.
      REDStore.misc.forcePniSignedPreKeyRotation = false
    }
  }

  private fun syncPreKeys(serviceIdType: ServiceIdType, serviceId: ServiceId?, protocolStore: REDServiceAccountDataStore, metadataStore: PreKeyMetadataStore, forceRotation: Boolean) {
    if (serviceId == null) {
      warn(TAG, serviceIdType, "AccountId not set!")
      return
    }

    val availablePreKeyCounts = REDNetwork.keys.getAvailablePreKeyCountsSync(serviceIdType).successOrThrow()

    val signedPreKeyToUpload: SignedPreKeyRecord? = signedPreKeyUploadIfNeeded(serviceIdType, protocolStore, metadataStore, forceRotation)

    val oneTimeEcPreKeysToUpload: List<PreKeyRecord>? = if (forceRotation || availablePreKeyCounts.ecCount < ONE_TIME_PREKEY_MINIMUM) {
      log(serviceIdType, "There are ${availablePreKeyCounts.ecCount} one-time EC prekeys available, which is less than our threshold. Need more. (Forced: $forceRotation)")
      PreKeyUtil.generateAndStoreOneTimeEcPreKeys(protocolStore, metadataStore)
    } else {
      log(serviceIdType, "There are ${availablePreKeyCounts.ecCount} one-time EC prekeys available, which is enough.")
      null
    }

    val lastResortKyberPreKeyToUpload: KyberPreKeyRecord? = lastResortKyberPreKeyUploadIfNeeded(serviceIdType, protocolStore, metadataStore, forceRotation)

    val oneTimeKyberPreKeysToUpload: List<KyberPreKeyRecord>? = if (forceRotation || availablePreKeyCounts.kyberCount < ONE_TIME_PREKEY_MINIMUM) {
      log(serviceIdType, "There are ${availablePreKeyCounts.kyberCount} one-time kyber prekeys available, which is less than our threshold. Need more. (Forced: $forceRotation)")
      PreKeyUtil.generateAndStoreOneTimeKyberPreKeys(protocolStore, metadataStore)
    } else {
      log(serviceIdType, "There are ${availablePreKeyCounts.kyberCount} one-time kyber prekeys available, which is enough.")
      null
    }

    if (signedPreKeyToUpload != null || oneTimeEcPreKeysToUpload != null || lastResortKyberPreKeyToUpload != null || oneTimeKyberPreKeysToUpload != null) {
      log(serviceIdType, "Something to upload. SignedPreKey: ${signedPreKeyToUpload != null}, OneTimeEcPreKeys: ${oneTimeEcPreKeysToUpload != null}, LastResortKyberPreKey: ${lastResortKyberPreKeyToUpload != null}, OneTimeKyberPreKeys: ${oneTimeKyberPreKeysToUpload != null}")
      REDNetwork.keys.setPreKeysSync(
        PreKeyUpload(
          serviceIdType = serviceIdType,
          signedPreKey = signedPreKeyToUpload,
          oneTimeEcPreKeys = oneTimeEcPreKeysToUpload,
          lastResortKyberPreKey = lastResortKyberPreKeyToUpload,
          oneTimeKyberPreKeys = oneTimeKyberPreKeysToUpload
        )
      ).successOrThrow()

      if (signedPreKeyToUpload != null) {
        log(serviceIdType, "Successfully uploaded signed prekey.")
        metadataStore.activeSignedPreKeyId = signedPreKeyToUpload.id
        metadataStore.isSignedPreKeyRegistered = true
        metadataStore.lastSignedPreKeyRotationTime = System.currentTimeMillis()
      }

      if (oneTimeEcPreKeysToUpload != null) {
        log(serviceIdType, "Successfully uploaded one-time EC prekeys.")
      }

      if (lastResortKyberPreKeyToUpload != null) {
        log(serviceIdType, "Successfully uploaded last-resort kyber prekey.")
        metadataStore.lastResortKyberPreKeyId = lastResortKyberPreKeyToUpload.id
        metadataStore.lastResortKyberPreKeyRotationTime = System.currentTimeMillis()
      }

      if (oneTimeKyberPreKeysToUpload != null) {
        log(serviceIdType, "Successfully uploaded one-time kyber prekeys.")
      }
    } else {
      log(serviceIdType, "No prekeys to upload.")
    }

    log(serviceIdType, "Cleaning prekeys...")
    PreKeyUtil.cleanSignedPreKeys(protocolStore, metadataStore)
    PreKeyUtil.cleanLastResortKyberPreKeys(protocolStore, metadataStore)
    PreKeyUtil.cleanOneTimePreKeys(protocolStore)
  }

  private fun signedPreKeyUploadIfNeeded(serviceIdType: ServiceIdType, protocolStore: REDProtocolStore, metadataStore: PreKeyMetadataStore, forceRotation: Boolean): SignedPreKeyRecord? {
    val signedPreKeyRegistered = metadataStore.isSignedPreKeyRegistered && metadataStore.activeSignedPreKeyId >= 0
    val timeSinceLastSignedPreKeyRotation = System.currentTimeMillis() - metadataStore.lastSignedPreKeyRotationTime

    return if (forceRotation || !signedPreKeyRegistered || timeSinceLastSignedPreKeyRotation >= REFRESH_INTERVAL || timeSinceLastSignedPreKeyRotation < 0) {
      log(serviceIdType, "Rotating signed prekey. ForceRotation: $forceRotation, SignedPreKeyRegistered: $signedPreKeyRegistered, TimeSinceLastRotation: $timeSinceLastSignedPreKeyRotation ms (${timeSinceLastSignedPreKeyRotation.milliseconds.toDouble(DurationUnit.DAYS).roundedString(2)} days)")
      PreKeyUtil.generateAndStoreSignedPreKey(protocolStore, metadataStore)
    } else {
      log(serviceIdType, "No need to rotate signed prekey. TimeSinceLastRotation: $timeSinceLastSignedPreKeyRotation ms (${timeSinceLastSignedPreKeyRotation.milliseconds.toDouble(DurationUnit.DAYS).roundedString(2)} days)")
      null
    }
  }

  private fun lastResortKyberPreKeyUploadIfNeeded(serviceIdType: ServiceIdType, protocolStore: REDServiceAccountDataStore, metadataStore: PreKeyMetadataStore, forceRotation: Boolean): KyberPreKeyRecord? {
    val lastResortRegistered = metadataStore.lastResortKyberPreKeyId >= 0
    val timeSinceLastSignedPreKeyRotation = System.currentTimeMillis() - metadataStore.lastResortKyberPreKeyRotationTime

    return if (forceRotation || !lastResortRegistered || timeSinceLastSignedPreKeyRotation >= REFRESH_INTERVAL || timeSinceLastSignedPreKeyRotation < 0) {
      log(serviceIdType, "Rotating last-resort kyber prekey. ForceRotation: $forceRotation, TimeSinceLastRotation: $timeSinceLastSignedPreKeyRotation ms (${timeSinceLastSignedPreKeyRotation.milliseconds.toDouble(DurationUnit.DAYS).roundedString(2)} days)")
      PreKeyUtil.generateAndStoreLastResortKyberPreKey(protocolStore, metadataStore)
    } else {
      log(serviceIdType, "No need to rotate last-resort kyber prekey. TimeSinceLastRotation: $timeSinceLastSignedPreKeyRotation ms (${timeSinceLastSignedPreKeyRotation.milliseconds.toDouble(DurationUnit.DAYS).roundedString(2)} days)")
      null
    }
  }

  @Throws(IOException::class)
  private fun checkPreKeyConsistency(serviceIdType: ServiceIdType, protocolStore: REDServiceAccountDataStore, metadataStore: PreKeyMetadataStore): Boolean {
    val result: NetworkResult<Unit> = try {
      REDNetwork.keys.checkRepeatedUseKeysSync(
        serviceIdType = serviceIdType,
        identityKey = protocolStore.identityKeyPair.publicKey,
        signedPreKeyId = metadataStore.activeSignedPreKeyId,
        signedPreKey = protocolStore.loadSignedPreKey(metadataStore.activeSignedPreKeyId).keyPair.publicKey,
        lastResortKyberKeyId = metadataStore.lastResortKyberPreKeyId,
        lastResortKyberKey = protocolStore.loadKyberPreKey(metadataStore.lastResortKyberPreKeyId).keyPair.publicKey
      )
    } catch (e: InvalidKeyException) {
      Log.w(TAG, "Unable to load keys.", e)
      return false
    } catch (e: InvalidKeyIdException) {
      Log.w(TAG, "Unable to load keys.", e)
      return false
    }

    return when (result) {
      is NetworkResult.Success -> true
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.StatusCodeError -> if (result.code == 409) {
        false
      } else {
        throw NonSuccessfulResponseCodeException(result.code)
      }
    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e.isRetryableIOException()
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to sync prekeys. Enqueuing an account consistency check.")
    AppDependencies.jobManager.add(AccountConsistencyWorkerJob())
  }

  private fun log(serviceIdType: ServiceIdType, message: String) {
    Log.i(TAG, "[$serviceIdType] $message")
  }

  class Factory : Job.Factory<PreKeysSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PreKeysSyncJob {
      return try {
        serializedData?.let {
          val data = PreKeysSyncJobData.ADAPTER.decode(serializedData)
          PreKeysSyncJob(parameters, data.forceRefreshRequested)
        } ?: PreKeysSyncJob(parameters, forceRotationRequested = false)
      } catch (e: IOException) {
        Log.w(TAG, "Error deserializing PreKeysSyncJob", e)
        PreKeysSyncJob(parameters, forceRotationRequested = false)
      } catch (e: ProtocolException) {
        Log.w(TAG, "Error deserializing PreKeysSyncJob", e)
        PreKeysSyncJob(parameters, forceRotationRequested = false)
      }
    }
  }
}
