package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import org.signal.network.exceptions.PushNetworkException
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.RecipientRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.NotPushRegisteredException
import com.red.sovereign.recipients.Recipient
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage
import org.whispersystems.signalservice.api.messages.multidevice.REDServiceSyncMessage
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import java.io.IOException
import java.util.concurrent.TimeUnit

class MultiDeviceBlockedUpdateJob private constructor(parameters: Parameters) : BaseJob(parameters) {
  companion object {
    const val KEY: String = "MultiDeviceBlockedUpdateJob"

    private val TAG = Log.tag(MultiDeviceBlockedUpdateJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setQueue("MultiDeviceBlockedUpdateJob")
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  @Throws(IOException::class, UntrustedIdentityException::class)
  public override fun onRun() {
    if (!Recipient.self().isRegistered) {
      throw NotPushRegisteredException()
    }

    if (!REDStore.account.isMultiDevice) {
      Log.i(TAG, "Not multi device, aborting...")
      return
    }

    val blocked: List<RecipientRecord> = REDDatabase.recipients.getBlocked()
    val blockedGroups = blocked.mapNotNull { it.groupId?.decodedId }
    val blockedIndividuals = blocked
      .filter { it.aci != null || it.e164 != null }
      .map { BlockedListMessage.Individual(it.aci, it.e164) }

    AppDependencies.signalServiceMessageSender.sendSyncMessage(
      REDServiceSyncMessage.forBlocked(BlockedListMessage(blockedIndividuals, blockedGroups))
    )
  }

  public override fun onShouldRetry(exception: Exception): Boolean {
    if (exception is ServerRejectedException) return false
    if (exception is PushNetworkException) return true
    return false
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceBlockedUpdateJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceBlockedUpdateJob {
      return MultiDeviceBlockedUpdateJob(parameters)
    }
  }
}
