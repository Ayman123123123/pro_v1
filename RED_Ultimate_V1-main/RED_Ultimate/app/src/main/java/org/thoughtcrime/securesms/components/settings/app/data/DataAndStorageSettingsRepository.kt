package com.red.sovereign.components.settings.app.data

import android.content.Context
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies

class DataAndStorageSettingsRepository {

  private val context: Context = AppDependencies.application

  fun getTotalStorageUse(consumer: (Long) -> Unit) {
    REDExecutors.BOUNDED.execute {
      val breakdown = REDDatabase.media.getStorageBreakdown()

      consumer(listOf(breakdown.audioSize, breakdown.documentSize, breakdown.photoSize, breakdown.videoSize).sum())
    }
  }
}
