/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.changenumber

import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString.Companion.toByteString
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.REDProtocolAddress
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.REDProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import org.signal.libsignal.protocol.util.Medium
import org.signal.network.NetworkResult
import com.red.sovereign.crypto.PreKeyUtil
import com.red.sovereign.database.IdentityTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.databaseprotos.PendingChangeNumberMetadata
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.impl.BackoffUtil
import com.red.sovereign.jobs.RefreshAttributesJob
import com.red.sovereign.keyvalue.CertificateType
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.REDNetwork
import com.red.sovereign.pin.SvrRepository
import com.red.sovereign.pin.SvrWrongPinException
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.registration.viewmodel.SvrAuthCredentialSet
import com.red.sovereign.storage.StorageSyncHelper
import org.whispersystems.signalservice.api.REDServiceAccountManager
import org.whispersystems.signalservice.api.REDServiceMessageSender
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.account.ChangePhoneNumberRequest
import org.whispersystems.signalservice.api.account.PreKeyUpload
import org.whispersystems.signalservice.api.push.ServiceIdType
import org.whispersystems.signalservice.api.push.REDServiceAddress
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity
import org.whispersystems.signalservice.internal.push.KyberPreKeyEntity
import org.whispersystems.signalservice.internal.push.MismatchedDevices
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage
import org.whispersystems.signalservice.internal.push.SyncMessage
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import java.io.IOException
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Repository to perform data operations during change number.
 *
 * @see [com.red.sovereign.registration.data.RegistrationRepository]
 */
