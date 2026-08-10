/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.benchmark.setup

import org.signal.benchmark.setup.Generator.toEnvelope
import org.signal.core.models.ServiceId
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.REDProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.IdentityKeyStore.IdentityChange
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import com.red.sovereign.crypto.ProfileKeyUtil
import com.red.sovereign.crypto.ReentrantSessionLock
import com.red.sovereign.crypto.SealedSenderAccessUtil
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import org.whispersystems.signalservice.api.REDServiceAccountDataStore
import org.whispersystems.signalservice.api.REDSessionLock
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.crypto.REDServiceCipher
import org.whispersystems.signalservice.api.crypto.REDSessionBuilder
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.REDServiceAddress
import org.whispersystems.signalservice.internal.push.Envelope
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * This is a "fake" client that can start a session with the running app's user, referred to as Alice in this
 * code.
 */
class OtherClient(val serviceId: ServiceId, val e164: String, val identityKeyPair: IdentityKeyPair, val profileKey: ProfileKey) {

  private val serviceAddress = REDServiceAddress(serviceId, e164)
  private val registrationId = KeyHelper.generateRegistrationId(false)
  private val aciStore = BobREDServiceAccountDataStore(registrationId, identityKeyPair)
  private val senderCertificate = Harness.createCertificateFor(serviceId.rawUuid, e164, 1, identityKeyPair.publicKey.publicKey, System.currentTimeMillis().milliseconds + 30.days)
  private val sessionLock = object : REDSessionLock {
    private val lock = ReentrantLock()

    override fun acquire(): REDSessionLock.Lock {
      lock.lock()
      return REDSessionLock.Lock { lock.unlock() }
    }
  }

  /** Inspired by REDServiceMessageSender#getEncryptedMessage */
  fun encrypt(envelopeContent: EnvelopeContent): Envelope {
    return encrypt(envelopeContent, envelopeContent.content.get().dataMessage!!.timestamp!!)
  }

  fun encrypt(envelopeContent: EnvelopeContent, timestamp: Long): Envelope {
    val cipher = REDServiceCipher(serviceAddress, 1, aciStore, sessionLock, null)

    if (!aciStore.containsSession(getAliceProtocolAddress())) {
      val sessionBuilder = REDSessionBuilder(sessionLock, SessionBuilder(aciStore, getAliceProtocolAddress(), REDProtocolAddress(serviceAddress.identifier, 1)))
      sessionBuilder.process(getAlicePreKeyBundle())
    }

    return cipher.encrypt(getAliceProtocolAddress(), getAliceUnidentifiedAccess(), envelopeContent)
      .toEnvelope(timestamp, getAliceServiceId())
  }

  fun decrypt(envelope: Envelope, serverDeliveredTimestamp: Long) {
    val cipher = REDServiceCipher(serviceAddress, 1, aciStore, sessionLock, SealedSenderAccessUtil.getCertificateValidator())
    cipher.decrypt(envelope, serverDeliveredTimestamp)
  }

  /**
   * Completes the RED session handshake by having Alice (the app) encrypt a reply
   * to this client, then decrypting it. This establishes a proper double-ratchet session
   * on both sides.
   *
   * Must be called after this client's initial PreKeyMessage has been processed by Alice.
   */
  fun completeSession() {
    val aliceAddress = REDServiceAddress(Harness.SELF_ACI, Harness.SELF_E164)
    val aliceCipher = REDServiceCipher(aliceAddress, 1, AppDependencies.protocolStore.aci(), ReentrantSessionLock.INSTANCE, null)

    val bobProtocolAddress = REDProtocolAddress(serviceId.toString(), 1)
    val now = System.currentTimeMillis()
    val content = Generator.encryptedTextMessage(now)

    val recipientId = RecipientId.from(REDServiceAddress(serviceId, e164))
    val sealedSenderAccess = SealedSenderAccessUtil.getSealedSenderAccessFor(Recipient.resolved(recipientId))

    val outgoing = aliceCipher.encrypt(bobProtocolAddress, sealedSenderAccess, content)
    val envelope = outgoing.toEnvelope(now, serviceId)

    decrypt(envelope, now)
  }

