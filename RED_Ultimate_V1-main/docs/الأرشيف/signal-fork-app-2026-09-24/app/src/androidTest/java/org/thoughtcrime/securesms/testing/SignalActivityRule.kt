package com.red.sovereign.testing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.Util
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.REDProtocolAddress
import com.red.sovereign.REDInstrumentationApplicationContext
import com.red.sovereign.crypto.MasterSecretUtil
import com.red.sovereign.crypto.ProfileKeyUtil
import com.red.sovereign.database.IdentityTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.databaseprotos.RestoreDecisionState
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.NewAccount
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.profiles.ProfileName
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.registration.data.AccountRegistrationResult
import com.red.sovereign.registration.data.LocalRegistrationMetadataUtil
import com.red.sovereign.registration.data.RegistrationData
import com.red.sovereign.registration.data.RegistrationRepository
import com.red.sovereign.registration.util.RegistrationUtil
import com.red.sovereign.testing.GroupTestingUtils.asMember
import org.whispersystems.signalservice.api.profiles.REDServiceProfile
import org.whispersystems.signalservice.api.push.REDServiceAddress
import java.util.UUID

/**
 * Test rule to use that sets up the application in a mostly registered state. Enough so that most
 * activities should be launchable directly.
 *
 * To use: `@get:Rule val harness = REDActivityRule()`
 */
class REDActivityRule(private val othersCount: Int = 4, private val createGroup: Boolean = false) : ExternalResource() {

  val application: Application = AppDependencies.application
  private val TEST_E164 = "+15555550101"

  lateinit var context: Context
    private set
  lateinit var self: Recipient
    private set
  lateinit var others: List<RecipientId>
    private set
  lateinit var othersKeys: List<IdentityKeyPair>

  var group: GroupTestingUtils.TestGroupInfo? = null
    private set

  val inMemoryLogger: InMemoryLogger
    get() = (application as REDInstrumentationApplicationContext).inMemoryLogger

  override fun before() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    self = setupSelf()

    val setupOthers = setupOthers()
    others = setupOthers.first
    othersKeys = setupOthers.second

    if (createGroup && others.size >= 2) {
      group = GroupTestingUtils.insertGroup(
        revision = 0,
        self.asMember(),
        others[0].asMember(),
        others[1].asMember()
      )
    }
  }

  private fun setupSelf(): Recipient {
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
        e164 = TEST_E164,
        password = Util.getSecret(18),
        registrationId = RegistrationRepository.getRegistrationId(),
        profileKey = RegistrationRepository.getProfileKey(TEST_E164),
        fcmToken = null,
        pniRegistrationId = RegistrationRepository.getPniRegistrationId(),
        recoveryPassword = "asdfasdfasdfasdf"
      )
      val remoteResult = AccountRegistrationResult(
        uuid = UUID.randomUUID().toString(),
        pni = UUID.randomUUID().toString(),
        storageCapable = false,
        number = TEST_E164,
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
    RegistrationUtil.maybeMarkRegistrationComplete()
    REDDatabase.recipients.setProfileName(Recipient.self().id, ProfileName.fromParts("Tester", "McTesterson"))

    REDStore.settings.isMessageNotificationsEnabled = false
    REDStore.registration.restoreDecisionState = RestoreDecisionState.NewAccount

    return Recipient.self()
  }

  private fun setupOthers(): Pair<List<RecipientId>, List<IdentityKeyPair>> {
    val others = mutableListOf<RecipientId>()
    val othersKeys = mutableListOf<IdentityKeyPair>()

    if (othersCount !in 0 until 1000) {
      throw IllegalArgumentException("$othersCount must be between 0 and 1000")
    }

    for (i in 0 until othersCount) {
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
      othersKeys += otherIdentity
    }

    return others to othersKeys
  }

  inline fun <reified T : Activity> launchActivity(initIntent: Intent.() -> Unit = {}): ActivityScenario<T> {
    return androidx.test.core.app.launchActivity(Intent(context, T::class.java).apply(initIntent))
  }

  fun changeIdentityKey(recipient: Recipient, identityKey: IdentityKey = IdentityKeyPair.generate().publicKey) {
    AppDependencies.protocolStore.aci().saveIdentity(REDProtocolAddress(recipient.requireServiceId().toString(), 0), identityKey)
  }

  fun getIdentity(recipient: Recipient): IdentityKey {
    return AppDependencies.protocolStore.aci().identities().getIdentity(REDProtocolAddress(recipient.requireServiceId().toString(), 0))
  }

  fun setVerified(recipient: Recipient, status: IdentityTable.VerifiedStatus) {
    AppDependencies.protocolStore.aci().identities().setVerified(recipient.id, getIdentity(recipient), IdentityTable.VerifiedStatus.VERIFIED)
  }
}
