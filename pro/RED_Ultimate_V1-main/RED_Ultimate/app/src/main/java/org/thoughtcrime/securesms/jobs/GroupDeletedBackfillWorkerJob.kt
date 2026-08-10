/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.jobmanager.Job

/**
 * Clears metadata of existing deleted groups (left and thread deleted). Each group's state is re-validated at run time,
 * so any group that has settled back to active (or regained a thread) is skipped.
 */
class GroupDeletedBackfillWorkerJob private constructor(parameters: Parameters) : Job(parameters) {

  companion object {
    val TAG = Log.tag(GroupDeletedBackfillWorkerJob::class.java)
    const val KEY = "GroupDeletedBackfillWorkerJob"
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue(KEY)
      .setMaxInstancesForFactory(2)
      .setLifespan(Parameters.IMMORTAL)
      .setMaxAttempts(3)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    val groupIdsToClear = REDDatabase.groups.getGroups().use { groups ->
      groups
        .asSequence()
        .filter { !it.isActive && !REDDatabase.threads.hasActiveThread(it.recipientId) }
        .map { it.id }
        .toList()
    }

    groupIdsToClear.forEach { id ->
      REDDatabase.groups.clearGroupIfLeftAndDeleted(id)
    }

    Log.i(TAG, "Cleared ${groupIdsToClear.size} group(s) during backfill.")
    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<GroupDeletedBackfillWorkerJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): GroupDeletedBackfillWorkerJob {
      return GroupDeletedBackfillWorkerJob(parameters)
    }
  }
}