  fun generateInboundEnvelopes(count: Int): List<Envelope> {
    val envelopes = ArrayList<Envelope>(count)
    var now = System.currentTimeMillis()
    for (i in 0 until count) {
      envelopes += encrypt(Generator.encryptedTextMessage(now))
      now += 3
    }

    return envelopes
  }

  fun generateInboundDeliveryReceipts(messageTimestamps: List<Long>): List<Envelope> {
    return generateInboundReceipts(messageTimestamps, Generator::encryptedDeliveryReceipt)
  }

  fun generateInboundReadReceipts(messageTimestamps: List<Long>): List<Envelope> {
    return generateInboundReceipts(messageTimestamps, Generator::encryptedReadReceipt)
  }

  private fun generateInboundReceipts(messageTimestamps: List<Long>, receiptFactory: (Long, List<Long>) -> EnvelopeContent): List<Envelope> {
    val envelopes = ArrayList<Envelope>(messageTimestamps.size)
    var now = System.currentTimeMillis()
    for (messageTimestamp in messageTimestamps) {
      envelopes += encrypt(receiptFactory(now, listOf(messageTimestamp)), now)
      now += 3
    }
    return envelopes
  }

  fun generateInboundGroupEnvelopes(count: Int, groupMasterKey: GroupMasterKey): List<Envelope> {
    val envelopes = ArrayList<Envelope>(count)
    var now = System.currentTimeMillis()
    for (i in 0 until count) {
      envelopes += encrypt(Generator.encryptedTextMessage(now, groupMasterKey = groupMasterKey))
      now += 3
    }

    return envelopes
  }

  private fun getAliceServiceId(): ServiceId {
    return REDStore.account.requireAci()
  }

  private fun getAlicePreKeyBundle(): PreKeyBundle {
    val aliceSignedPreKeyRecord = REDDatabase.signedPreKeys.getAll(getAliceServiceId()).first()

    val aliceSignedKyberPreKeyRecord = REDDatabase.kyberPreKeys.getAllLastResort(getAliceServiceId()).first().record

    return PreKeyBundle(
      registrationId = REDStore.account.registrationId,
      deviceId = 1,
      preKeyId = PreKeyBundle.NULL_PRE_KEY_ID,
      preKeyPublic = null,
      signedPreKeyId = aliceSignedPreKeyRecord.id,
      signedPreKeyPublic = aliceSignedPreKeyRecord.keyPair.publicKey,
      signedPreKeySignature = aliceSignedPreKeyRecord.signature,
      identityKey = getAlicePublicKey(),
      kyberPreKeyId = aliceSignedKyberPreKeyRecord.id,
      kyberPreKeyPublic = aliceSignedKyberPreKeyRecord.keyPair.publicKey,
      kyberPreKeySignature = aliceSignedKyberPreKeyRecord.signature
    )
  }

  private fun getAliceProtocolAddress(): REDProtocolAddress {
    return REDProtocolAddress(REDStore.account.requireAci().toString(), 1)
  }

  private fun getAlicePublicKey(): IdentityKey {
    return REDStore.account.aciIdentityKey.publicKey
  }

  private fun getAliceProfileKey(): ProfileKey {
    return ProfileKeyUtil.getSelfProfileKey()
  }

  private fun getAliceUnidentifiedAccess(): SealedSenderAccess? {
    val theirProfileKey = getAliceProfileKey()
    val themUnidentifiedAccessKey = UnidentifiedAccess(UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey), senderCertificate.serialized, false)

