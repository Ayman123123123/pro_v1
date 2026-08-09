package com.red.sovereign.components.settings.app.privacy.advanced

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.DeviceDeregisteredException
import org.signal.libsignal.net.RequestResult
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.MultiDeviceConfigurationUpdateJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.REDNetwork
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.storage.StorageSyncHelper
import com.red.sovereign.util.TextSecurePreferences
import java.io.IOException
import java.util.concurrent.ExecutionException

private val TAG = Log.tag(AdvancedPrivacySettingsRepository::class.java)

class AdvancedPrivacySettingsRepository(private val context: Context) {

  suspend fun disablePushMessages(): DisablePushMessagesResult = withContext(Dispatchers.IO) {
    val clearTokenError: Throwable? = when (val result = REDNetwork.account.clearFcmToken()) {
      is RequestResult.Success, is RequestResult.NonSuccess -> null
      is RequestResult.RetryableNetworkError -> result.networkError
      is RequestResult.ApplicationError -> result.cause
    }

    if (clearTokenError != null) {
      Log.w(TAG, clearTokenError)
      if (clearTokenError !is DeviceDeregisteredException) {
        return@withContext DisablePushMessagesResult.NETWORK_ERROR
      }
    }

    try {
      if (REDStore.account.fcmEnabled) {
        Tasks.await(FirebaseInstallations.getInstance().delete())
      }
      DisablePushMessagesResult.SUCCESS
    } catch (ioe: IOException) {
      Log.w(TAG, ioe)
      DisablePushMessagesResult.NETWORK_ERROR
    } catch (e: InterruptedException) {
      Log.w(TAG, "Interrupted while deleting", e)
      DisablePushMessagesResult.NETWORK_ERROR
    } catch (e: ExecutionException) {
      Log.w(TAG, "Error deleting", e.cause)
      DisablePushMessagesResult.NETWORK_ERROR
    }
  }

  fun syncShowSealedSenderIconState() {
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

  enum class DisablePushMessagesResult {
    SUCCESS,
    NETWORK_ERROR
  }
}
