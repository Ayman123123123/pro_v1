package com.red.sovereign.components.settings.app.privacy.expire

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.ThreadTable
import com.red.sovereign.groups.GroupChangeException
import com.red.sovereign.groups.GroupManager
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.sms.MessageSender
import com.red.sovereign.storage.StorageSyncHelper
import java.io.IOException

private val TAG: String = Log.tag(ExpireTimerSettingsRepository::class.java)

/**
 * Provide operations to set expire timer for individuals and groups.
 */
class ExpireTimerSettingsRepository(val context: Context) {

  fun setExpiration(recipientId: RecipientId, newExpirationTime: Int, consumer: (Result<Int>) -> Unit) {
    REDExecutors.BOUNDED.execute {
      val recipient = Recipient.resolved(recipientId)
      if (recipient.groupId.isPresent && recipient.groupId.get().isPush) {
        try {
          GroupManager.updateGroupTimer(context, recipient.groupId.get().requirePush(), newExpirationTime)
          consumer.invoke(Result.success(newExpirationTime))
        } catch (e: GroupChangeException) {
          Log.w(TAG, e)
          consumer.invoke(Result.failure(e))
        } catch (e: IOException) {
          Log.w(TAG, e)
          consumer.invoke(Result.failure(e))
        }
      } else {
        val expireTimerVersion = REDDatabase.recipients.setExpireMessagesAndIncrementVersion(recipientId, newExpirationTime)
        val outgoingMessage = OutgoingMessage.expirationUpdateMessage(Recipient.resolved(recipientId), System.currentTimeMillis(), newExpirationTime * 1000L, expireTimerVersion)
        MessageSender.send(context, outgoingMessage, getThreadId(recipientId), MessageSender.SendType.SIGNAL, null, null)
        consumer.invoke(Result.success(newExpirationTime))
      }
    }
  }

  fun setUniversalExpireTimerSeconds(newExpirationTime: Int, onDone: () -> Unit) {
    REDExecutors.BOUNDED.execute {
      REDStore.settings.universalExpireTimer = newExpirationTime
      REDDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
      onDone.invoke()
    }
  }

  @WorkerThread
  private fun getThreadId(recipientId: RecipientId): Long {
    val threadTable: ThreadTable = REDDatabase.threads
    val recipient: Recipient = Recipient.resolved(recipientId)
    return threadTable.getOrCreateThreadIdFor(recipient)
  }
}
