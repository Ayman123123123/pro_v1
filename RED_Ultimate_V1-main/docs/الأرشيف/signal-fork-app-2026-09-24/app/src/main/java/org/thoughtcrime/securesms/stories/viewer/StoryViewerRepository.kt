package com.red.sovereign.stories.viewer

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import com.red.sovereign.database.MessageTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.DistributionListId
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.withAttachments
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId

/**
 * Open for testing
 */
open class StoryViewerRepository {
  fun getFirstStory(recipientId: RecipientId, storyId: Long): Single<MmsMessageRecord> {
    return if (storyId > 0) {
      Single.fromCallable {
        REDDatabase.messages.getMessageRecord(storyId).withAttachments() as MmsMessageRecord
      }
    } else {
      Single.fromCallable {
        val recipient = Recipient.resolved(recipientId)
        val reader: MessageTable.Reader = if (recipient.isMyStory || recipient.isSelf) {
          REDDatabase.messages.getAllOutgoingStories(false, 1)
        } else {
          val unread = REDDatabase.messages.getUnreadStories(recipientId, 1)
          if (unread.iterator().hasNext()) {
            unread
          } else {
            REDDatabase.messages.getAllStoriesFor(recipientId, 1)
          }
        }
        reader.use { it.iterator().next() }.withAttachments() as MmsMessageRecord
      }
    }
  }

  fun getStories(hiddenStories: Boolean, isOutgoingOnly: Boolean): Single<List<RecipientId>> {
    return Single.create { emitter ->
      val myStoriesId = REDDatabase.recipients.getOrInsertFromDistributionListId(DistributionListId.MY_STORY)
      val myStories = Recipient.resolved(myStoriesId)
      val releaseChannelId = REDStore.releaseChannel.releaseChannelRecipientId
      val recipientIds = REDDatabase.messages.getOrderedStoryRecipientsAndIds(isOutgoingOnly).groupBy {
        val recipient = Recipient.resolved(it.recipientId)
        if (recipient.isDistributionList) {
          myStories
        } else {
          recipient
        }
      }.keys.filter {
        if (hiddenStories) {
          it.shouldHideStory
        } else {
          !it.shouldHideStory
        }
      }.map { it.id }

      emitter.onSuccess(
        recipientIds.floatToTop(releaseChannelId).floatToTop(myStoriesId)
      )
    }.subscribeOn(Schedulers.io())
  }

  private fun List<RecipientId>.floatToTop(recipientId: RecipientId?): List<RecipientId> {
    return if (recipientId != null && contains(recipientId)) {
      listOf(recipientId) + (this - recipientId)
    } else {
      this
    }
  }
}
