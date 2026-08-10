/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobmanager.impl

import android.app.job.JobInfo
import com.red.sovereign.jobmanager.Constraint
import com.red.sovereign.jobmanager.ConstraintObserver
import com.red.sovereign.keyvalue.REDStore

/**
 * A constraint that is met so long as the current user is registered.
 */
object RegisteredConstraint : Constraint {

  const val KEY = "RegisteredConstraint"

  override fun isMet(): Boolean {
    return REDStore.account.isRegistered && REDStore.account.aci != null
  }

  override fun getFactoryKey(): String = KEY

  override fun applyToJobInfo(jobInfoBuilder: JobInfo.Builder) = Unit

  object Observer : ConstraintObserver {
    val listeners: MutableSet<ConstraintObserver.Notifier> = mutableSetOf()

    override fun register(notifier: ConstraintObserver.Notifier) {
      listeners += notifier
    }

    fun notifyListeners() {
      for (listener in listeners) {
        listener.onConstraintMet(KEY)
      }
    }
  }

  class Factory : Constraint.Factory<RegisteredConstraint> {
    override fun create(): RegisteredConstraint {
      return RegisteredConstraint
    }
  }
}
