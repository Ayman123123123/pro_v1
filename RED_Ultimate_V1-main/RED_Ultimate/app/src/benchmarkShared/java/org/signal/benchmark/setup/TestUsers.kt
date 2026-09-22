package org.signal.benchmark.setup

import android.app.Application
import android.content.SharedPreferences
import android.preference.PreferenceManager
import kotlinx.coroutines.runBlocking
import okio.ByteString
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.Util
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.REDProtocolAddress
import org.signal.storageservice.storage.protos.groups.AccessControl
import org.signal.storageservice.storage.protos.groups.Member
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember
import org.signal.storageservice.storage.protos.groups.local.DecryptedTimer
import org.signal.storageservice.storage.protos.groups.local.EnabledState
import com.red.sovereign.crypto.MasterSecretUtil
import com.red.sovereign.crypto.PreKeyUtil
import com.red.sovereign.crypto.ProfileKeyUtil
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.databaseprotos.RestoreDecisionState
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.GroupId
import com.red.sovereign.keyvalue.CertificateType
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.Skipped
import com.red.sovereign.net.DeviceTransferBlockingInterceptor
import com.red.sovereign.profiles.ProfileName
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.registration.data.AccountRegistrationResult
import com.red.sovereign.registration.data.LocalRegistrationMetadataUtil
import com.red.sovereign.registration.data.RegistrationData
import com.red.sovereign.registration.data.RegistrationRepository
import com.red.sovereign.registration.util.RegistrationUtil
import com.red.sovereign.util.TextSecurePreferences
import org.whispersystems.signalservice.api.profiles.REDServiceProfile
import org.whispersystems.signalservice.api.push.REDServiceAddress
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

object TestUsers {

  private var generatedOthers: Int = 1

  fun setupSelf(): Recipient {
    val application: Application = AppDependencies.application
    DeviceTransferBlockingInterceptor.getInstance().blockNetwork()

    PreferenceManager.getDefaultSharedPreferences(application).edit().putBoolean("pref_prompted_push_registration", true).commit()
    val masterSecret = MasterSecretUtil.generateMasterSecret(application, MasterSecretUtil.UNENCRYPTED_PASSPHRASE)
    MasterSecretUtil.generateAsymmetricMasterSecret(application, masterSecret)
    val preferences: SharedPreferences = application.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0)
    preferences.edit().putBoolean("passphrase_initialized", true).commit()

    REDStore.account.generateAciIdentityKeyIfNecessary()
    REDStore.account.generatePniIdentityKeyIfNecessary()

    runBlocking {
      val registrationData = RegistrationData(
        code = "123123",
        e164 = Harness.SELF_E164,
        password = Util.getSecret(18),
        registrationId = RegistrationRepository.getRegistrationId(),
        profileKey = RegistrationRepository.getProfileKey(Harness.SELF_E164),
        fcmToken = null,
        pniRegistrationId = RegistrationRepository.getPniRegistrationId(),
        recoveryPassword = "asdfasdfasdfasdf"
      )
      val remoteResult = AccountRegistrationResult(
        uuid = Harness.SELF_ACI.toString(),
        pni = UUID.randomUUID().toString(),
        storageCapable = false,
        number = Harness.SELF_E164,
        masterKey = null,
        pin = null,
        aciPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(REDStore.account.aciIdentityKey, REDStore.account.aciPreKeys),
        pniPreKeyCollection = RegistrationRepository.generateSignedAndLastResortPreKeys(REDStore.account.aciIdentityKey, REDStore.account.pniPreKeys),
        reRegistration = false
      )
      val localRegistrationData = LocalRegistrationMetadataUtil.createLocalRegistrationMetadata(REDStore.account.aciIdentityKey, REDStore.account.pniIdentityKey, registrationData, remoteResult, false)
      RegistrationRepository.registerAccountLocally(application, localRegistrationData)
    }

    REDStore.svr.optOut()
    REDStore.registration.restoreDecisionState = RestoreDecisionState.Skipped
    RegistrationUtil.maybeMarkRegistrationComplete()
    REDDatabase.recipients.setProfileName(Recipient.self().id, ProfileName.fromParts("Tester", "McTesterson"))
    TextSecurePreferences.setPromptedOptimizeDoze(application, true)
    TextSecurePreferences.setRatingEnabled(application, false)

    PreKeyUtil.generateAndStoreSignedPreKey(AppDependencies.protocolStore.aci(), REDStore.account.aciPreKeys)
    PreKeyUtil.generateAndStoreOneTimeEcPreKeys(AppDependencies.protocolStore.aci(), REDStore.account.aciPreKeys)
    PreKeyUtil.generateAndStoreOneTimeKyberPreKeys(AppDependencies.protocolStore.aci(), REDStore.account.aciPreKeys)

    val aliceSenderCertificate = Harness.createCertificateFor(
      uuid = Harness.SELF_ACI.rawUuid,
      e164 = Harness.SELF_E164,
      deviceId = 1,
      identityKey = REDStore.account.aciIdentityKey.publicKey.publicKey,
      expires = System.currentTimeMillis().milliseconds + 30.days
    )

