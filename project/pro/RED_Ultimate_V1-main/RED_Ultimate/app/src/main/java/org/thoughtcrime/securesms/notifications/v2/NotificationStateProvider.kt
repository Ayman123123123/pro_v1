package com.red.sovereign.notifications.v2

import androidx.annotation.WorkerThread
import org.signal.core.util.CursorUtil
import org.signal.core.util.logging.Log
import com.red.sovereign.database.MessageTable
import com.red.sovereign.database.NoSuchMessageException
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.model.ReactionRecord
import com.red.sovereign.notifications.profiles.NotificationProfile
import com.red.sovereign.polls.PollVote
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.isStoryReaction

/**
 * Queries the message databases to determine messages that should be in notifications.
 */
object NotificationStateProvider {

  private val TAG = Log.tag(NotificationStateProvider::class.java)

  @WorkerThread
  fun constructNotificationState(stickyThreads: Map<ConversationId, DefaultMessageNotifier.StickyThread>, notificationProfile: NotificationProfile?): NotificationState {
    val messages: MutableList<NotificationMessage> = mutableListOf()

    REDDatabase.messages.getMessagesForNotificationState(stickyThreads.values).use { unreadMessages ->
      if (unreadMessages.count == 0) {
        return NotificationState.EMPTY
      }

      MessageTable.mmsReaderFor(unreadMessages).use { reader ->
        var record: MessageRecord? = reader.getNext()
        while (record != null) {
          val threadRecipient: Recipient? = REDDatabase.threads.getRecipientForThreadId(record.threadId)
          if (threadRecipient != null) {
            val hasUnreadReactions = CursorUtil.requireInt(unreadMessages, MessageTable.REACTIONS_UNREAD) == 1
            val hasUnreadVotes = CursorUtil.requireInt(unreadMessages, MessageTable.VOTES_UNREAD) == 1
            val conversationId = ConversationId.fromMessageRecord(record)

            val parentRecord = conversationId.groupStoryId?.let {
              try {
                REDDatabase.messages.getMessageRecord(it)
              } catch (e: NoSuchMessageException) {
                null
              }
            }

            val hasSelfRepliedToGroupStory = conversationId.groupStoryId?.let {
              REDDatabase.messages.hasGroupReplyOrReactionInStory(it)
            }

            if (record is MmsMessageRecord) {
              val attachments = REDDatabase.attachments.getAttachmentsForMessage(record.id)
              if (attachments.isNotEmpty()) {
                record = record.withAttachments(attachments)
              }
              val poll = REDDatabase.polls.getPoll(record.id)
              if (poll != null) {
                record = record.withPoll(poll)
              }
            }

            messages += NotificationMessage(
              messageRecord = record,
              reactions = if (hasUnreadReactions) REDDatabase.reactions.getReactions(MessageId(record.id)) else emptyList(),
              pollVotes = if (hasUnreadVotes) REDDatabase.polls.getAllVotes(record.id) else emptyList(),
              threadRecipient = threadRecipient,
              thread = conversationId,
              stickyThread = stickyThreads.containsKey(conversationId),
              isUnreadMessage = CursorUtil.requireInt(unreadMessages, MessageTable.READ) == 0,
              hasUnreadReactions = hasUnreadReactions,
              hasUnreadVotes = hasUnreadVotes,
              lastReactionRead = CursorUtil.requireLong(unreadMessages, MessageTable.REACTIONS_LAST_SEEN),
              lastVoteRead = CursorUtil.requireLong(unreadMessages, MessageTable.VOTES_LAST_SEEN),
              isParentStorySentBySelf = parentRecord?.isOutgoing ?: false,
              hasSelfRepliedToStory = hasSelfRepliedToGroupStory ?: false
            )
          }
          try {
            record = reader.getNext()
          } catch (e: IllegalStateException) {
            // XXX Weird SQLCipher bug that's being investigated
            record = null
            Log.w(TAG, "Failed to read next record!", e)
          }
        }
      }
    }

    val conversations: MutableList<NotificationConversation> = mutableListOf()
    val muteFilteredMessages: MutableList<NotificationState.FilteredMessage> = mutableListOf()
    val profileFilteredMessages: MutableList<NotificationState.FilteredMessage> = mutableListOf()

    messages.groupBy { it.thread }
      .forEach { (thread, threadMessages) ->
        var notificationItems: MutableList<NotificationItem> = mutableListOf()

        for (notification: NotificationMessage in threadMessages) {
          when (notification.includeMessage(notificationProfile)) {
            MessageInclusion.INCLUDE -> notificationItems.add(MessageNotification(notification.threadRecipient, notification.messageRecord, notification.isUnreadMessage))
            MessageInclusion.EXCLUDE -> Unit
            MessageInclusion.MUTE_FILTERED -> muteFilteredMessages += NotificationState.FilteredMessage(notification.messageRecord.id, notification.messageRecord.isMms)
            MessageInclusion.PROFILE_FILTERED -> profileFilteredMessages += NotificationState.FilteredMessage(notification.messageRecord.id, notification.messageRecord.isMms)
          }

          if (notification.hasUnreadReactions) {
            notification.reactions.forEach {
              when (notification.includeReaction(it, notificationProfile)) {
                MessageInclusion.INCLUDE -> notificationItems.add(ReactionNotification(notification.threadRecipient, notification.messageRecord, it))
                MessageInclusion.EXCLUDE -> Unit
                MessageInclusion.MUTE_FILTERED -> muteFilteredMessages += NotificationState.FilteredMessage(notification.messageRecord.id, notification.messageRecord.isMms)
                MessageInclusion.PROFILE_FILTERED -> profileFilteredMessages += NotificationState.FilteredMessage(notification.messageRecord.id, notification.messageRecord.isMms)
              }
            }
          }

          if (notification.hasUnreadVotes) {
            notification.pollVotes.forEach {
              when (notification.shouldIncludeVote(it, notificationProfile)) {
                MessageInclusion.INCLUDE -> notificationItems.add(VoteNotification(notification.threadRecipient, notification.messageRecord, it))
                MessageInclusion.EXCLUDE -> Unit
                MessageInclusion.MUTE_FILTERED -> muteFilteredMessages += NotificationState.FilteredMessage(notification.messageRecord.id, notification.messageRecord.isMms)
                MessageInclusion.PROFILE_FILTERED -> profileFilteredMessages += NotificationState.FilteredMessage(notification.messageRecord.id, notification.messageRecord.isMms)
              }
            }
          }
        }

        notificationItems.sort()
        if (notificationItems.isNotEmpty() && stickyThreads.containsKey(thread) && !notificationItems.last().authorRecipient.isSelf) {
          val indexOfOldestNonSelfMessage: Int = notificationItems.indexOfLast { it.authorRecipient.isSelf } + 1
          notificationItems = notificationItems.slice(indexOfOldestNonSelfMessage..notificationItems.lastIndex).toMutableList()
        }

        if (notificationItems.isNotEmpty()) {
          conversations += NotificationConversation(notificationItems[0].threadRecipient, thread, notificationItems)
        }
      }

    return NotificationState(conversations, muteFilteredMessages, profileFilteredMessages)
  }

