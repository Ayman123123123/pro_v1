package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import org.signal.network.exceptions.PushNetworkException
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.NotPushRegisteredException
import com.red.sovereign.recipients.Recipient
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.multidevice.KeysMessage
import org.whispersystems.signalservice.api.messages.multidevice.REDServiceSyncMessage
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import java.io.IOException

class MultiDeviceKeysUpdateJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY: String = "MultiDeviceKeysUpdateJob"

    private val TAG = Log.tag(MultiDeviceKeysUpdateJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue("MultiDeviceKeysUpdateJob")
      .setMaxInstancesForFactory(2)
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setMaxAttempts(10)
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

    if (REDStore.account.isLinkedDevice) {
      Log.i(TAG, "Not primary device, aborting...")
      return
    }

    val syncMessage = REDServiceSyncMessage.forKeys(
      KeysMessage(
        storageService = REDStore.storageService.storageKey,
        accountEntropyPool = REDStore.account.accountEntropyPool,
        mediaRootBackupKey = REDStore.backup.mediaRootBackupKey
      )
    )

    AppDependencies.signalServiceMessageSender.sendSyncMessage(syncMessage)
  }

  public override fun onShouldRetry(e: Exception): Boolean {
    if (e is ServerRejectedException) return false
    return e is PushNetworkException
  }

  override fun onFailure() {
  }

  class Factory : Job.Factory<MultiDeviceKeysUpdateJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceKeysUpdateJob {
      return MultiDeviceKeysUpdateJob(parameters)
    }
  }
}
