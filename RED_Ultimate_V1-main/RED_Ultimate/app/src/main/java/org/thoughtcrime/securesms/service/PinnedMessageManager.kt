package com.red.sovereign.service

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.transport.UndeliverableMessageException
import com.red.sovereign.util.GroupUtil
import com.red.sovereign.util.NetworkUtil
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage

/**
 * Manages waking up and unpinning pinned messages at the correct time
 */
class PinnedMessageManager(
  val application: Application
) : TimedEventManager<PinnedMessageManager.Event>(application, "PinnedMessagesManager") {

  companion object {
    private val TAG = Log.tag(PinnedMessageManager::class.java)
  }

  private val messagesTable = REDDatabase.messages

  init {
    scheduleIfNecessary()
  }

  @WorkerThread
  override fun getNextClosestEvent(): Event? {
    val oldestMessage: MmsMessageRecord? = messagesTable.getOldestExpiringPinnedMessageTimestamp() as? MmsMessageRecord

    if (oldestMessage == null) {
      cancelAlarm(application, PinnedMessagesAlarm::class.java)
      return null
    }

    val delay = (oldestMessage.pinnedUntil - System.currentTimeMillis()).coerceAtLeast(0)
    Log.i(TAG, "The next pinned message needs to be unpinned in $delay ms.")

    return Event(delay, oldestMessage.toRecipient.id, oldestMessage.threadId)
  }

  @WorkerThread
  override fun executeEvent(event: Event) {
    val pinnedMessagesToUnpin = messagesTable.getPinnedMessagesBefore(System.currentTimeMillis())
    for (record in pinnedMessagesToUnpin) {
      messagesTable.unpinMessage(messageId = record.id, threadId = record.threadId)
      val dataMessageBuilder = REDServiceDataMessage.newBuilder()
        .withTimestamp(System.currentTimeMillis())
        .withUnpinnedMessage(
          REDServiceDataMessage.UnpinnedMessage(
            targetAuthor = record.fromRecipient.requireServiceId(),
            targetSentTimestamp = record.dateSent
          )
        )

      val conversationRecipient = REDDatabase.threads.getRecipientForThreadId(record.threadId) ?: continue
      if (conversationRecipient.isGroup) {
        try {
          GroupUtil.setDataMessageGroupContext(application, dataMessageBuilder, conversationRecipient.requireGroupId().requirePush())
        } catch (e: UndeliverableMessageException) {
          Log.w(TAG, "Cannot attach group context for unpin sync of message ${record.id}, likely deleted group. Skipping. Other devices will expire the pin independently.", e)
          continue
        }
      }

      // Best-effort attempt so that messages expire at the same time across devices but if it fails, we can ignore.
      if (NetworkUtil.isConnected(application)) {
        try {
          AppDependencies.signalServiceMessageSender.sendSyncMessage(dataMessageBuilder.build())
        } catch (e: Exception) {
          Log.w(TAG, "Failed to send unpin sync message for message ${record.id}. Other devices will expire the pin independently.", e)
        }
      } else {
        Log.w(TAG, "Failed to send unpin sync message for message ${record.id}. Other devices will expire the pin independently.")
      }
    }
  }

  @WorkerThread
  override fun getDelayForEvent(event: Event): Long = event.delay

  @WorkerThread
  override fun scheduleAlarm(application: Application, event: Event, delay: Long) {
    setAlarm(
      application,
      System.currentTimeMillis() + delay,
      PinnedMessagesAlarm::class.java
    )
  }

  data class Event(val delay: Long, val recipientId: RecipientId, val threadId: Long)

  class PinnedMessagesAlarm : BroadcastReceiver() {

    companion object {
      private val TAG = Log.tag(PinnedMessagesAlarm::class.java)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "onReceive()")
      AppDependencies.pinnedMessageManager.scheduleIfNecessary()
    }
  }
}
