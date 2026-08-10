/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.DataRestoreConstraint
import com.red.sovereign.util.RemoteConfig
import java.util.concurrent.TimeUnit

/**
 * Trims expired entries out of the message send log after a delay.
 */
class MessageSendLogCleanupJob private constructor(parameters: Parameters) : Job(parameters) {
  companion object {
    const val KEY = "MessageSendLogCleanupJob"

    @JvmStatic
    fun enqueue() {
      AppDependencies.jobManager.add(
        MessageSendLogCleanupJob(
          Parameters.Builder()
            .addConstraint(DataRestoreConstraint.KEY)
            .setInitialDelay(TimeUnit.MINUTES.toMillis(1))
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

  override fun run(): Result {
    REDDatabase.messageLog.trimOldMessages(System.currentTimeMillis(), RemoteConfig.retryRespondMaxAge)
    return Result.success()
  }

  class Factory : Job.Factory<MessageSendLogCleanupJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MessageSendLogCleanupJob {
      return MessageSendLogCleanupJob(parameters)
    }
  }
}
