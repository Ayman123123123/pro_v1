/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.red.sovereign.jobs

import android.text.TextUtils
import okhttp3.internal.http2.StreamResetException
import org.greenrobot.eventbus.EventBus
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64
import org.signal.core.util.Util
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.inRoundedDays
import org.signal.core.util.logging.Log
import org.signal.core.util.readLength
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RetryLaterException
import org.signal.libsignal.net.UploadTooLargeException
import org.signal.network.api.AttachmentUploadResult
import org.signal.protos.resumableuploads.ResumableUpload
import com.red.sovereign.R
import com.red.sovereign.attachments.Attachment
import com.red.sovereign.attachments.AttachmentUploadUtil
import com.red.sovereign.attachments.DatabaseAttachment
import com.red.sovereign.backup.v2.BackupRepository
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.events.PartProgressEvent
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobmanager.persistence.JobSpec
import com.red.sovereign.jobs.protos.AttachmentUploadJobData
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.mms.PartAuthority
import com.red.sovereign.net.NotPushRegisteredException
import com.red.sovereign.net.REDNetwork
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.service.AttachmentProgressService
import com.red.sovereign.transport.UndeliverableMessageException
import com.red.sovereign.util.MediaUtil
import com.red.sovereign.util.MessageUtil
import com.red.sovereign.util.RemoteConfig
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.REDServiceAttachment
import org.whispersystems.signalservice.api.messages.REDServiceAttachmentStream
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResumableUploadResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import java.io.IOException
import java.net.ProtocolException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Uploads an attachment without alteration.
 *
 * Queue [AttachmentCompressionJob] before to compress.
 */
