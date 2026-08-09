/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.red.sovereign.conversation.clicklisteners

import android.view.View
import kotlinx.collections.immutable.toPersistentList
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import com.red.sovereign.attachments.DatabaseAttachment
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.AttachmentCompressionJob
import com.red.sovereign.jobs.AttachmentDownloadJob
import com.red.sovereign.jobs.AttachmentUploadJob
import com.red.sovereign.mms.Slide
import com.red.sovereign.mms.SlidesClickedListener

/**
 * Cancels all attachments passed through to the callback.
 *
 * Creates a persistent copy of the handed list of slides to prevent off-thread
 * manipulation.
 */
internal class AttachmentCancelClickListener : SlidesClickedListener {
  override fun onClick(unused: View, slides: List<Slide>) {
    val toCancel = slides.toPersistentList()

    Log.i(TAG, "Canceling compression/upload/download jobs for ${toCancel.size} items")

    REDExecutors.BOUNDED_IO.execute {
      var cancelCount = 0
      for (slide in toCancel) {
        val attachmentId = (slide.asAttachment() as DatabaseAttachment).attachmentId
        val jobsToCancel = AppDependencies.jobManager.find {
          when (it.factoryKey) {
            AttachmentDownloadJob.KEY -> AttachmentDownloadJob.jobSpecMatchesAttachmentId(it, attachmentId)
            AttachmentCompressionJob.KEY -> AttachmentCompressionJob.jobSpecMatchesAttachmentId(it, attachmentId)
            AttachmentUploadJob.KEY -> AttachmentUploadJob.jobSpecMatchesAttachmentId(it, attachmentId)
            else -> false
          }
        }
        jobsToCancel.forEach {
          AppDependencies.jobManager.cancel(it.id)
          cancelCount++
        }
      }
      Log.i(TAG, "Canceled $cancelCount jobs.")
    }
  }

  companion object {
    private val TAG = Log.tag(AttachmentCancelClickListener::class.java)
  }
}
