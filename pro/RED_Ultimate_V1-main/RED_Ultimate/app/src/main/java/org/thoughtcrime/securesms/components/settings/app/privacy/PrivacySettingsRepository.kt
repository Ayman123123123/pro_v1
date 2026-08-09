package com.red.sovereign.components.settings.app.privacy

import android.content.Context
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.MultiDeviceConfigurationUpdateJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.storage.StorageSyncHelper
import com.red.sovereign.util.TextSecurePreferences

class PrivacySettingsRepository {

  private val context: Context = AppDependencies.application

  fun getBlockedCount(consumer: (Int) -> Unit) {
    REDExecutors.BOUNDED.execute {
      val recipientDatabase = REDDatabase.recipients

      consumer(recipientDatabase.getBlocked().size)
    }
  }

  fun syncReadReceiptState() {
    REDExecutors.BOUNDED.execute {
      REDDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      AppDependencies.jobManager.add(
        MultiDeviceConfigurationUpdateJob(
          TextSecurePreferences.isReadReceiptsEnabled(context),
          TextSecurePreferences.isTypingIndicatorsEnabled(context),
          TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
          REDStore.settings.isLinkPreviewsEnabled
        )
      )
    }
  }

  fun syncTypingIndicatorsState() {
    val enabled = TextSecurePreferences.isTypingIndicatorsEnabled(context)

    REDDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
    AppDependencies.jobManager.add(
      MultiDeviceConfigurationUpdateJob(
        TextSecurePreferences.isReadReceiptsEnabled(context),
        enabled,
        TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
        REDStore.settings.isLinkPreviewsEnabled
      )
    )

    if (!enabled) {
      AppDependencies.typingStatusRepository.clear()
    }
  }
}
