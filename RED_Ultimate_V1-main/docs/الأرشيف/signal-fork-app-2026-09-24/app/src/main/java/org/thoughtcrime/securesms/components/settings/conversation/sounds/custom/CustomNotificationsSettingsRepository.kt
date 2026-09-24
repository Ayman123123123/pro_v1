package com.red.sovereign.components.settings.conversation.sounds.custom

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import org.signal.core.util.concurrent.SerialExecutor
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.notifications.NotificationChannels
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId

class CustomNotificationsSettingsRepository(context: Context) {

  private val context = context.applicationContext
  private val executor = SerialExecutor(REDExecutors.BOUNDED)

  fun ensureCustomChannelConsistency(recipientId: RecipientId, onComplete: () -> Unit) {
    executor.execute {
      if (NotificationChannels.supported()) {
        NotificationChannels.getInstance().ensureCustomChannelConsistency()

        val recipient = Recipient.resolved(recipientId)
        val database = REDDatabase.recipients
        if (recipient.notificationChannel != null) {
          val ringtoneUri: Uri? = NotificationChannels.getInstance().getMessageRingtone(recipient)
          database.setMessageRingtone(recipient.id, if (ringtoneUri == Uri.EMPTY) null else ringtoneUri)
          database.setMessageVibrate(recipient.id, RecipientTable.VibrateState.fromBoolean(NotificationChannels.getInstance().getMessageVibrate(recipient)))
        }
      }

      onComplete()
    }
  }

  fun setHasCustomNotifications(recipientId: RecipientId, hasCustomNotifications: Boolean) {
    executor.execute {
      if (hasCustomNotifications) {
        createCustomNotificationChannel(recipientId)
      } else {
        deleteCustomNotificationChannel(recipientId)
      }
    }
  }

  fun setMessageVibrate(recipientId: RecipientId, vibrateState: RecipientTable.VibrateState) {
    executor.execute {
      val recipient: Recipient = Recipient.resolved(recipientId)

      REDDatabase.recipients.setMessageVibrate(recipient.id, vibrateState)
      NotificationChannels.getInstance().updateMessageVibrate(recipient, vibrateState)
    }
  }

  fun setCallingVibrate(recipientId: RecipientId, vibrateState: RecipientTable.VibrateState) {
    executor.execute {
      REDDatabase.recipients.setCallVibrate(recipientId, vibrateState)
    }
  }

  fun setMessageSound(recipientId: RecipientId, sound: Uri?) {
    executor.execute {
      val recipient: Recipient = Recipient.resolved(recipientId)
      val defaultValue = REDStore.settings.messageNotificationSound
      val newValue: Uri? = if (defaultValue == sound) null else sound ?: Uri.EMPTY

      REDDatabase.recipients.setMessageRingtone(recipient.id, newValue)
      NotificationChannels.getInstance().updateMessageRingtone(recipient, newValue)
    }
  }

  fun setCallSound(recipientId: RecipientId, sound: Uri?) {
    executor.execute {
      val defaultValue = REDStore.settings.callRingtone
      val newValue: Uri? = if (defaultValue == sound) null else sound ?: Uri.EMPTY

      REDDatabase.recipients.setCallRingtone(recipientId, newValue)
    }
  }

  @WorkerThread
  private fun createCustomNotificationChannel(recipientId: RecipientId) {
    val recipient: Recipient = Recipient.resolved(recipientId)
    val channelId = NotificationChannels.getInstance().createChannelFor(recipient)
    REDDatabase.recipients.setNotificationChannel(recipient.id, channelId)
  }

  @WorkerThread
  private fun deleteCustomNotificationChannel(recipientId: RecipientId) {
    val recipient: Recipient = Recipient.resolved(recipientId)
    REDDatabase.recipients.setNotificationChannel(recipient.id, null)
    NotificationChannels.getInstance().deleteChannelFor(recipient)
  }
}
