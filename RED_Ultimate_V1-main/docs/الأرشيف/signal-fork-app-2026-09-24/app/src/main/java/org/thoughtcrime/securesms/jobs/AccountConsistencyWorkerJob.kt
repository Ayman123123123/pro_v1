package com.red.sovereign.jobs

import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.ProfileUtil
import org.whispersystems.signalservice.api.profiles.REDServiceProfile
import java.io.IOException
import kotlin.time.Duration.Companion.days

/**
 * The worker job for [com.red.sovereign.migrations.AccountConsistencyMigrationJob].
 */
class AccountConsistencyWorkerJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(AccountConsistencyWorkerJob::class.java)

    const val KEY = "AccountConsistencyWorkerJob"

    @JvmStatic
    fun enqueueIfNecessary() {
      if (REDStore.account.isPrimaryDevice && System.currentTimeMillis() - REDStore.misc.lastConsistencyCheckTime > 3.days.inWholeMilliseconds) {
        AppDependencies.jobManager.add(AccountConsistencyWorkerJob())
      }
    }
  }

  constructor() : this(
    Parameters.Builder()
      .setMaxInstancesForFactory(1)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(30.days.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    if (!REDStore.account.hasAciIdentityKey()) {
      Log.i(TAG, "No identity set yet, skipping.")
      return
    }

    if (!REDStore.account.isRegistered || REDStore.account.aci == null) {
      Log.i(TAG, "Not yet registered, skipping.")
      return
    }

    if (REDStore.account.isLinkedDevice) {
      Log.i(TAG, "Linked device, skipping.")
      return
    }

    val aciProfile: REDServiceProfile = ProfileUtil.retrieveProfileSync(context, Recipient.self(), REDServiceProfile.RequestType.PROFILE, false).profile
    val encodedAciPublicKey = Base64.encodeWithPadding(REDStore.account.aciIdentityKey.publicKey.serialize())

    if (aciProfile.identityKey != encodedAciPublicKey) {
      Log.w(TAG, "ACI identity key on profile differed from the one we have locally! Marking ourselves unregistered.")

      markUnregistered()

      REDStore.misc.lastConsistencyCheckTime = System.currentTimeMillis()
      return
    }

    val pniProfile: REDServiceProfile = ProfileUtil.retrieveProfileSync(REDStore.account.pni!!, REDServiceProfile.RequestType.PROFILE).profile
    val encodedPniPublicKey = Base64.encodeWithPadding(REDStore.account.pniIdentityKey.publicKey.serialize())

    if (pniProfile.identityKey != encodedPniPublicKey) {
      Log.w(TAG, "PNI identity key on profile differed from the one we have locally! Marking ourselves unregistered.")

      markUnregistered()

      REDStore.misc.lastConsistencyCheckTime = System.currentTimeMillis()
      return
    }

    Log.i(TAG, "Everything matched.")

    REDStore.misc.lastConsistencyCheckTime = System.currentTimeMillis()
  }

  /** Marks the account unregistered so the user is prompted to re-register. */
  private fun markUnregistered() {
    REDStore.account.setRegistered(false)
    REDStore.registration.clearRegistrationComplete()
    REDStore.registration.hasUploadedProfile = false
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is IOException
  }

  class Factory : Job.Factory<AccountConsistencyWorkerJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AccountConsistencyWorkerJob {
      return AccountConsistencyWorkerJob(parameters)
    }
  }
}
