/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import java.util.concurrent.TimeUnit

/**
 * Cleans database of stale group rings which can occur if the device or application
 * crashes while an incoming ring is happening.
 */
class GroupRingCleanupJob private constructor(parameters: Parameters) : BaseJob(parameters) {
  companion object {
    const val KEY = "GroupRingCleanupJob"

    @JvmStatic
    fun enqueue() {
      AppDependencies.jobManager.add(
        GroupRingCleanupJob(
          Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setLifespan(TimeUnit.HOURS.toMillis(1))
            .setMaxInstancesForFactory(1)
            .setQueue(KEY)
            .build()
        )
      )
    }
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    REDDatabase.calls.getLatestRingingCalls().forEach {
      AppDependencies.signalCallManager.peekGroupCall(it.peer)
    }

    REDDatabase.calls.markRingingCallsAsMissed()
  }

  override fun onShouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<GroupRingCleanupJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): GroupRingCleanupJob {
      return GroupRingCleanupJob(parameters)
    }
  }
}
