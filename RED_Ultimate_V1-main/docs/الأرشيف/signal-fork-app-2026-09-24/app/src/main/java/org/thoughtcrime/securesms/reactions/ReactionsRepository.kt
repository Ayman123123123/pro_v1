package com.red.sovereign.reactions

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.components.emoji.EmojiUtil
import com.red.sovereign.database.DatabaseObserver
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.database.model.ReactionRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.sms.MessageSender

class ReactionsRepository {

  fun getReactions(messageId: MessageId): Observable<List<ReactionDetails>> {
    return Observable.create { emitter: ObservableEmitter<List<ReactionDetails>> ->
      val databaseObserver: DatabaseObserver = AppDependencies.databaseObserver

      val messageObserver = DatabaseObserver.MessageObserver { reactionMessageId ->
        if (reactionMessageId == messageId) {
          emitter.onNext(fetchReactionDetails(reactionMessageId))
        }
      }

      databaseObserver.registerMessageUpdateObserver(messageObserver)

      emitter.setCancellable {
        databaseObserver.unregisterObserver(messageObserver)
      }

      emitter.onNext(fetchReactionDetails(messageId))
    }.subscribeOn(Schedulers.io())
  }

  private fun fetchReactionDetails(messageId: MessageId): List<ReactionDetails> {
    val reactions: List<ReactionRecord> = REDDatabase.reactions.getReactions(messageId)

    return reactions.map { reaction ->
      ReactionDetails(
        sender = Recipient.resolved(reaction.author),
        baseEmoji = EmojiUtil.getCanonicalRepresentation(reaction.emoji),
        displayEmoji = reaction.emoji,
        timestamp = reaction.dateReceived
      )
    }
  }

  fun sendReactionRemoval(messageId: MessageId) {
    val oldReactionRecord = REDDatabase.reactions.getReactions(messageId).firstOrNull { it.author == Recipient.self().id } ?: return
    REDExecutors.BOUNDED.execute {
      MessageSender.sendReactionRemoval(
        AppDependencies.application.applicationContext,
        MessageId(messageId.id),
        oldReactionRecord
      )
    }
  }
}
