package com.red.sovereign.components.settings.conversation.sounds.custom

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.notifications.NotificationChannels
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.util.livedata.Store

class CustomNotificationsSettingsViewModel(
  private val recipientId: RecipientId,
  private val repository: CustomNotificationsSettingsRepository
) : ViewModel() {

  private val store = Store(CustomNotificationsSettingsState())

  val state: LiveData<CustomNotificationsSettingsState> = store.stateLiveData

  init {
    store.update(Recipient.live(recipientId).liveData) { recipient, state ->
      val recipientHasCustomNotifications = NotificationChannels.supported() && recipient.notificationChannel != null
      state.copy(
        recipient = recipient,
        hasCustomNotifications = recipientHasCustomNotifications,
        controlsEnabled = (!NotificationChannels.supported() || recipientHasCustomNotifications) && state.isInitialLoadComplete,
        messageSound = recipient.messageRingtone,
        messageVibrateState = recipient.messageVibrate,
        messageVibrateEnabled = when (recipient.messageVibrate) {
          RecipientTable.VibrateState.DEFAULT -> REDStore.settings.isMessageVibrateEnabled
          RecipientTable.VibrateState.ENABLED -> true
          RecipientTable.VibrateState.DISABLED -> false
        },
        showCallingOptions = recipient.isRegistered,
        callSound = recipient.callRingtone,
        callVibrateState = recipient.callVibrate
      )
    }
  }

  fun setHasCustomNotifications(hasCustomNotifications: Boolean) {
    repository.setHasCustomNotifications(recipientId, hasCustomNotifications)
  }

  fun setMessageVibrate(messageVibrateState: RecipientTable.VibrateState) {
    repository.setMessageVibrate(recipientId, messageVibrateState)
  }

  fun setMessageSound(uri: Uri?) {
    repository.setMessageSound(recipientId, uri)
  }

  fun setCallVibrate(callVibrateState: RecipientTable.VibrateState) {
    repository.setCallingVibrate(recipientId, callVibrateState)
  }

  fun setCallSound(uri: Uri?) {
    repository.setCallSound(recipientId, uri)
  }

  fun channelConsistencyCheck() {
    store.update { it.copy(isInitialLoadComplete = false) }
    repository.ensureCustomChannelConsistency(recipientId) {
      store.update {
        it.copy(
          isInitialLoadComplete = true,
          controlsEnabled = (!NotificationChannels.supported() || it.hasCustomNotifications)
        )
      }
    }
  }

  class Factory(
    private val recipientId: RecipientId,
    private val repository: CustomNotificationsSettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(CustomNotificationsSettingsViewModel(recipientId, repository)))
    }
  }
}
