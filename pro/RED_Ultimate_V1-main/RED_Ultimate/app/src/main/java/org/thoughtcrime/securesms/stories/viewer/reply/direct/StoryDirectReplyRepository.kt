package com.red.sovereign.stories.viewer.reply.direct

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.model.ParentStoryId
import com.red.sovereign.database.model.databaseprotos.BodyRangeList
import com.red.sovereign.database.withAttachments
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.mms.QuoteModel
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.sms.MessageSender
import java.util.concurrent.TimeUnit

class StoryDirectReplyRepository(context: Context) {

  private val context = context.applicationContext

  fun getStoryPost(storyId: Long): Single<MessageRecord> {
    return Single.fromCallable {
      REDDatabase.messages.getMessageRecord(storyId).withAttachments()
    }.subscribeOn(Schedulers.io())
  }

  fun send(storyId: Long, groupDirectReplyRecipientId: RecipientId?, body: CharSequence, bodyRangeList: BodyRangeList?, isReaction: Boolean): Completable {
    return Completable.create { emitter ->
      val message = REDDatabase.messages.getMessageRecord(storyId).withAttachments() as MmsMessageRecord
      val (recipient, threadId) = if (groupDirectReplyRecipientId == null) {
        message.fromRecipient to message.threadId
      } else {
        val resolved = Recipient.resolved(groupDirectReplyRecipientId)
        resolved to REDDatabase.threads.getOrCreateThreadIdFor(resolved)
      }

      val quoteAuthor: Recipient = message.fromRecipient

      MessageSender.send(
        context,
        OutgoingMessage(
          threadRecipient = recipient,
          body = body.toString(),
          sentTimeMillis = System.currentTimeMillis(),
          expiresIn = TimeUnit.SECONDS.toMillis(recipient.expiresInSeconds.toLong()),
          parentStoryId = ParentStoryId.DirectReply(storyId),
          isStoryReaction = isReaction,
          outgoingQuote = QuoteModel(message.dateSent, quoteAuthor.id, message.body, false, message.slideDeck.asAttachments().firstOrNull(), null, QuoteModel.Type.NORMAL, message.messageRanges),
          bodyRanges = bodyRangeList,
          isSecure = true
        ),
        threadId,
        MessageSender.SendType.SIGNAL,
        null
      ) {
        emitter.onComplete()
      }
    }.subscribeOn(Schedulers.io())
  }
}
