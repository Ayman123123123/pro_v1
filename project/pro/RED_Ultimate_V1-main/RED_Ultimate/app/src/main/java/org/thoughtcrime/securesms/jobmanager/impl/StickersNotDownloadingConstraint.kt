/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobmanager.impl

import android.app.job.JobInfo
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Constraint
import com.red.sovereign.jobmanager.ConstraintObserver
import com.red.sovereign.jobs.StickerDownloadJob
import com.red.sovereign.jobs.StickerPackDownloadJob

/**
 * When met, no sticker download jobs should be in the job queue/running.
 */
object StickersNotDownloadingConstraint : Constraint {

  const val KEY = "StickersNotDownloadingConstraint"

  private val factoryKeys = setOf(StickerPackDownloadJob.KEY, StickerDownloadJob.KEY)

  override fun isMet(): Boolean {
    return AppDependencies.jobManager.areFactoriesEmpty(factoryKeys)
  }

  override fun getFactoryKey(): String = KEY

  override fun applyToJobInfo(jobInfoBuilder: JobInfo.Builder) = Unit

  object Observer : ConstraintObserver {
    override fun register(notifier: ConstraintObserver.Notifier) {
      AppDependencies.jobManager.addListener({ job -> factoryKeys.contains(job.factoryKey) }) { job, jobState ->
        if (jobState.isComplete) {
          if (isMet) {
            notifier.onConstraintMet(KEY)
          }
        }
      }
    }
  }

  class Factory : Constraint.Factory<StickersNotDownloadingConstraint> {
    override fun create(): StickersNotDownloadingConstraint {
      return StickersNotDownloadingConstraint
    }
  }
}
