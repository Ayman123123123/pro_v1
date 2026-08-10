/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import org.signal.glide.decryptableuri.DecryptableUri
import com.red.sovereign.attachments.DatabaseAttachment
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.util.MediaUtil
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.util.getQuote
import kotlin.time.Duration.Companion.milliseconds

/**
 * A job that should be enqueued after a free-tier backup restore completes.
 * Before enqueueing this job, be sure to call [AttachmentTable.markQuotesThatNeedReconstruction].
 */
class QuoteThumbnailReconstructionJob private constructor(params: Parameters) : Job(params) {

  companion object {
    private val TAG = Log.tag(QuoteThumbnailReconstructionJob::class)

    const val KEY = "QuoteThumbnailReconstructionJob"
  }

  private var activeQuoteAttachment: DatabaseAttachment? = null

  constructor() : this(
    Parameters.Builder()
      .setLifespan(Parameters.IMMORTAL)
      .setMaxInstancesForFactory(2)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey() = KEY

  override fun run(): Result {
    val quoteAttachment = REDDatabase.attachments.getNewestQuotePendingReconstruction()
    if (quoteAttachment == null) {
      Log.i(TAG, "No remaining quotes to reconstruct. Done!")
      return Result.success()
    }

    activeQuoteAttachment = quoteAttachment

    val message = REDDatabase.messages.getMessageRecordOrNull(quoteAttachment.mmsId)
    if (message == null) {
      Log.w(TAG, "Failed to find message for quote attachment. Possible race condition where it was just deleted. Marking as migrated and continuing.")
      REDDatabase.attachments.clearQuotePendingReconstruction(quoteAttachment.attachmentId)
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
      return Result.success()
    }

    if (message.getQuote() == null) {
      Log.w(TAG, "The target message has no quote data. Marking as migrated and continuing.")
      REDDatabase.attachments.clearQuotePendingReconstruction(quoteAttachment.attachmentId)
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
      return Result.success()
    }

    val messageAge = System.currentTimeMillis().milliseconds - message.dateReceived.milliseconds
    if (messageAge > RemoteConfig.messageQueueTime.milliseconds) {
      Log.w(TAG, "Target message is older than the message queue time. Clearing remaining pending quotes and ending the reconstruction process.")
      REDDatabase.attachments.clearAllQuotesPendingReconstruction()
      return Result.success()
    }

    val targetMessage = REDDatabase.messages.getMessageFor(message.getQuote()!!.id, message.getQuote()!!.author)
    if (targetMessage == null) {
      Log.w(TAG, "Failed to find the target message of the quote. Marking as migrated and continuing.")
      REDDatabase.attachments.clearQuotePendingReconstruction(quoteAttachment.attachmentId)
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
      return Result.success()
    }

    val targetAttachment = REDDatabase.attachments.getAttachmentsForMessage(targetMessage.id).firstOrNull { MediaUtil.isImageOrVideoType(it.contentType) && it.uri != null }
    if (targetAttachment == null) {
      Log.w(TAG, "No applicable attachments found for the target message. Marking as migrated and continuing.")
      REDDatabase.attachments.clearQuotePendingReconstruction(quoteAttachment.attachmentId)
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
      return Result.success()
    }

    val thumbnailData = REDDatabase.attachments.generateQuoteThumbnail(DecryptableUri(targetAttachment.uri!!), targetAttachment.contentType, quiet = true)
    if (thumbnailData == null) {
      Log.w(TAG, "Failed to generate a thumbnail for the attachment. Marking as migrated and continuing.")
      REDDatabase.attachments.clearQuotePendingReconstruction(quoteAttachment.attachmentId)
      AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
      return Result.success()
    }

    REDDatabase.attachments.applyReconstructedQuoteData(quoteAttachment.attachmentId, thumbnailData)
    Log.d(TAG, "Successfully reconstructed quote attachment for ${quoteAttachment.attachmentId}")

    AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
    return Result.success()
  }

  override fun onFailure() {
    activeQuoteAttachment?.let { attachment ->
      Log.w(TAG, "Failed during reconstruction. Marking as migrated and continuing.", true)
      REDDatabase.attachments.clearQuotePendingReconstruction(attachment.attachmentId)
    } ?: Log.w(TAG, "Job failed, but no active file is set!")

    AppDependencies.jobManager.add(QuoteThumbnailReconstructionJob())
  }

  class Factory : Job.Factory<QuoteThumbnailReconstructionJob> {
    override fun create(params: Parameters, data: ByteArray?): QuoteThumbnailReconstructionJob {
      return QuoteThumbnailReconstructionJob(params)
    }
  }
}
