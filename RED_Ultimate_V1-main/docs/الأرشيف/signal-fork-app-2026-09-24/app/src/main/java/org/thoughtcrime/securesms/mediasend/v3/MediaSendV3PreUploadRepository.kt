/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.mediasend.v3

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.models.database.AttachmentId
import org.signal.core.models.media.Media
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.preupload.PreUploadRepository
import org.signal.mediasend.preupload.PreUploadResult
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.mediasend.MediaUploadRepository
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.sms.MessageSender

object MediaSendV3PreUploadRepository : PreUploadRepository {

  @WorkerThread
  override fun preUpload(context: Context, media: Media, recipientId: MediaRecipientId?): PreUploadResult? {
    val attachment = MediaUploadRepository.asAttachment(context, media)
    val recipient = recipientId?.let { Recipient.resolved(RecipientId.from(it.id)) }
    val legacyResult = MessageSender.preUploadPushAttachment(context, attachment, recipient, media) ?: return null
    return PreUploadResult(
      legacyResult.media,
      legacyResult.attachmentId.id,
      legacyResult.jobIds.toMutableList()
    )
  }

  @WorkerThread
  override fun cancelJobs(context: Context, jobIds: List<String>) {
    val jobManager = AppDependencies.jobManager
    jobIds.forEach(jobManager::cancel)
  }

  @WorkerThread
  override fun deleteAttachment(context: Context, attachmentId: Long) {
    REDDatabase.attachments.deleteAttachment(AttachmentId(attachmentId))
  }

  @WorkerThread
  override fun updateAttachmentCaption(context: Context, attachmentId: Long, caption: String?) {
    REDDatabase.attachments.updateAttachmentCaption(AttachmentId(attachmentId), caption)
  }

  @WorkerThread
  override fun updateDisplayOrder(context: Context, orderMap: Map<Long, Int>) {
    val mapped = orderMap.mapKeys { AttachmentId(it.key) }
    REDDatabase.attachments.updateDisplayOrder(mapped)
  }

  @WorkerThread
  override fun deleteAbandonedPreuploadedAttachments(context: Context): Int {
    return REDDatabase.attachments.deleteAbandonedPreuploadedAttachments()
  }
}
