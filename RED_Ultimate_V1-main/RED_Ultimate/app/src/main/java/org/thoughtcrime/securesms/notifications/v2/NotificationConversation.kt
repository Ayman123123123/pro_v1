package com.red.sovereign.notifications.v2

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.SpannableStringBuilder
import androidx.core.app.TaskStackBuilder
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.Util
import org.signal.core.util.logging.Log
import com.red.sovereign.R
import com.red.sovereign.avatar.fallback.FallbackAvatar
import com.red.sovereign.avatar.fallback.FallbackAvatarDrawable
import com.red.sovereign.contacts.TurnOffContactJoinedNotificationsActivity
import com.red.sovereign.conversation.ConversationIntents
import com.red.sovereign.conversation.colors.AvatarColor
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.notifications.DeleteNotificationReceiver
import com.red.sovereign.notifications.MarkReadReceiver
import com.red.sovereign.notifications.NotificationChannels
import com.red.sovereign.notifications.NotificationIds
import com.red.sovereign.notifications.RemoteReplyReceiver
import com.red.sovereign.notifications.ReplyMethod
import com.red.sovereign.preferences.widgets.NotificationPrivacyPreference
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.service.KeyCachingService
import com.red.sovereign.stories.StoryViewerArgs
import com.red.sovereign.stories.viewer.StoryViewerActivity

/**
 * Encapsulate all the notifications for a given conversation (thread) and the top
 * level information about said conversation.
 */
