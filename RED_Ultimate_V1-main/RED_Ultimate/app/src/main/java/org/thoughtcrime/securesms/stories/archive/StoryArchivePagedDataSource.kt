package com.red.sovereign.stories.archive

import org.signal.paging.PagedDataSource
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.model.StoryType
import com.red.sovereign.database.withAttachments
import com.red.sovereign.keyvalue.REDStore

class StoryArchivePagedDataSource(
  private val sortNewest: Boolean
) : PagedDataSource<Long, ArchivedStoryItem> {

  private val includeActive = REDStore.story.isArchiveEnabled

  override fun size(): Int {
    return REDDatabase.messages.getArchiveScreenStoriesCount(includeActive)
  }

  override fun load(start: Int, length: Int, totalSize: Int, cancellationRED: PagedDataSource.CancellationRED): List<ArchivedStoryItem> {
    val rawRecords = REDDatabase.messages.getArchiveScreenStoriesPage(includeActive, sortNewest, start, length).use { reader ->
      reader.mapNotNull { record ->
        if (cancellationRED.isCanceled) return@use emptyList()
        record
      }
    }

    return rawRecords.withAttachments().map { record ->
      val mmsRecord = record as? MmsMessageRecord
      ArchivedStoryItem(
        messageId = record.id,
        dateSent = record.dateSent,
        thumbnailUri = mmsRecord?.slideDeck?.thumbnailSlide?.uri,
        blurHash = mmsRecord?.slideDeck?.thumbnailSlide?.placeholderBlur,
        storyType = mmsRecord?.storyType ?: StoryType.NONE,
        body = record.body
      )
    }
  }

  override fun load(key: Long): ArchivedStoryItem? {
    val record = REDDatabase.messages.getMessageRecordOrNull(key) ?: return null
    val mmsRecord = record.withAttachments() as? MmsMessageRecord
    return ArchivedStoryItem(
      messageId = record.id,
      dateSent = record.dateSent,
      thumbnailUri = mmsRecord?.slideDeck?.thumbnailSlide?.uri,
      blurHash = mmsRecord?.slideDeck?.thumbnailSlide?.placeholderBlur,
      storyType = mmsRecord?.storyType ?: StoryType.NONE,
      body = record.body
    )
  }

  override fun getKey(data: ArchivedStoryItem): Long {
    return data.messageId
  }
}