class ChangeNumberRepository(
  private val accountManager: REDServiceAccountManager = AppDependencies.signalServiceAccountManager,
  private val messageSender: REDServiceMessageSender = AppDependencies.signalServiceMessageSender
) {

  companion object {
    private val TAG = Log.tag(ChangeNumberRepository::class.java)
  }

  fun whoAmI(): WhoAmIResponse {
    return accountManager.whoAmI
  }

  suspend fun ensureDecryptionsDrained(timeout: Duration = 15.seconds) = withTimeoutOrNull(timeout) {
    suspendCancellableCoroutine {
      val drainedListener = object : Runnable {
        override fun run() {
          AppDependencies
            .incomingMessageObserver
            .removeDecryptionDrainedListener(this)
          Log.d(TAG, "Decryptions drained.")
          it.resume(true)
        }
      }

      it.invokeOnCancellation { cancellationCause ->
        AppDependencies
          .incomingMessageObserver
          .removeDecryptionDrainedListener(drainedListener)
        Log.d(TAG, "Decryptions draining canceled.", cancellationCause)
      }

      AppDependencies
        .incomingMessageObserver
        .addDecryptionDrainedListener(drainedListener)
      Log.d(TAG, "Waiting for decryption drain.")
    }
  }

  @WorkerThread
  fun changeLocalNumber(e164: String, pni: ServiceId.PNI) {
    val metadata: PendingChangeNumberMetadata? = REDStore.misc.pendingChangeNumberMetadata
    if (metadata == null) {
      Log.w(TAG, "No change number metadata, this shouldn't happen")
      throw AssertionError("No change number metadata")
    }

    val pniIdentityKeyPair = IdentityKeyPair(metadata.pniIdentityKeyPair.toByteArray())
    val pniRegistrationId = metadata.pniRegistrationId
    val pniSignedPreKeyId = metadata.pniSignedPreKeyId
    val pniLastResortKyberPreKeyId = metadata.pniLastResortKyberPreKeyId

    // Prekeys were generated and stored during createChangeNumberRequest; reload them so we can pass them through and reuse for the upload below.
    val preResetPniStore = AppDependencies.protocolStore.pni()
    val signedPreKey = preResetPniStore.loadSignedPreKey(pniSignedPreKeyId)
    val lastResortKyberPreKey = preResetPniStore.loadLastResortKyberPreKeys().firstOrNull { it.id == pniLastResortKyberPreKeyId }

    applyLocalNumberChange(
      e164 = e164,
      pni = pni,
      pniIdentityKeyPair = pniIdentityKeyPair,
      pniSignedPreKey = signedPreKey,
      pniLastResortKyberPreKey = lastResortKyberPreKey,
      pniRegistrationId = pniRegistrationId
    )

    AppDependencies.resetNetwork()
    AppDependencies.startNetwork()

    val pniProtocolStore = AppDependencies.protocolStore.pni()
    val pniMetadataStore = REDStore.account.pniPreKeys

    val oneTimeEcPreKeys = PreKeyUtil.generateAndStoreOneTimeEcPreKeys(pniProtocolStore, pniMetadataStore)
    val oneTimeKyberPreKeys = PreKeyUtil.generateAndStoreOneTimeKyberPreKeys(pniProtocolStore, pniMetadataStore)

    Log.i(TAG, "Submitting prekeys with PNI identity key: ${pniIdentityKeyPair.publicKey.fingerprint}")

    retryChangeLocalNumberNetworkOperation {
      REDNetwork.keys.setPreKeysSync(
        PreKeyUpload(
          serviceIdType = ServiceIdType.PNI,
          signedPreKey = signedPreKey,
          oneTimeEcPreKeys = oneTimeEcPreKeys,
          lastResortKyberPreKey = lastResortKyberPreKey,
          oneTimeKyberPreKeys = oneTimeKyberPreKeys
        )
      )
    }.successOrThrow()

    pniMetadataStore.isSignedPreKeyRegistered = true
    pniMetadataStore.lastResortKyberPreKeyId = pniLastResortKyberPreKeyId

    REDStore.misc.hasPniInitializedDevices = true

    AppDependencies.jobManager.add(RefreshAttributesJob())

    rotateCertificates()

    REDStore.misc.unlockChangeNumber()
  }

  /**
   * Applies the local state for a successful number change: self recipient row, account values,
   * PNI protocol store, and identity entry.
   *
   * Does NOT reset the network — callers must do so before any subsequent traffic that needs to
   * use the new PNI. Does NOT make any server requests and does NOT flag prekeys as registered
   * server-side — the caller is responsible for that once it can attest to server state.
   */
  @WorkerThread
  fun applyLocalNumberChange(
    e164: String,
    pni: ServiceId.PNI,
    pniIdentityKeyPair: IdentityKeyPair,
    pniSignedPreKey: SignedPreKeyRecord,
    pniLastResortKyberPreKey: KyberPreKeyRecord?,
    pniRegistrationId: Int
  ) {
    REDDatabase.recipients.updateSelfE164(e164, pni)
    AppDependencies.recipientCache.clear()

    if (e164 != REDStore.account.requireE164()) {
      REDDatabase.recipients.rotateStorageId(Recipient.self().fresh().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    REDStore.account.setNumberAndPniIdentity(e164, pni, pniRegistrationId, pniIdentityKeyPair)
    AppDependencies.resetProtocolStores()

    AppDependencies.groupsV2Authorization.clear()

    val pniProtocolStore = AppDependencies.protocolStore.pni()
    val pniMetadataStore = REDStore.account.pniPreKeys

    PreKeyUtil.storeSignedPreKey(pniProtocolStore, pniMetadataStore, pniSignedPreKey)
    pniMetadataStore.activeSignedPreKeyId = pniSignedPreKey.id

    if (pniLastResortKyberPreKey != null) {
      PreKeyUtil.storeLastResortKyberPreKey(pniProtocolStore, pniMetadataStore, pniLastResortKyberPreKey)
    } else {
      Log.w(TAG, "Last-resort kyber prekey is missing!")
    }

    pniProtocolStore.identities().saveIdentityWithoutSideEffects(
      Recipient.self().id,
      pni,
      pniProtocolStore.identityKeyPair.publicKey,
      IdentityTable.VerifiedStatus.VERIFIED,
      true,
      System.currentTimeMillis(),
      true
    )

    Recipient.self().fresh()
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  @WorkerThread
  private fun rotateCertificates() {
    val certificateTypes = REDStore.phoneNumberPrivacy.allCertificateTypes

    Log.i(TAG, "Rotating these certificates $certificateTypes")

    for (certificateType in certificateTypes) {
      val certificate: ByteArray? = when (certificateType) {
        CertificateType.ACI_AND_E164 -> retryChangeLocalNumberNetworkOperation { REDNetwork.certificate.getSenderCertificate() }.successOrThrow()
        CertificateType.ACI_ONLY -> retryChangeLocalNumberNetworkOperation { REDNetwork.certificate.getSenderCertificateForPhoneNumberPrivacy() }.successOrThrow()
        else -> throw AssertionError()
      }

      Log.i(TAG, "Successfully got $certificateType certificate")

      REDStore.certificate.setUnidentifiedAccessCertificate(certificateType, certificate)
    }
  }

  private fun <T> retryChangeLocalNumberNetworkOperation(operation: () -> NetworkResult<T>): NetworkResult<T> {
    var tries = 0
    var result = operation()
    while (tries < 5) {
      when (result) {
        is NetworkResult.Success,
        is NetworkResult.ApplicationError -> return result
        is NetworkResult.StatusCodeError,
        is NetworkResult.NetworkError -> Log.w(TAG, "Network related error attempting change number operation, try: $tries", result.getCause())
      }

      tries++
      BackoffUtil.exponentialBackoff(tries, 10.seconds.inWholeMilliseconds)
      result = operation()
    }

    return result
  }

  suspend fun changeNumberWithRecoveryPassword(recoveryPassword: String, newE164: String): ChangeNumberResult {
    return changeNumberInternal(recoveryPassword = recoveryPassword, newE164 = newE164)
  }

  suspend fun changeNumberWithoutRegistrationLock(sessionId: String, newE164: String): ChangeNumberResult {
    return changeNumberInternal(sessionId = sessionId, newE164 = newE164)
  }

  suspend fun changeNumberWithRegistrationLock(
    sessionId: String,
    newE164: String,
    pin: String,
    svrAuthCredentials: SvrAuthCredentialSet
  ): ChangeNumberResult {
    val masterKey: MasterKey

    try {
      masterKey = SvrRepository.restoreMasterKeyPreRegistration(svrAuthCredentials, pin)
    } catch (e: SvrWrongPinException) {
      return ChangeNumberResult.SvrWrongPin(e)
    } catch (e: SvrNoDataException) {
      return ChangeNumberResult.SvrNoData(e)
    } catch (e: IOException) {
      return ChangeNumberResult.UnknownError(e)
    }

    val registrationLock = masterKey.deriveRegistrationLock()
    return changeNumberInternal(sessionId = sessionId, registrationLock = registrationLock, newE164 = newE164)
  }

  /**
   * Sends a request to the service to change the phone number associated with this account.
   */
  private suspend fun changeNumberInternal(sessionId: String? = null, recoveryPassword: String? = null, registrationLock: String? = null, newE164: String): ChangeNumberResult {
    check((sessionId != null && recoveryPassword == null) || (sessionId == null && recoveryPassword != null))
    var completed = false
    var attempts = 0
    lateinit var result: NetworkResult<VerifyAccountResponse>

    while (!completed && attempts < 5) {
      Log.i(TAG, "Attempt #$attempts")
      val (request: ChangePhoneNumberRequest, metadata: PendingChangeNumberMetadata) = createChangeNumberRequest(
        sessionId = sessionId,
        recoveryPassword = recoveryPassword,
        newE164 = newE164,
        registrationLock = registrationLock
      )

      REDStore.misc.setPendingChangeNumberMetadata(metadata)
      REDStore.misc.lockChangeNumber()
      withContext(Dispatchers.IO) {
        result = REDNetwork.account.changeNumber(request)
      }

      if (result is NetworkResult.StatusCodeError && result.code == 409) {
        val mismatchedDevices: MismatchedDevices? = result.parseJsonBody()
        if (mismatchedDevices != null) {
          messageSender.handleChangeNumberMismatchDevices(mismatchedDevices)
        }
        attempts++
      } else {
        completed = true
      }
    }

    if (result !is NetworkResult.Success) {
      result = verifyChangeAppliedDespiteError(newE164 = newE164, originalResult = result)
    }

    if (result is NetworkResult.StatusCodeError) {
      REDStore.misc.unlockChangeNumber()
    }

    Log.i(TAG, "Returning change number network result.")
    return ChangeNumberResult.from(
      result.map { accountRegistrationResponse: VerifyAccountResponse ->
        NumberChangeResult(
          uuid = accountRegistrationResponse.uuid,
          pni = accountRegistrationResponse.pni,
          number = accountRegistrationResponse.number
        )
      }
    )
  }

  /**
   * The server can commit a number change but still return an error. Before trusting a non-success result, check
   * with the server and see if it already reports [newE164] as our number. If it does, then the change actually
   * went through, so treat it as a success instead of surfacing the error.
   */
  private suspend fun verifyChangeAppliedDespiteError(newE164: String, originalResult: NetworkResult<VerifyAccountResponse>): NetworkResult<VerifyAccountResponse> {
    return try {
      val whoAmI = withContext(Dispatchers.IO) { whoAmI() }
      if (whoAmI.number == newE164 && whoAmI.pni != null) {
        Log.w(TAG, "Change number request did not succeed, but whoami reports the new number is already active. Treating the change as successful.")
        NetworkResult.Success(
          VerifyAccountResponse().apply {
            uuid = whoAmI.aci
            pni = whoAmI.pni
            number = whoAmI.number
          }
        )
      } else {
        Log.i(TAG, "Change number request did not succeed and whoami does not report the new number; treating as a genuine failure.")
        originalResult
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to query whoami while verifying change number outcome; treating as a genuine failure.", e)
      originalResult
    }
  }

  @WorkerThread
  private fun createChangeNumberRequest(
    sessionId: String? = null,
    recoveryPassword: String? = null,
    newE164: String,
    registrationLock: String? = null
  ): ChangeNumberRequestData {
    val selfIdentifier: String = REDStore.account.requireAci().toString()
    val aciProtocolStore: REDProtocolStore = AppDependencies.protocolStore.aci()

    val pniIdentity: IdentityKeyPair = IdentityKeyPair.generate()
    val deviceMessages = mutableListOf<OutgoingPushMessage>()
    val devicePniSignedPreKeys = mutableMapOf<Int, SignedPreKeyEntity>()
    val devicePniLastResortKyberPreKeys = mutableMapOf<Int, KyberPreKeyEntity>()
    val pniRegistrationIds = mutableMapOf<Int, Int>()
    val primaryDeviceId: Int = REDServiceAddress.DEFAULT_DEVICE_ID

    val devices: List<Int> = listOf(primaryDeviceId) + aciProtocolStore.getSubDeviceSessions(selfIdentifier)

    devices
      .filter { it == primaryDeviceId || aciProtocolStore.containsSession(REDProtocolAddress(selfIdentifier, it)) }
      .forEach { deviceId ->
        // Signed Prekeys
        val signedPreKeyRecord: SignedPreKeyRecord = if (deviceId == primaryDeviceId) {
          PreKeyUtil.generateAndStoreSignedPreKey(AppDependencies.protocolStore.pni(), REDStore.account.pniPreKeys, pniIdentity.privateKey)
        } else {
          PreKeyUtil.generateSignedPreKey(SecureRandom().nextInt(Medium.MAX_VALUE), pniIdentity.privateKey)
        }
        devicePniSignedPreKeys[deviceId] = SignedPreKeyEntity(signedPreKeyRecord.id.toLong(), signedPreKeyRecord.keyPair.publicKey, signedPreKeyRecord.signature)

        // Last-resort kyber prekeys
        val lastResortKyberPreKeyRecord: KyberPreKeyRecord = if (deviceId == primaryDeviceId) {
          PreKeyUtil.generateAndStoreLastResortKyberPreKey(AppDependencies.protocolStore.pni(), REDStore.account.pniPreKeys, pniIdentity.privateKey)
        } else {
          PreKeyUtil.generateLastResortKyberPreKey(SecureRandom().nextInt(Medium.MAX_VALUE), pniIdentity.privateKey)
        }
        devicePniLastResortKyberPreKeys[deviceId] = KyberPreKeyEntity(lastResortKyberPreKeyRecord.id.toLong(), lastResortKyberPreKeyRecord.keyPair.publicKey, lastResortKyberPreKeyRecord.signature)

        // Registration Ids
        var pniRegistrationId = -1

        while (pniRegistrationId < 0 || pniRegistrationIds.values.contains(pniRegistrationId)) {
          pniRegistrationId = KeyHelper.generateRegistrationId(false)
        }
        pniRegistrationIds[deviceId] = pniRegistrationId

        // Device Messages
        if (deviceId != primaryDeviceId) {
          val pniChangeNumber = SyncMessage.PniChangeNumber(
            identityKeyPair = pniIdentity.serialize().toByteString(),
            signedPreKey = signedPreKeyRecord.serialize().toByteString(),
            lastResortKyberPreKey = lastResortKyberPreKeyRecord.serialize().toByteString(),
            registrationId = pniRegistrationId,
            newE164 = newE164
          )

          deviceMessages += messageSender.getEncryptedSyncPniInitializeDeviceMessage(deviceId, pniChangeNumber)
        }
      }

    val request = ChangePhoneNumberRequest(
      sessionId,
      recoveryPassword,
      newE164,
      registrationLock,
      pniIdentity.publicKey,
      deviceMessages,
      devicePniSignedPreKeys.mapKeys { it.key.toString() },
      devicePniLastResortKyberPreKeys.mapKeys { it.key.toString() },
      pniRegistrationIds.mapKeys { it.key.toString() }
    )

    val metadata = PendingChangeNumberMetadata(
      previousPni = REDStore.account.pni!!.toByteString(),
      pniIdentityKeyPair = pniIdentity.serialize().toByteString(),
      pniRegistrationId = pniRegistrationIds[primaryDeviceId]!!,
      pniSignedPreKeyId = devicePniSignedPreKeys[primaryDeviceId]!!.keyId.toInt(),
      pniLastResortKyberPreKeyId = devicePniLastResortKyberPreKeys[primaryDeviceId]!!.keyId.toInt(),
      previousE164 = REDStore.account.requireE164(),
      newE164 = newE164
    )

    return ChangeNumberRequestData(request, metadata)
  }

  private data class ChangeNumberRequestData(val changeNumberRequest: ChangePhoneNumberRequest, val pendingChangeNumberMetadata: PendingChangeNumberMetadata)

  data class NumberChangeResult(
    val uuid: String,
    val pni: String,
    val number: String
  )
}
