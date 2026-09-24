package com.red.sovereign.stories.viewer.reply.group

import org.signal.paging.PagedDataSource
import com.red.sovereign.conversation.ConversationMessage
import com.red.sovereign.database.MessageTable
import com.red.sovereign.database.MessageTypes
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.withAttachments
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.recipients.Recipient

class StoryGroupReplyDataSource(private val parentStoryId: Long) : PagedDataSource<MessageId, ReplyBody> {
  override fun size(): Int {
    return REDDatabase.messages.getNumberOfStoryReplies(parentStoryId)
  }

  override fun load(start: Int, length: Int, totalSize: Int, cancellationRED: PagedDataSource.CancellationRED): MutableList<ReplyBody> {
    val rawRecords = mutableListOf<MmsMessageRecord>()
    REDDatabase.messages.getStoryReplies(parentStoryId).use { cursor ->
      cursor.moveToPosition(start - 1)
      val mmsReader = MessageTable.MmsReader(cursor)
      while (cursor.moveToNext() && cursor.position < start + length) {
        rawRecords.add(mmsReader.getCurrent() as MmsMessageRecord)
      }
    }

    return rawRecords.withAttachments().map { readRowFromRecord(it as MmsMessageRecord) }.toMutableList()
  }

  override fun load(key: MessageId): ReplyBody {
    return readRowFromRecord(REDDatabase.messages.getMessageRecord(key.id).withAttachments() as MmsMessageRecord)
  }

  override fun getKey(data: ReplyBody): MessageId {
    return data.key
  }

  private fun readRowFromRecord(record: MmsMessageRecord): ReplyBody {
    val threadRecipient: Recipient = requireNotNull(REDDatabase.threads.getRecipientForThreadId(record.threadId))
    return when {
      record.isRemoteDelete -> ReplyBody.RemoteDelete(record)
      MessageTypes.isStoryReaction(record.type) -> ReplyBody.Reaction(record)
      else -> ReplyBody.Text(
        ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(AppDependencies.application, record, threadRecipient)
      )
    }
  }
}
