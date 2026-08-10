package com.red.sovereign.stories.viewer.reply.group

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.contacts.paged.ContactSearchKey
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.identity.IdentityRecordList
import com.red.sovereign.database.model.Mention
import com.red.sovereign.database.model.ParentStoryId
import com.red.sovereign.database.model.databaseprotos.BodyRangeList
import com.red.sovereign.mediasend.v2.UntrustedRecords
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.sms.MessageSender

/**
 * Stateless message sender for Story Group replies and reactions.
 */
object StoryGroupReplySender {

  fun sendReply(context: Context, storyId: Long, body: CharSequence, mentions: List<Mention>, bodyRanges: BodyRangeList?): Completable {
    return sendInternal(
      context = context,
      storyId = storyId,
      body = body,
      mentions = mentions,
      bodyRanges = bodyRanges,
      isReaction = false
    )
  }

  fun sendReaction(context: Context, storyId: Long, emoji: String): Completable {
    return sendInternal(
      context = context,
      storyId = storyId,
      body = emoji,
      mentions = emptyList(),
      bodyRanges = null,
      isReaction = true
    )
  }

  private fun sendInternal(context: Context, storyId: Long, body: CharSequence, mentions: List<Mention>, bodyRanges: BodyRangeList?, isReaction: Boolean): Completable {
    val messageAndRecipient = Single.fromCallable {
      val message = REDDatabase.messages.getMessageRecord(storyId)
      val recipient = REDDatabase.threads.getRecipientForThreadId(message.threadId)!!

      message to recipient
    }

    return messageAndRecipient.flatMapCompletable { (message, recipient) ->
      UntrustedRecords.checkForBadIdentityRecords(setOf(ContactSearchKey.RecipientSearchKey(recipient.id, false)), System.currentTimeMillis() - IdentityRecordList.DEFAULT_UNTRUSTED_WINDOW)
        .andThen(
          Completable.create {
            MessageSender.send(
              context,
              OutgoingMessage(
                threadRecipient = recipient,
                body = body.toString(),
                sentTimeMillis = System.currentTimeMillis(),
                parentStoryId = ParentStoryId.GroupReply(message.id),
                isStoryReaction = isReaction,
                mentions = mentions,
                isSecure = true,
                bodyRanges = bodyRanges
              ),
              message.threadId,
              MessageSender.SendType.SIGNAL,
              null
            ) {
              it.onComplete()
            }
          }
        )
    }.subscribeOn(Schedulers.io())
  }
}