  private data class NotificationMessage(
    val messageRecord: MessageRecord,
    val reactions: List<ReactionRecord>,
    val pollVotes: List<PollVote>,
    val threadRecipient: Recipient,
    val thread: ConversationId,
    val stickyThread: Boolean,
    val isUnreadMessage: Boolean,
    val hasUnreadReactions: Boolean,
    val hasUnreadVotes: Boolean,
    val lastReactionRead: Long,
    val lastVoteRead: Long,
    val isParentStorySentBySelf: Boolean,
    val hasSelfRepliedToStory: Boolean
  ) {
    private val isGroupStoryReply: Boolean = thread.groupStoryId != null
    private val isUnreadIncoming: Boolean = isUnreadMessage && !messageRecord.isOutgoing && !isGroupStoryReply
    private val isUnreadNoteToSelf: Boolean = isUnreadMessage && messageRecord.isOutgoing && threadRecipient.isSelf && !isGroupStoryReply
    private val isIncomingMissedCall: Boolean = !messageRecord.isOutgoing && (messageRecord.isMissedAudioCall || messageRecord.isMissedVideoCall)

    private val isNotifiableGroupStoryMessage: Boolean =
      isUnreadMessage &&
        !messageRecord.isOutgoing &&
        isGroupStoryReply &&
        (isParentStorySentBySelf || messageRecord.hasGroupQuoteOrSelfMention() || (hasSelfRepliedToStory && !messageRecord.isStoryReaction()))

    fun includeMessage(notificationProfile: NotificationProfile?): MessageInclusion {
      return if (isUnreadIncoming || isUnreadNoteToSelf || stickyThread || isNotifiableGroupStoryMessage || isIncomingMissedCall) {
        if (threadRecipient.isMuted && !breaksThroughMute()) {
          MessageInclusion.MUTE_FILTERED
        } else if (notificationProfile != null && !notificationProfile.isRecipientAllowed(threadRecipient.id) && !(notificationProfile.allowAllMentions && messageRecord.hasGroupQuoteOrSelfMention())) {
          MessageInclusion.PROFILE_FILTERED
        } else {
          MessageInclusion.INCLUDE
        }
      } else {
        MessageInclusion.EXCLUDE
      }
    }

    private fun breaksThroughMute(): Boolean {
      return when {
        isIncomingMissedCall -> threadRecipient.callNotificationSetting == RecipientTable.NotificationSetting.ALWAYS_NOTIFY
        messageRecord.hasSelfMention() -> threadRecipient.mentionSetting == RecipientTable.NotificationSetting.ALWAYS_NOTIFY
        messageRecord.isQuoteOfSelf() -> threadRecipient.replyNotificationSetting == RecipientTable.NotificationSetting.ALWAYS_NOTIFY
        else -> false
      }
    }

    private fun MessageRecord.isQuoteOfSelf(): Boolean {
      return this is MmsMessageRecord && quote?.author == Recipient.self().id
    }

    fun includeReaction(reaction: ReactionRecord, notificationProfile: NotificationProfile?): MessageInclusion {
      return if (threadRecipient.isMuted) {
        MessageInclusion.MUTE_FILTERED
      } else if (notificationProfile != null && !notificationProfile.isRecipientAllowed(threadRecipient.id)) {
        MessageInclusion.PROFILE_FILTERED
      } else if (reaction.author != Recipient.self().id && messageRecord.isOutgoing && reaction.dateReceived > lastReactionRead) {
        MessageInclusion.INCLUDE
      } else {
        MessageInclusion.EXCLUDE
      }
    }

    fun shouldIncludeVote(vote: PollVote, notificationProfile: NotificationProfile?): MessageInclusion {
      return if (threadRecipient.isMuted) {
        MessageInclusion.MUTE_FILTERED
      } else if (notificationProfile != null && !notificationProfile.isRecipientAllowed(threadRecipient.id)) {
        MessageInclusion.PROFILE_FILTERED
      } else if (vote.voterId != Recipient.self().id && messageRecord.isOutgoing && vote.dateReceived > lastVoteRead) {
        MessageInclusion.INCLUDE
      } else {
        MessageInclusion.EXCLUDE
      }
    }

    private fun MessageRecord.hasGroupQuoteOrSelfMention(): Boolean {
      if (!threadRecipient.isGroup) {
        return false
      }
      return hasSelfMention() || (this is MmsMessageRecord && quote?.author == Recipient.self().id)
    }
  }

  private enum class MessageInclusion {
    INCLUDE,
    EXCLUDE,
    MUTE_FILTERED,
    PROFILE_FILTERED
  }
}
