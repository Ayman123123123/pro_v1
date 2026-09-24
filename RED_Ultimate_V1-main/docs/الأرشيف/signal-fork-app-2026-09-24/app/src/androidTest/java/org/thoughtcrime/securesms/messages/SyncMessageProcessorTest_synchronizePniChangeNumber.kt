/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.models.ServiceId
import org.signal.core.util.UuidUtil
import org.signal.core.util.orNull
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.REDProtocolAddress
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import com.red.sovereign.crypto.PreKeyUtil
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.testing.MessageContentFuzzer
import com.red.sovereign.testing.REDActivityRule
import org.whispersystems.signalservice.api.push.REDServiceAddress
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.UUID

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class SyncMessageProcessorTest_synchronizePniChangeNumber {

  @get:Rule
  val harness = REDActivityRule(createGroup = true)

  private lateinit var messageHelper: MessageHelper

  private val newPniUuid: UUID = UUID.randomUUID()
  private val newPni: ServiceId.PNI = ServiceId.PNI.from(newPniUuid)

  // 16-byte raw UUID — matches the actual wire format the server sends (per proto comment and
  // iOS/Desktop behavior). Do NOT use `newPni.toByteString()` here — that produces libsignal's
  // 17-byte ServiceIdBinary form, which is a different format.
  private val newPniBytes: ByteString = UuidUtil.toByteArray(newPniUuid).toByteString()
  private val newE164 = "+15555550199"
  private val newPniIdentity: IdentityKeyPair = IdentityKeyPair.generate()
  private val newSignedPreKey: SignedPreKeyRecord = PreKeyUtil.generateSignedPreKey(1234, newPniIdentity.privateKey)
  private val newLastResortKyber: KyberPreKeyRecord = PreKeyUtil.generateLastResortKyberPreKey(5678, newPniIdentity.privateKey)
  private val newRegistrationId = 4242

  @Before
  fun setUp() {
    messageHelper = MessageHelper(harness)
    REDStore.account.deviceId = 2
  }

  @After
  fun tearDown() {
    messageHelper.tearDown()
  }

  @Test
  fun appliesAllStateOnHappyPath() {
    sendPniChangeNumber()

    assertThat(REDStore.account.e164).isEqualTo(newE164)
    assertThat(REDStore.account.pni).isEqualTo(newPni)
    assertThat(REDStore.account.pniRegistrationId).isEqualTo(newRegistrationId)
    assertThat(REDStore.account.pniIdentityKey.publicKey.serialize().toByteString())
      .isEqualTo(newPniIdentity.publicKey.serialize().toByteString())
    assertThat(REDStore.account.pniPreKeys.activeSignedPreKeyId).isEqualTo(newSignedPreKey.id)
    assertThat(REDStore.account.pniPreKeys.isSignedPreKeyRegistered).isTrue()
    assertThat(REDStore.account.pniPreKeys.lastResortKyberPreKeyId).isEqualTo(newLastResortKyber.id)
    assertThat(REDStore.misc.forcePniSignedPreKeyRotation).isTrue()

    val self = Recipient.self().fresh()
    assertThat(self.requireE164()).isEqualTo(newE164)
    assertThat(self.pni.orNull()).isEqualTo(newPni)

    val pniProtocolStore = AppDependencies.protocolStore.pni()
    val storedSigned = pniProtocolStore.loadSignedPreKey(newSignedPreKey.id)
    assertThat(storedSigned.serialize().toByteString()).isEqualTo(newSignedPreKey.serialize().toByteString())
    val storedKyber = pniProtocolStore.loadLastResortKyberPreKeys().firstOrNull { it.id == newLastResortKyber.id }
    assertThat(storedKyber).isNotNull()
    assertThat(storedKyber!!.serialize().toByteString()).isEqualTo(newLastResortKyber.serialize().toByteString())

    // The IdentityTable cache is keyed by ServiceId string, not RecipientId — for self, that's
    // separate ACI and PNI rows. We want the PNI row, so look it up by the new PNI directly.
    val selfPniIdentity = pniProtocolStore.getIdentity(REDProtocolAddress(newPni.toString(), REDServiceAddress.DEFAULT_DEVICE_ID))
    assertThat(selfPniIdentity).isNotNull()
    assertThat(selfPniIdentity!!.publicKey.serialize().toByteString())
      .isEqualTo(newPniIdentity.publicKey.serialize().toByteString())
  }

  @Test
  fun appliesStateWhenLastResortKyberAbsent() {
    val original = captureOriginalState()

    sendPniChangeNumber(lastResortKyberPreKey = null)

    assertThat(REDStore.account.e164).isEqualTo(newE164)
    assertThat(REDStore.account.pni).isEqualTo(newPni)
    assertThat(REDStore.account.pniRegistrationId).isEqualTo(newRegistrationId)
    assertThat(REDStore.account.pniPreKeys.activeSignedPreKeyId).isEqualTo(newSignedPreKey.id)
    assertThat(REDStore.account.pniPreKeys.isSignedPreKeyRegistered).isTrue()
    // No kyber was supplied, so kyber metadata should be unchanged.
    assertThat(REDStore.account.pniPreKeys.lastResortKyberPreKeyId).isEqualTo(original.lastResortKyberPreKeyId)
    assertThat(REDStore.misc.forcePniSignedPreKeyRotation).isTrue()
  }

  @Test
  fun bailsWhenPrimaryDevice() {
    REDStore.account.deviceId = REDServiceAddress.DEFAULT_DEVICE_ID
    val original = captureOriginalState()

    sendPniChangeNumber()

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsWhenSourceIsNotPrimaryDevice() {
    val original = captureOriginalState()

    sendPniChangeNumber(sourceDeviceId = 3)

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsWhenEnvelopePniMissing() {
    val original = captureOriginalState()

    sendPniChangeNumber(envelopePniBinary = null)

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsWhenIdentityKeyPairMissing() {
    val original = captureOriginalState()

    sendPniChangeNumber(identityKeyPair = null)

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsWhenSignedPreKeyMissing() {
    val original = captureOriginalState()

    sendPniChangeNumber(signedPreKey = null)

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsWhenRegistrationIdMissing() {
    val original = captureOriginalState()

    sendPniChangeNumber(registrationId = null)

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsWhenRegistrationIdZero() {
    val original = captureOriginalState()

    sendPniChangeNumber(registrationId = 0)

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsWhenNewE164Missing() {
    val original = captureOriginalState()

    sendPniChangeNumber(e164 = null)

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsWhenNewE164Empty() {
    val original = captureOriginalState()

    sendPniChangeNumber(e164 = "")

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsWhenNewE164NotValid() {
    val original = captureOriginalState()

    sendPniChangeNumber(e164 = "not a phone number")

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsOnMalformedIdentityKeyPair() {
    val original = captureOriginalState()

    sendPniChangeNumber(identityKeyPair = malformedBytes())

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsOnMalformedSignedPreKey() {
    val original = captureOriginalState()

    sendPniChangeNumber(signedPreKey = malformedBytes())

    assertOriginalStatePreserved(original)
  }

  @Test
  fun bailsOnMalformedLastResortKyber() {
    val original = captureOriginalState()

    sendPniChangeNumber(lastResortKyberPreKey = malformedBytes())

    assertOriginalStatePreserved(original)
  }

  @Test
  fun skipsRedeliveryWithSameServerTimestamp() {
    val timestamp = messageHelper.nextStartTime()
    sendPniChangeNumber(timestamp = timestamp)
    val afterFirstApply = captureOriginalState()

    val otherIdentity = IdentityKeyPair.generate()
    val otherSignedPreKey = PreKeyUtil.generateSignedPreKey(9999, otherIdentity.privateKey)

    sendPniChangeNumber(
      identityKeyPair = otherIdentity.serialize().toByteString(),
      signedPreKey = otherSignedPreKey.serialize().toByteString(),
      e164 = "+15555550100",
      timestamp = timestamp
    )

    assertOriginalStatePreserved(afterFirstApply)
  }

  @Test
  fun reappliesWhenServerTimestampIsNewer() {
    sendPniChangeNumber()

    val secondPniUuid = UUID.randomUUID()
    val secondPni = ServiceId.PNI.from(secondPniUuid)
    val secondPniBytes = UuidUtil.toByteArray(secondPniUuid).toByteString()
    val secondIdentity = IdentityKeyPair.generate()
    val secondSignedPreKey = PreKeyUtil.generateSignedPreKey(9999, secondIdentity.privateKey)
    val secondE164 = "+15555550100"
    val secondRegistrationId = 7777

    sendPniChangeNumber(
      identityKeyPair = secondIdentity.serialize().toByteString(),
      signedPreKey = secondSignedPreKey.serialize().toByteString(),
      lastResortKyberPreKey = null,
      registrationId = secondRegistrationId,
      e164 = secondE164,
      envelopePniBinary = secondPniBytes,
      timestamp = messageHelper.nextStartTime() + 1000
    )

    assertThat(REDStore.account.e164).isEqualTo(secondE164)
    assertThat(REDStore.account.pni).isEqualTo(secondPni)
    assertThat(REDStore.account.pniRegistrationId).isEqualTo(secondRegistrationId)
    assertThat(REDStore.account.pniIdentityKey.publicKey.serialize().toByteString())
      .isEqualTo(secondIdentity.publicKey.serialize().toByteString())
  }

  @Test
  fun bailsWhenServerTimestampStale() {
    sendPniChangeNumber()
    val afterFirstApply = captureOriginalState()

    val otherPniUuid = UUID.randomUUID()
    val otherPniBytes = UuidUtil.toByteArray(otherPniUuid).toByteString()

    sendPniChangeNumber(
      envelopePniBinary = otherPniBytes,
      e164 = "+15555550100",
      timestamp = messageHelper.nextStartTime() - 100_000L
    )

    assertOriginalStatePreserved(afterFirstApply)
  }

  private fun captureOriginalState(): OriginalState {
    val self = Recipient.self().fresh()
    return OriginalState(
      e164 = REDStore.account.e164,
      pni = REDStore.account.pni,
      pniRegistrationId = REDStore.account.pniRegistrationId,
      isSignedPreKeyRegistered = REDStore.account.pniPreKeys.isSignedPreKeyRegistered,
      activeSignedPreKeyId = REDStore.account.pniPreKeys.activeSignedPreKeyId,
      lastResortKyberPreKeyId = REDStore.account.pniPreKeys.lastResortKyberPreKeyId,
      pniIdentityPublicKey = REDStore.account.pniIdentityKey.publicKey.serialize().toByteString(),
      selfE164 = self.e164.orNull(),
      selfPni = self.pni.orNull(),
      forcePniSignedPreKeyRotation = REDStore.misc.forcePniSignedPreKeyRotation
    )
  }

  private fun assertOriginalStatePreserved(original: OriginalState) {
    assertThat(REDStore.account.e164).isEqualTo(original.e164)
    assertThat(REDStore.account.pni).isEqualTo(original.pni)
    assertThat(REDStore.account.pniRegistrationId).isEqualTo(original.pniRegistrationId)
    assertThat(REDStore.account.pniPreKeys.isSignedPreKeyRegistered).isEqualTo(original.isSignedPreKeyRegistered)
    assertThat(REDStore.account.pniPreKeys.activeSignedPreKeyId).isEqualTo(original.activeSignedPreKeyId)
    assertThat(REDStore.account.pniPreKeys.lastResortKyberPreKeyId).isEqualTo(original.lastResortKyberPreKeyId)
    assertThat(REDStore.account.pniIdentityKey.publicKey.serialize().toByteString())
      .isEqualTo(original.pniIdentityPublicKey)
    assertThat(REDStore.misc.forcePniSignedPreKeyRotation).isEqualTo(original.forcePniSignedPreKeyRotation)
    val self = Recipient.self().fresh()
    assertThat(self.e164.orNull()).isEqualTo(original.selfE164)
    assertThat(self.pni.orNull()).isEqualTo(original.selfPni)
  }

  private data class OriginalState(
    val e164: String?,
    val pni: ServiceId.PNI?,
    val pniRegistrationId: Int,
    val isSignedPreKeyRegistered: Boolean,
    val activeSignedPreKeyId: Int,
    val lastResortKyberPreKeyId: Int,
    val pniIdentityPublicKey: ByteString,
    val selfE164: String?,
    val selfPni: ServiceId.PNI?,
    val forcePniSignedPreKeyRotation: Boolean
  )

  private fun malformedBytes(): ByteString = byteArrayOf(0x00, 0x01, 0x02).toByteString()

  private fun sendPniChangeNumber(
    identityKeyPair: ByteString? = newPniIdentity.serialize().toByteString(),
    signedPreKey: ByteString? = newSignedPreKey.serialize().toByteString(),
    lastResortKyberPreKey: ByteString? = newLastResortKyber.serialize().toByteString(),
    registrationId: Int? = newRegistrationId,
    e164: String? = newE164,
    envelopePniBinary: ByteString? = newPniBytes,
    sourceDeviceId: Int = REDServiceAddress.DEFAULT_DEVICE_ID,
    timestamp: Long = messageHelper.nextStartTime()
  ) {
    val content = Content(
      syncMessage = SyncMessage(
        pniChangeNumber = SyncMessage.PniChangeNumber(
          identityKeyPair = identityKeyPair,
          signedPreKey = signedPreKey,
          lastResortKyberPreKey = lastResortKyberPreKey,
          registrationId = registrationId,
          newE164 = e164
        )
      )
    )

    val envelope = MessageContentFuzzer.envelope(
      timestamp = timestamp,
      updatedPniBinary = envelopePniBinary
    )

    messageHelper.processor.process(
      envelope = envelope,
      content = content,
      metadata = MessageContentFuzzer.envelopeMetadata(harness.self.id, harness.self.id, sourceDeviceId = sourceDeviceId),
      serverDeliveredTimestamp = timestamp + 10
    )
  }
}
