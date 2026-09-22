/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import org.signal.core.util.concurrent.safeBlockingGet
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.service.webrtc.links.CallLinkCredentials
import com.red.sovereign.service.webrtc.links.ReadCallLinkResult
import com.red.sovereign.service.webrtc.links.REDCallLinkManager
import org.whispersystems.signalservice.internal.push.SyncMessage.CallLinkUpdate
import java.util.concurrent.TimeUnit

/**
 * Requests the latest call link state from the call service.
 */
class RefreshCallLinkDetailsJob private constructor(
  parameters: Parameters,
  private val callLinkUpdate: CallLinkUpdate
) : BaseJob(parameters) {

  constructor(callLinkUpdate: CallLinkUpdate) : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setQueue("__RefreshCallLinkDetailsJob__")
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    callLinkUpdate
  )

  companion object {
    const val KEY = "RefreshCallLinkDetailsJob"
  }

  override fun serialize(): ByteArray = callLinkUpdate.encode()

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    val manager: REDCallLinkManager = AppDependencies.signalCallManager.callLinkManager
    val credentials = CallLinkCredentials(
      linkKeyBytes = callLinkUpdate.rootKey!!.toByteArray(),
      adminPassBytes = callLinkUpdate.adminPasskey?.toByteArray()
    )

    when (val result = manager.readCallLink(credentials).safeBlockingGet()) {
      is ReadCallLinkResult.Success -> {
        REDDatabase.callLinks.updateCallLinkState(credentials.roomId, result.callLinkState)
      }
      else -> Unit
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<RefreshCallLinkDetailsJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): RefreshCallLinkDetailsJob {
      val callLinkUpdate = CallLinkUpdate.ADAPTER.decode(serializedData!!)
      return RefreshCallLinkDetailsJob(parameters, callLinkUpdate)
    }
  }
}
