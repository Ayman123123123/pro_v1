/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.testutil

import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.signal.core.models.ServiceId.ACI
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.storageservice.storage.protos.groups.Member
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember
import com.red.sovereign.database.GroupReceiptTable
import com.red.sovereign.database.MessageType
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.GroupId
import com.red.sovereign.keyvalue.ReleaseChannelValues
import com.red.sovereign.keyvalue.SettingsValues
import com.red.sovereign.mms.IncomingMessage
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.profiles.ProfileName
import com.red.sovereign.recipients.LiveRecipientCache
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.util.RemoteConfig
import org.whispersystems.signalservice.api.profiles.REDServiceProfile
import java.util.UUID
import kotlin.random.Random

/**
 * Test rule for unit tests that need to insert recipients and groups into the database. Handles
 * the various database, dependencies, and store setup/teardown. Self is created by default.
 */
class RecipientTestRule : TestRule {

  val signalStore = MockREDStoreRule(relaxed = setOf(SettingsValues::class, ReleaseChannelValues::class))
  val signalDatabase = REDDatabaseRule()
  val appDependencies = MockAppDependenciesRule()

  val selfAci: ACI = ACI.from(UUID.randomUUID())
  val selfE164: String = "+15555555555"

  lateinit var self: RecipientId
    private set

  private val extras = object : ExternalResource() {
    override fun before() {
      mockkStatic(AppDependencies::class)
      every { AppDependencies.recipientCache } returns LiveRecipientCache(
        ApplicationProvider.getApplicationContext(),
        Runnable::run
      )

      mockkObject(RemoteConfig)
      every { RemoteConfig.collapseEvents } returns true

      every { signalStore.account.aci } returns selfAci
      every { signalStore.account.requireAci() } returns selfAci
      every { signalStore.account.e164 } returns selfE164
      every { signalStore.account.requireE164() } returns selfE164
      every { signalStore.account.isRegistered } returns true
      every { signalStore.account.deviceId } returns 1
      every { signalStore.account.isMultiDevice } returns false
      every { signalStore.account.isLinkedDevice } returns false
      every { signalStore.account.isPrimaryDevice } returns true

      every { signalStore.registration.isRegistrationComplete } returns true

      self = insertRecipient(selfAci, ProfileName.fromParts("Tester", "McTesterson"), e164 = selfE164)
    }

    override fun after() {
      unmockkObject(RemoteConfig)
      unmockkStatic(AppDependencies::class)
    }
  }

  override fun apply(base: Statement, description: Description): Statement {
    return RuleChain
      .outerRule(signalStore)
      .around(signalDatabase)
      .around(appDependencies)
      .around(extras)
      .apply(base, description)
  }

  fun createRecipient(profileName: ProfileName, profileSharing: Boolean = true): RecipientId {
    return insertRecipient(ACI.from(UUID.randomUUID()), profileName, profileSharing)
  }

  /**
   * Convenience overload: splits [profileName] on the first space into given/family name parts.
   */
  fun createRecipient(profileName: String, profileSharing: Boolean = true): RecipientId {
    val name = profileName.split(" ", limit = 2).let { ProfileName.fromParts(it[0], it.getOrNull(1)) }
    return createRecipient(name, profileSharing)
  }

  /**
   * Create a thread for [id] (1:1) if necessary and insert a simple outgoing MMS-style message.
   * Mirrors the androidTest `MmsHelper.insert(recipient, threadId)` shape.
   */
  fun insertOutgoingMessage(id: RecipientId, body: String = "body", sentTimeMillis: Long = System.currentTimeMillis()): Long {
    val recipient = Recipient.resolved(id)
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(recipient)
    val message = OutgoingMessage(
      recipient = recipient,
      body = body,
      timestamp = sentTimeMillis,
      isSecure = true
    )
    return insertOutgoingMessage(message, threadId)
  }

  fun insertOutgoingMessage(message: OutgoingMessage, threadId: Long): Long {
    return REDDatabase.messages.insertMessageOutbox(
      message = message,
      threadId = threadId,
      forceSms = false,
      defaultReceiptStatus = GroupReceiptTable.STATUS_UNKNOWN,
      insertListener = null
    ).messageId
  }

  /**
   * Create a thread for [id] if necessary and insert a single normal incoming message.
   */
  fun insertIncomingMessage(id: RecipientId) {
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(id, false)
    REDDatabase.messages.insertMessageInbox(
      IncomingMessage(
        type = MessageType.NORMAL,
        from = id,
        groupId = null,
        body = "hi",
        sentTimeMillis = 100L,
        receivedTimeMillis = 200L,
        serverTimeMillis = 100L,
        isUnidentified = true
      ),
      threadId
    )
  }

  /**
   * Insert a v2 group containing [self] plus the given members, all as administrators.
   */
  fun createGroup(vararg members: RecipientId): TestGroupInfo {
    val masterKey = GroupMasterKey(Random.nextBytes(GroupMasterKey.SIZE))
    val decryptedGroup = DecryptedGroup.Builder()
      .members(
        listOf(asAdminMember(selfAci)) +
          members.map { asAdminMember(Recipient.resolved(it).requireAci()) }
      )
      .revision(0)
      .title("Test group")
      .build()

    val groupId: GroupId.V2 = REDDatabase.groups.create(masterKey, decryptedGroup, null)!!
    val groupRecipientId = REDDatabase.recipients.getOrInsertFromGroupId(groupId)
    REDDatabase.recipients.setProfileSharing(groupRecipientId, true)
    return TestGroupInfo(groupId, masterKey, groupRecipientId)
  }

  fun setProfileName(id: RecipientId, name: ProfileName) {
    REDDatabase.recipients.setProfileName(id, name)
    Recipient.live(id).refresh()
  }

  private fun insertRecipient(aci: ACI, profileName: ProfileName, profileSharing: Boolean = true, e164: String? = null): RecipientId {
    val id = if (e164 != null) {
      REDDatabase.recipients.getAndPossiblyMerge(aci, e164)
    } else {
      REDDatabase.recipients.getOrInsertFromServiceId(aci)
    }
    REDDatabase.recipients.setProfileName(id, profileName)
    REDDatabase.recipients.setProfileKeyIfAbsent(id, ProfileKey(Random.nextBytes(32)))
    REDDatabase.recipients.setCapabilities(id, REDServiceProfile.Capabilities(true, true, true))
    REDDatabase.recipients.setProfileSharing(id, profileSharing)
    REDDatabase.recipients.markRegistered(id, aci)
    return id
  }

  private fun asAdminMember(aci: ACI): DecryptedMember {
    return DecryptedMember(aciBytes = aci.toByteString(), role = Member.Role.ADMINISTRATOR)
  }

  data class TestGroupInfo(
    val groupId: GroupId.V2,
    val masterKey: GroupMasterKey,
    val recipientId: RecipientId
  )
}
