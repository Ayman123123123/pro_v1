package com.red.sovereign.stories.archive

import com.red.sovereign.database.REDDatabase
import com.red.sovereign.jobs.MultiDeviceDeleteSyncJob

class StoryArchiveRepository {

  fun deleteStories(messageIds: Set<Long>) {
    val records = messageIds.mapNotNull { REDDatabase.messages.getMessageRecordOrNull(it) }.toSet()
    messageIds.forEach { REDDatabase.messages.deleteMessage(it) }
    MultiDeviceDeleteSyncJob.enqueueMessageDeletes(records)
  }
}
