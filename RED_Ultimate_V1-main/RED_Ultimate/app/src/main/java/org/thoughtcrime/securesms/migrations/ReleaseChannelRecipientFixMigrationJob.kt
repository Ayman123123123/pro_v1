/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.migrations

import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.CreateReleaseChannelJob

/**
 * In a failed backup flow, the release channel recipient can be incorrectly set. Fix it if that's the case.
 */
internal class ReleaseChannelRecipientFixMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {
    const val KEY = "ReleaseChannelRecipientFixMigrationJob"
  }

  constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    AppDependencies.jobManager.add(CreateReleaseChannelJob.create())
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<ReleaseChannelRecipientFixMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ReleaseChannelRecipientFixMigrationJob {
      return ReleaseChannelRecipientFixMigrationJob(parameters)
    }
  }
}
