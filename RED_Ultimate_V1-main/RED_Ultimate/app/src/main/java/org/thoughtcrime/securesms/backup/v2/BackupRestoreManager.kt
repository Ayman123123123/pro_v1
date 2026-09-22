/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2

import org.signal.core.models.database.AttachmentId
import org.signal.core.util.concurrent.REDExecutors
import com.red.sovereign.attachments.DatabaseAttachment
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.RestoreAttachmentThumbnailJob

/**
 * Responsible for managing logic around restore prioritization
 */
object BackupRestoreManager {

  private val reprioritizedAttachments: HashSet<AttachmentId> = HashSet()

  /**
   * Raise priority of all attachments for the included message records.
   *
   * This is so we can make certain attachments get downloaded more quickly
   */
  fun prioritizeAttachmentsIfNeeded(messageRecords: List<MessageRecord>) {
    REDExecutors.BOUNDED.execute {
      synchronized(this) {
        val restoringAttachments = messageRecords
          .asSequence()
          .mapNotNull { (it as? MmsMessageRecord?)?.slideDeck?.slides }
          .flatten()
          .mapNotNull { it.asAttachment() as? DatabaseAttachment }
          .filter {
            val needThumbnail = it.thumbnailRestoreState == AttachmentTable.ThumbnailRestoreState.NEEDS_RESTORE && it.transferState == AttachmentTable.TRANSFER_RESTORE_IN_PROGRESS
            (needThumbnail || it.thumbnailRestoreState == AttachmentTable.ThumbnailRestoreState.IN_PROGRESS) && !reprioritizedAttachments.contains(it.attachmentId)
          }
          .map { it.attachmentId to it.mmsId }
          .toSet()

        reprioritizedAttachments += restoringAttachments.map { it.first }

        val thumbnailJobs = restoringAttachments.map { (attachmentId, mmsId) ->
          RestoreAttachmentThumbnailJob(attachmentId = attachmentId, messageId = mmsId, highPriority = true)
        }

        if (thumbnailJobs.isNotEmpty()) {
          AppDependencies.jobManager.addAll(thumbnailJobs)
        }
      }
    }
  }
}