    return SealedSenderAccess.forIndividual(themUnidentifiedAccessKey)
  }

  private class BobREDServiceAccountDataStore(private val registrationId: Int, private val identityKeyPair: IdentityKeyPair) : REDServiceAccountDataStore {
    private var aliceSessionRecord: SessionRecord? = null

    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair

    override fun getLocalRegistrationId(): Int = registrationId
    override fun isTrustedIdentity(address: REDProtocolAddress?, identityKey: IdentityKey?, direction: IdentityKeyStore.Direction?): Boolean = true
    override fun loadSession(address: REDProtocolAddress?): SessionRecord = aliceSessionRecord ?: SessionRecord()
    override fun saveIdentity(address: REDProtocolAddress?, identityKey: IdentityKey?): IdentityChange = IdentityChange.NEW_OR_UNCHANGED
    override fun storeSession(address: REDProtocolAddress?, record: SessionRecord?) {
      aliceSessionRecord = record
    }
    override fun getSubDeviceSessions(name: String?): List<Int> = emptyList()
    override fun containsSession(address: REDProtocolAddress?): Boolean = aliceSessionRecord != null
    override fun getIdentity(address: REDProtocolAddress?): IdentityKey = REDStore.account.aciIdentityKey.publicKey
    override fun loadPreKey(preKeyId: Int): PreKeyRecord = throw UnsupportedOperationException()
    override fun storePreKey(preKeyId: Int, record: PreKeyRecord?) = throw UnsupportedOperationException()
    override fun containsPreKey(preKeyId: Int): Boolean = throw UnsupportedOperationException()
    override fun removePreKey(preKeyId: Int) = throw UnsupportedOperationException()
    override fun loadExistingSessions(addresses: MutableList<REDProtocolAddress>?): MutableList<SessionRecord> = throw UnsupportedOperationException()
    override fun deleteSession(address: REDProtocolAddress?) = throw UnsupportedOperationException()
    override fun deleteAllSessions(name: String?) = throw UnsupportedOperationException()
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord = throw UnsupportedOperationException()
    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = throw UnsupportedOperationException()
    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord?) = throw UnsupportedOperationException()
    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = throw UnsupportedOperationException()
    override fun removeSignedPreKey(signedPreKeyId: Int) = throw UnsupportedOperationException()
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord = throw UnsupportedOperationException()
    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> = throw UnsupportedOperationException()
    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord?) = throw UnsupportedOperationException()
    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = throw UnsupportedOperationException()
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) = throw UnsupportedOperationException()
    override fun deleteAllStaleOneTimeEcPreKeys(threshold: Long, minCount: Int) = throw UnsupportedOperationException()
    override fun markAllOneTimeEcPreKeysStaleIfNecessary(staleTime: Long) = throw UnsupportedOperationException()
    override fun storeSenderKey(sender: REDProtocolAddress?, distributionId: UUID?, record: SenderKeyRecord?) = throw UnsupportedOperationException()
    override fun loadSenderKey(sender: REDProtocolAddress?, distributionId: UUID?): SenderKeyRecord = throw UnsupportedOperationException()
    override fun archiveSession(address: REDProtocolAddress?) = throw UnsupportedOperationException()
    override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>?): MutableMap<REDProtocolAddress, SessionRecord> = throw UnsupportedOperationException()
    override fun getSenderKeySharedWith(distributionId: DistributionId?): MutableSet<REDProtocolAddress> = throw UnsupportedOperationException()
    override fun markSenderKeySharedWith(distributionId: DistributionId?, addresses: MutableCollection<REDProtocolAddress>?) = throw UnsupportedOperationException()
    override fun clearSenderKeySharedWith(addresses: MutableCollection<REDProtocolAddress>?) = throw UnsupportedOperationException()
    override fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord) = throw UnsupportedOperationException()
    override fun removeKyberPreKey(kyberPreKeyId: Int) = throw UnsupportedOperationException()
    override fun markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime: Long) = throw UnsupportedOperationException()
    override fun deleteAllStaleOneTimeKyberPreKeys(threshold: Long, minCount: Int) = throw UnsupportedOperationException()
    override fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord> = throw UnsupportedOperationException()
    override fun isMultiDevice(): Boolean = throw UnsupportedOperationException()
    override fun setMultiDevice(isMultiDevice: Boolean) = throw UnsupportedOperationException()
  }
}