class AttachmentUploadJob private constructor(
  parameters: Parameters,
  private val attachmentId: AttachmentId,
  private var uploadSpec: ResumableUpload?
) : BaseJob(parameters) {

  companion object {
    const val KEY = "AttachmentUploadJobV3"

    private val TAG = Log.tag(AttachmentUploadJob::class.java)

    private val NETWORK_RESET_THRESHOLD = 1.minutes.inWholeMilliseconds

    val UPLOAD_REUSE_THRESHOLD = 3.days.inWholeMilliseconds

    @JvmStatic
    val maxPlaintextSize: Long
      get() {
        return AttachmentCipherStreamUtil.getMaxPlaintextSizeForCiphertext(RemoteConfig.maxAttachmentSizeBytes)
      }

    @JvmStatic
    fun jobSpecMatchesAttachmentId(jobSpec: JobSpec, attachmentId: AttachmentId): Boolean {
      if (KEY != jobSpec.factoryKey) {
        return false
      }
      val serializedData = jobSpec.serializedData ?: return false
      val data = AttachmentUploadJobData.ADAPTER.decode(serializedData)
      val parsed = AttachmentId(data.attachmentId)
      return attachmentId == parsed
    }
  }

  constructor(attachmentId: AttachmentId) : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    attachmentId,
    null
  )

  override fun serialize(): ByteArray {
    return AttachmentUploadJobData(
      attachmentId = attachmentId.id,
      uploadSpec = uploadSpec
    ).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun shouldTrace(): Boolean = true

  override fun onAdded() {
    Log.i(TAG, "onAdded() $attachmentId")

    val database = REDDatabase.attachments
    val attachment = database.getAttachment(attachmentId)

    if (attachment == null) {
      Log.w(TAG, "Could not fetch attachment from database.")
      return
    }

    val pending = attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE && attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE

    if (pending) {
      Log.i(TAG, "onAdded() Marking attachment progress as 'started'")
      database.setTransferState(attachment.mmsId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED)
    }
  }

  @Throws(Exception::class)
  public override fun onRun() {
    if (!Recipient.self().isRegistered) {
      throw NotPushRegisteredException()
    }

    REDDatabase.attachments.createRemoteKeyIfNecessary(attachmentId)

    val databaseAttachment = REDDatabase.attachments.getAttachment(attachmentId) ?: throw InvalidAttachmentException("Cannot find the specified attachment.")

    if (MediaUtil.isLongTextType(databaseAttachment.contentType) && databaseAttachment.size > MessageUtil.MAX_TOTAL_BODY_SIZE_BYTES) {
      throw UndeliverableMessageException("Long text attachment is too long! Max size: ${MessageUtil.MAX_TOTAL_BODY_SIZE_BYTES} bytes, Actual size: ${databaseAttachment.size} bytes.")
    }

    val timeSinceUpload = System.currentTimeMillis() - databaseAttachment.uploadTimestamp
    if (timeSinceUpload < UPLOAD_REUSE_THRESHOLD && !TextUtils.isEmpty(databaseAttachment.remoteLocation)) {
      Log.i(TAG, "[$attachmentId] We can re-use an already-uploaded file. It was uploaded $timeSinceUpload ms (${timeSinceUpload.milliseconds.inRoundedDays()} days) ago. Skipping.")
      REDDatabase.attachments.setTransferState(databaseAttachment.mmsId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_DONE)
      if (REDStore.account.isPrimaryDevice && BackupRepository.shouldCopyAttachmentToArchive(databaseAttachment.attachmentId, databaseAttachment.mmsId)) {
        Log.i(TAG, "[$attachmentId] The re-used file was not copied to the archive. Copying now.")
        AppDependencies.jobManager.add(CopyAttachmentToArchiveJob(attachmentId))
      }
      return
    } else if (databaseAttachment.uploadTimestamp > 0) {
      Log.i(TAG, "[$attachmentId] This file was previously-uploaded, but too long ago to be re-used. Age: $timeSinceUpload ms (${timeSinceUpload.milliseconds.inRoundedDays()} days)")
      if (databaseAttachment.archiveTransferState != AttachmentTable.ArchiveTransferState.NONE) {
        REDDatabase.attachments.clearArchiveData(attachmentId)
      }
    }

    if (uploadSpec != null && System.currentTimeMillis() > uploadSpec!!.timeout) {
      Log.w(TAG, "[$attachmentId] Upload spec expired! Clearing.")
      uploadSpec = null
    }

    Log.i(TAG, "[$attachmentId] Uploading attachment for message ${databaseAttachment.mmsId}")
    try {
      val existingSpec = uploadSpec?.let { ResumableUploadSpec.from(it) }

      val ciphertextLength = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(databaseAttachment.size))

      val uploadForm = if (existingSpec == null) {
        when (val result = REDNetwork.attachments.getAttachmentV4UploadForm(ciphertextLength)) {
          is RequestResult.Success -> result.result
          is RequestResult.NonSuccess -> throw result.error
          is RequestResult.RetryableNetworkError -> throw RetryLaterException(result.retryAfter ?: defaultBackoff().milliseconds.toJavaDuration())
          is RequestResult.ApplicationError -> throw result.cause
        }
      } else {
        null
      }

      val key = existingSpec?.attachmentKey ?: Base64.decode(databaseAttachment.remoteKey!!)
      val iv = existingSpec?.attachmentIv ?: Util.getSecretBytes(16)

      val checksumSha256 = if (existingSpec == null) {
        PartAuthority.getAttachmentStream(context, databaseAttachment.uri!!).use { stream ->
          AttachmentUploadUtil.computeCiphertextChecksum(key, iv, stream, databaseAttachment.size)
        }
      } else {
        null
      }

      getAttachmentNotificationIfNeeded(databaseAttachment).use { notification ->
        buildAttachmentStream(databaseAttachment, notification).use { localAttachment ->
          val uploadResult: AttachmentUploadResult = REDNetwork.attachments.uploadAttachmentV4(
            form = uploadForm,
            key = key,
            iv = iv,
            checksumSha256 = checksumSha256,
            attachmentStream = localAttachment,
            existingSpec = existingSpec,
            onSpecCreated = { spec -> uploadSpec = spec.toProto() }
          ).successOrThrow()

          REDDatabase.attachments.finalizeAttachmentAfterUpload(databaseAttachment.attachmentId, uploadResult)
          if (REDStore.backup.backsUpMedia) {
            val messageId = REDDatabase.attachments.getMessageId(databaseAttachment.attachmentId)
            when {
              messageId == AttachmentTable.PREUPLOAD_MESSAGE_ID -> {
                Log.i(TAG, "[$attachmentId] Avoid uploading preuploaded attachments to archive. Skipping.")
              }
              REDDatabase.messages.isStory(messageId) -> {
                Log.i(TAG, "[$attachmentId] Attachment is a story. Skipping.")
              }
              REDDatabase.messages.isViewOnce(messageId) -> {
                Log.i(TAG, "[$attachmentId] Attachment is view-once. Skipping.")
              }
              REDDatabase.messages.willMessageExpireBeforeCutoff(messageId) -> {
                Log.i(TAG, "[$attachmentId] Message will expire within 24hrs. Skipping.")
              }
              databaseAttachment.contentType == MediaUtil.LONG_TEXT -> {
                Log.i(TAG, "[$attachmentId] Long text attachment. Skipping.")
              }
              REDStore.account.isLinkedDevice -> {
                Log.i(TAG, "[$attachmentId] Linked device. Skipping archive.")
              }
              else -> {
                Log.i(TAG, "[$attachmentId] Enqueuing job to copy to archive.")
                AppDependencies.jobManager.add(CopyAttachmentToArchiveJob(attachmentId))
              }
            }
          }
        }
      }
    } catch (e: StreamResetException) {
      val lastReset = REDStore.misc.lastNetworkResetDueToStreamResets
      val now = System.currentTimeMillis()

      if (lastReset > now || lastReset + NETWORK_RESET_THRESHOLD > now) {
        Log.w(TAG, "Our existing connections is getting repeatedly denied by the server, reset network to establish new connections")
        AppDependencies.resetNetwork()
        AppDependencies.startNetwork()
        REDStore.misc.lastNetworkResetDueToStreamResets = now
      } else {
        Log.i(TAG, "Stream reset during upload, not resetting network yet, last reset: $lastReset")
      }

      resetProgressListeners(databaseAttachment)

      throw e
    } catch (e: NonSuccessfulResumableUploadResponseCodeException) {
      if (e.code == 400) {
        Log.w(TAG, "[$attachmentId] Failed to upload due to a 400 when getting resumable upload information. Clearing upload spec.", e)
        uploadSpec = null
      }

      resetProgressListeners(databaseAttachment)

      throw e
    } catch (e: ResumeLocationInvalidException) {
      Log.w(TAG, "[$attachmentId] Resume location invalid. Clearing upload spec.", e)
      uploadSpec = null

      resetProgressListeners(databaseAttachment)

      throw e
    } catch (e: IOException) {
      if (e is ProtocolException || e.cause is ProtocolException) {
        Log.w(TAG, "[$attachmentId] Length may be incorrect. Recalculating.", e)
        val actualLength = REDDatabase.attachments.getAttachmentStream(attachmentId, 0).use { it.readLength() }
        if (actualLength != databaseAttachment.size) {
          Log.w(TAG, "[$attachmentId] Length was incorrect! Will update. Previous: ${databaseAttachment.size}, Newly-Calculated: $actualLength")
          REDDatabase.attachments.updateAttachmentLength(attachmentId, actualLength)
          uploadSpec = null
        } else {
          Log.i(TAG, "[$attachmentId] Length was correct. No action needed. Will retry.")
        }
      }

      resetProgressListeners(databaseAttachment)

      throw e
    }
  }

  private fun getAttachmentNotificationIfNeeded(attachment: Attachment): AttachmentProgressService.Controller? {
    return if (attachment.size >= AttachmentUploadUtil.FOREGROUND_LIMIT_BYTES) {
      AttachmentProgressService.start(context, context.getString(R.string.AttachmentUploadJob_uploading_media))
    } else {
      null
    }
  }

  private fun resetProgressListeners(attachment: DatabaseAttachment) {
    EventBus.getDefault().postSticky(PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, 0, -1))
  }

  override fun onFailure() {
    val database = REDDatabase.attachments
    val databaseAttachment = database.getAttachment(attachmentId)
    if (databaseAttachment == null) {
      Log.i(TAG, "[$attachmentId] Could not find attachment in DB for upload job upon failure/cancellation.")
      return
    }

    database.setTransferProgressFailed(attachmentId, databaseAttachment.mmsId)
  }

  override fun getNextRunAttemptBackoff(pastAttemptCount: Int, exception: java.lang.Exception): Long {
    if (exception is RetryLaterException && exception.duration != null) {
      return exception.duration.toMillis()
    }

    return super.getNextRunAttemptBackoff(pastAttemptCount, exception)
  }

  override fun onShouldRetry(exception: Exception): Boolean {
    return exception is IOException && exception !is NotPushRegisteredException && exception !is UploadTooLargeException
  }

  @Throws(InvalidAttachmentException::class)
  private fun buildAttachmentStream(attachment: Attachment, notification: AttachmentProgressService.Controller?): REDServiceAttachmentStream {
    if (attachment.uri == null || attachment.size == 0L) {
      throw InvalidAttachmentException(IOException("Outgoing attachment has no data!"))
    }

    return try {
      AttachmentUploadUtil.buildREDServiceAttachmentStream(
        context = context,
        attachment = attachment,
        cancellationRED = { isCanceled },
        progressListener = object : REDServiceAttachment.ProgressListener {
          override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
            REDExecutors.BOUNDED_IO.execute {
              EventBus.getDefault().postSticky(PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, progress))
              notification?.updateProgress(progress.value)
            }
          }

          override fun shouldCancel(): Boolean {
            return isCanceled
          }
        }
      )
    } catch (e: IOException) {
      throw InvalidAttachmentException(e)
    }
  }

  private inner class InvalidAttachmentException : Exception {
    constructor(message: String?) : super(message)
    constructor(e: Exception?) : super(e)
  }

  class Factory : Job.Factory<AttachmentUploadJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AttachmentUploadJob {
      val data = AttachmentUploadJobData.ADAPTER.decode(serializedData!!)
      return AttachmentUploadJob(
        parameters = parameters,
        attachmentId = AttachmentId(data.attachmentId),
        data.uploadSpec
      )
    }
  }
}
