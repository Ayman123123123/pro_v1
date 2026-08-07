/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.signal.network.exceptions.PushNetworkException
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint
import com.red.sovereign.service.webrtc.links.CallLinkCredentials
import org.whispersystems.signalservice.api.messages.multidevice.REDServiceSyncMessage
import org.whispersystems.signalservice.internal.push.SyncMessage.CallLinkUpdate
import kotlin.time.Duration.Companion.days

/**
 * Sends a sync message to linked devices when a new call link is created locally.
 */
// SIGNAL_INHERITED: TODO [cody] not being created?
class MultiDeviceCallLinkSyncJob private constructor(
  parameters: Parameters,
  private val callLinkUpdate: CallLinkUpdate
) : BaseJob(parameters) {

  constructor(credentials: CallLinkCredentials) : this(
    Parameters.Builder()
      .setQueue("__MULTI_DEVICE_CALL_LINK_UPDATE_JOB__")
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    CallLinkUpdate(
      rootKey = credentials.linkKeyBytes.toByteString(),
      adminPasskey = credentials.adminPassBytes!!.toByteString()
    )
  )

  companion object {
    const val KEY = "MultiDeviceCallLinkSyncJob"

    private val TAG = Log.tag(MultiDeviceCallLinkSyncJob::class.java)
  }

  override fun serialize(): ByteArray {
    return callLinkUpdate.encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    val syncMessage = REDServiceSyncMessage.forCallLinkUpdate(callLinkUpdate)

    try {
      AppDependencies.signalServiceMessageSender.sendSyncMessage(syncMessage)
    } catch (e: Exception) {
      Log.w(TAG, "Unable to send call link update message.", e)
      throw e
    }
  }

  override fun onShouldRetry(exception: Exception): Boolean {
    return when (exception) {
      is PushNetworkException -> true
      else -> false
    }
  }

  class Factory : Job.Factory<MultiDeviceCallLinkSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceCallLinkSyncJob {
      val data = CallLinkUpdate.ADAPTER.decode(serializedData!!)
      return MultiDeviceCallLinkSyncJob(parameters, data)
    }
  }
}