    val aliceSenderCertificate2 = Harness.createCertificateFor(
      uuid = Harness.SELF_ACI.rawUuid,
      e164 = null,
      deviceId = 1,
      identityKey = REDStore.account.aciIdentityKey.publicKey.publicKey,
      expires = System.currentTimeMillis().milliseconds + 30.days
    )

    REDStore.certificate.setUnidentifiedAccessCertificate(CertificateType.ACI_AND_E164, aliceSenderCertificate.serialized)
    REDStore.certificate.setUnidentifiedAccessCertificate(CertificateType.ACI_ONLY, aliceSenderCertificate2.serialized)

    return Recipient.self()
  }

  fun setupTestRecipient(): RecipientId {
    return setupTestRecipients(1).first()
  }

  fun setupTestRecipients(othersCount: Int): List<RecipientId> {
    val others = mutableListOf<RecipientId>()
    synchronized(this) {
      if (generatedOthers + othersCount !in 0 until 1000) {
        throw IllegalArgumentException("$othersCount must be between 0 and 1000")
      }

      for (i in generatedOthers until generatedOthers + othersCount) {
        val aci = ACI.from(UUID.randomUUID())
        val recipientId = RecipientId.from(REDServiceAddress(aci, "+15555551%03d".format(i)))
        REDDatabase.recipients.setProfileName(recipientId, ProfileName.fromParts("Buddy", "#$i"))
        REDDatabase.recipients.setProfileKeyIfAbsent(recipientId, ProfileKeyUtil.createNew())
        REDDatabase.recipients.setCapabilities(recipientId, REDServiceProfile.Capabilities(true, true, true))
        REDDatabase.recipients.setProfileSharing(recipientId, true)
        REDDatabase.recipients.markRegistered(recipientId, aci)
        val otherIdentity = IdentityKeyPair.generate()
        AppDependencies.protocolStore.aci().saveIdentity(REDProtocolAddress(aci.toString(), 1), otherIdentity.publicKey)

        others += recipientId
      }

      generatedOthers += othersCount
    }

    return others
  }

  fun setupTestClients(othersCount: Int): List<RecipientId> {
    val others = mutableListOf<RecipientId>()
    synchronized(this) {
      for (i in 0 until othersCount) {
        val otherClient = Harness.otherClients[i]

        val recipientId = RecipientId.from(REDServiceAddress(otherClient.serviceId, otherClient.e164))
        REDDatabase.recipients.setProfileName(recipientId, ProfileName.fromParts("Buddy", "#$i"))
        REDDatabase.recipients.setProfileKeyIfAbsent(recipientId, otherClient.profileKey)
        REDDatabase.recipients.setCapabilities(recipientId, REDServiceProfile.Capabilities(true, true, true))
        REDDatabase.recipients.setProfileSharing(recipientId, true)
        REDDatabase.recipients.markRegistered(recipientId, otherClient.serviceId)
        AppDependencies.protocolStore.aci().saveIdentity(REDProtocolAddress(otherClient.serviceId.toString(), 1), otherClient.identityKeyPair.publicKey)

        others += recipientId
      }

      generatedOthers += othersCount
    }

    return others
  }

  fun setupGroup(withLabels: Boolean = false): GroupId.V2 {
    val members = setupTestClients(5)
    val self = Recipient.self()

    val labels = listOf("Admin", "Mod", "VIP", "Helper", "Member")
    val fullMembers = buildList {
      add(member(aci = self.requireAci()))
      addAll(
        members.mapIndexed { index, id ->
          if (withLabels) {
            member(aci = Recipient.resolved(id).requireAci(), labelString = labels[index % labels.size])
          } else {
            member(aci = Recipient.resolved(id).requireAci())
          }
        }
      )
    }

    val group = DecryptedGroup(
      title = "Title",
      avatar = "",
      disappearingMessagesTimer = DecryptedTimer(),
      accessControl = AccessControl(),
      revision = 1,
      members = fullMembers,
      pendingMembers = emptyList(),
      requestingMembers = emptyList(),
      inviteLinkPassword = ByteString.EMPTY,
      description = "Description",
      isAnnouncementGroup = EnabledState.DISABLED,
      bannedMembers = emptyList(),
      isPlaceholderGroup = false
    )

    val groupId = REDDatabase.groups.create(
      groupMasterKey = Harness.groupMasterKey,
      groupState = group,
      groupSendEndorsements = null
    )

    REDDatabase.recipients.setProfileSharing(Recipient.externalGroupExact(groupId!!).id, true)

    return groupId
  }

  private fun member(aci: ACI, role: Member.Role = Member.Role.DEFAULT, joinedAt: Int = 0, labelEmoji: String = "", labelString: String = ""): DecryptedMember {
    return DecryptedMember(
      role = role,
      aciBytes = aci.toByteString(),
      joinedAtRevision = joinedAt,
      labelEmoji = labelEmoji,
      labelString = labelString
    )
  }
}