data class NotificationConversation(
  val recipient: Recipient,
  val thread: ConversationId,
  val notificationItems: List<NotificationItem>
) {
  val mostRecentNotification: NotificationItem = notificationItems.last()
  val notificationId: Int = NotificationIds.getNotificationIdForThread(thread)
  val sortKey: Long = Long.MAX_VALUE - mostRecentNotification.timestamp
  val messageCount: Int = notificationItems.size
  val isGroup: Boolean = recipient.isGroup
  val isOnlyContactJoinedEvent: Boolean = messageCount == 1 && mostRecentNotification.isJoined

  fun getContentTitle(context: Context): CharSequence {
    return if (REDStore.settings.messageNotificationsPrivacy.isDisplayContact) {
      getDisplayName(context)
    } else {
      context.getString(R.string.SingleRecipientNotificationBuilder_signal)
    }
  }

  fun getContactLargeIcon(context: Context): Drawable? {
    return if (REDStore.settings.messageNotificationsPrivacy.isDisplayContact) {
      if (recipient.isSelf) {
        FallbackAvatarDrawable(context, FallbackAvatar.Resource.NoteToSelf(recipient.avatarColor)).circleCrop()
      } else {
        recipient.getContactDrawable(context)
      }
    } else {
      FallbackAvatarDrawable(context, FallbackAvatar.forTextOrDefault("Unknown", AvatarColor.UNKNOWN)).circleCrop()
    }
  }

  fun getSlideBigPictureUri(context: Context): Uri? {
    return if (notificationItems.size == 1 && REDStore.settings.messageNotificationsPrivacy.isDisplayMessage && !KeyCachingService.isLocked(context)) {
      mostRecentNotification.getBigPictureUri()
    } else {
      null
    }
  }

  fun getContentText(context: Context): CharSequence? {
    val privacy: NotificationPrivacyPreference = REDStore.settings.messageNotificationsPrivacy
    val stringBuilder = SpannableStringBuilder()

    if (privacy.isDisplayContact && recipient.isGroup) {
      stringBuilder.append(Util.getBoldedString(mostRecentNotification.authorRecipient.getDisplayName(context) + ": "))
    }

    return if (privacy.isDisplayMessage) {
      stringBuilder.append(mostRecentNotification.getPrimaryText(context))
    } else {
      stringBuilder.append(context.getString(R.string.SingleRecipientNotificationBuilder_new_message))
    }
  }

  fun getConversationTitle(context: Context): CharSequence? {
    if (REDStore.settings.messageNotificationsPrivacy.isDisplayContact) {
      return if (isGroup) getDisplayName(context) else null
    }
    return context.getString(R.string.SingleRecipientNotificationBuilder_signal)
  }

  fun getWhen(): Long {
    return mostRecentNotification.timestamp
  }

  fun hasNewNotifications(): Boolean {
    return notificationItems.any { it.isNewNotification }
  }

  fun getChannelId(): String {
    return if (isOnlyContactJoinedEvent) {
      NotificationChannels.getInstance().JOIN_EVENTS
    } else {
      recipient.notificationChannel ?: NotificationChannels.getInstance().messagesChannel
    }
  }

  fun hasSameContent(other: NotificationConversation?): Boolean {
    if (other == null) {
      return false
    }

    return messageCount == other.messageCount && notificationItems.zip(other.notificationItems).all { (item, otherItem) -> item.hasSameContent(otherItem) }
  }

  fun getPendingIntent(context: Context): PendingIntent? {
    val intent: Intent = if (thread.groupStoryId != null) {
      StoryViewerActivity.createIntent(
        context,
        StoryViewerArgs(
          recipientId = recipient.id,
          storyId = thread.groupStoryId,
          isInHiddenStoryMode = recipient.shouldHideStory,
          isFromNotification = true,
          groupReplyStartPosition = mostRecentNotification.getStartingPosition(context)
        )
      )
    } else {
      ConversationIntents.createBuilderSync(context, recipient.id, thread.threadId)
        .withStartingPosition(mostRecentNotification.getStartingPosition(context))
        .build()
    }.makeUniqueToPreventMerging()

    return try {
      TaskStackBuilder.create(context)
        .addNextIntentWithParentStack(intent)
        .getPendingIntent(0, PendingIntentFlags.updateCurrent())
    } catch (e: NullPointerException) {
      Log.w(NotificationFactory.TAG, "Vivo device quirk sometimes throws NPE", e)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      NotificationPendingIntentHelper.getActivity(context, 0, intent, PendingIntentFlags.updateCurrent())
    } catch (e: SecurityException) {
      Log.w(NotificationFactory.TAG, "TaskStackBuilder too many pending intents device quirk: ${e.message}")
      null
    }
  }

  fun getDeleteIntent(context: Context): PendingIntent? {
    val ids = LongArray(notificationItems.size)
    val mms = BooleanArray(ids.size)
    notificationItems.forEachIndexed { index, notificationItem ->
      ids[index] = notificationItem.id
      mms[index] = notificationItem.isMms
    }

    val intent = Intent(context, DeleteNotificationReceiver::class.java)
      .setAction(DeleteNotificationReceiver.DELETE_NOTIFICATION_ACTION)
      .putExtra(DeleteNotificationReceiver.EXTRA_IDS, ids)
      .putExtra(DeleteNotificationReceiver.EXTRA_MMS, mms)
      .putParcelableArrayListExtra(DeleteNotificationReceiver.EXTRA_THREADS, arrayListOf(thread))
      .makeUniqueToPreventMerging()

    return NotificationPendingIntentHelper.getBroadcast(context, 0, intent, PendingIntentFlags.updateCurrent())
  }

  fun getMarkAsReadIntent(context: Context): PendingIntent? {
    val intent = Intent(context, MarkReadReceiver::class.java)
      .setAction(MarkReadReceiver.CLEAR_ACTION)
      .putParcelableArrayListExtra(MarkReadReceiver.THREADS_EXTRA, arrayListOf(mostRecentNotification.thread))
      .putExtra(MarkReadReceiver.NOTIFICATION_ID_EXTRA, notificationId)
      .makeUniqueToPreventMerging()

    return NotificationPendingIntentHelper.getBroadcast(context, (thread.threadId * 2).toInt(), intent, PendingIntentFlags.updateCurrent())
  }

  fun getQuickReplyIntent(context: Context): PendingIntent? {
    val intent: Intent = ConversationIntents.createPopUpBuilder(context, recipient.id, mostRecentNotification.thread.threadId, recipient.wallpaper != null)
      .build()
      .makeUniqueToPreventMerging()

    return NotificationPendingIntentHelper.getActivity(context, (thread.threadId * 2).toInt() + 1, intent, PendingIntentFlags.updateCurrent())
  }

  fun getRemoteReplyIntent(context: Context, replyMethod: ReplyMethod): PendingIntent? {
    val intent = Intent(context, RemoteReplyReceiver::class.java)
      .setAction(RemoteReplyReceiver.REPLY_ACTION)
      .putExtra(RemoteReplyReceiver.RECIPIENT_EXTRA, recipient.id)
      .putExtra(RemoteReplyReceiver.REPLY_METHOD, replyMethod)
      .putExtra(RemoteReplyReceiver.EARLIEST_TIMESTAMP, notificationItems.first().timestamp)
      .putExtra(RemoteReplyReceiver.GROUP_STORY_ID_EXTRA, notificationItems.first().thread.groupStoryId ?: Long.MIN_VALUE)
      .makeUniqueToPreventMerging()

    return NotificationPendingIntentHelper.getBroadcast(context, (thread.threadId * 2).toInt() + 1, intent, PendingIntentFlags.updateCurrent())
  }

  fun getTurnOffJoinedNotificationsIntent(context: Context): PendingIntent? {
    return NotificationPendingIntentHelper.getActivity(
      context,
      0,
      TurnOffContactJoinedNotificationsActivity.newIntent(context, thread.threadId),
      PendingIntentFlags.updateCurrent()
    )
  }

  private fun getDisplayName(context: Context): String {
    return if (thread.groupStoryId != null) {
      context.getString(R.string.SingleRecipientNotificationBuilder__s_dot_story, recipient.getDisplayName(context))
    } else if (recipient.isSelf) {
      context.getString(R.string.note_to_self)
    } else {
      recipient.getDisplayName(context)
    }
  }

  override fun toString(): String {
    return "NotificationConversation(thread=$thread, notificationItems=$notificationItems, messageCount=$messageCount, hasNewNotifications=${hasNewNotifications()})"
  }
}
