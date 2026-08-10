/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2

import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.isNotNullOrBlank
import org.signal.libsignal.messagebackup.BackupForwardSecrecyToken
import org.signal.libsignal.messagebackup.MessageBackup
import org.signal.libsignal.messagebackup.ValidationError
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.util.isStory
import com.red.sovereign.util.isStoryReaction
import java.io.File
import java.io.IOException
import org.signal.libsignal.messagebackup.BackupKey as LibREDBackupKey
import org.signal.libsignal.messagebackup.MessageBackupKey as LibREDMessageBackupKey

object ArchiveValidator {

  fun validateREDBackup(backupFile: File, backupKey: MessageBackupKey, backupForwardSecrecyToken: BackupForwardSecrecyToken): ValidationResult {
    return validate(backupFile, backupKey, backupForwardSecrecyToken, forTransfer = false)
  }

  fun validateLocalOrLinking(backupFile: File, backupKey: MessageBackupKey, forTransfer: Boolean): ValidationResult {
    return validate(backupFile, backupKey, forwardSecrecyToken = null, forTransfer)
  }

  /**
   * Validates the provided [backupFile] that is encrypted with the provided [backupKey].
   */
  fun validate(backupFile: File, backupKey: MessageBackupKey, forwardSecrecyToken: BackupForwardSecrecyToken?, forTransfer: Boolean): ValidationResult {
    return try {
      val backupId = backupKey.deriveBackupId(REDStore.account.requireAci())
      val libREDBackupKey = LibREDBackupKey(backupKey.value)
      val libREDMessageBackupKey = LibREDMessageBackupKey(libREDBackupKey, backupId.value, forwardSecrecyToken)

      MessageBackup.validate(
        libREDMessageBackupKey,
        if (forTransfer) MessageBackup.Purpose.DEVICE_TRANSFER else MessageBackup.Purpose.REMOTE_BACKUP,
        { backupFile.inputStream() },
        backupFile.length()
      )

      ValidationResult.Success
    } catch (e: IOException) {
      ValidationResult.ReadError(e)
    } catch (e: ValidationError) {
      if (e.message?.contains("have the same phone number") == true) {
        val recipientIds = """RecipientId\((\d+)\)""".toRegex()
          .findAll(e.message ?: "")
          .map { it.groupValues[1] }
          .mapNotNull { it.toLongOrNull() }
          .map { RecipientId.from(it) }
          .toList()

        val recipientIdA = recipientIds.getOrNull(0)
        val recipientIdB = recipientIds.getOrNull(1)

        val e164A = recipientIdA?.let { Recipient.resolved(it).e164.orElse("UNKNOWN") }.let { "KEEP_E164::$it" }
        val e164B = recipientIdB?.let { Recipient.resolved(it).e164.orElse("UNKNOWN") }.let { "KEEP_E164::$it" }

        ValidationResult.RecipientDuplicateE164Error(
          exception = e,
          details = DuplicateRecipientDetails(
            recipientIdA = recipientIds.getOrNull(0),
            recipientIdB = recipientIds.getOrNull(1),
            e164A = e164A,
            e164B = e164B
          )
        )
      } else {
        val sentTimestamp = "\\d{10,}+".toRegex().find(e.message ?: "")?.value?.toLongOrNull()
        ValidationResult.MessageValidationError(
          exception = e,
          messageDetails = sentTimestamp?.let { fetchMessageDetails(it) } ?: emptyList()
        )
      }
    }
  }

  private fun fetchMessageDetails(sentTimestamp: Long): List<MessageDetails> {
    val messages = REDDatabase.messages.getMessagesBySentTimestamp(sentTimestamp)
    return messages.map {
      MessageDetails(
        messageId = it.id,
        dateSent = it.dateSent,
        threadId = it.threadId,
        threadRecipientId = REDDatabase.threads.getRecipientForThreadId(it.threadId)?.id?.toLong() ?: 0L,
        type = it.type,
        fromRecipientId = it.fromRecipient.id.toLong(),
        toRecipientId = it.toRecipient.id.toLong(),
        hasBody = it.body.isNotNullOrBlank(),
        hasExtras = it.messageExtras != null,
        outgoing = it.isOutgoing,
        viewOnce = it.isViewOnce,
        isStory = it.isStory(),
        isStoryReaction = it.isStoryReaction(),
        originalMessageId = it.originalMessageId?.id ?: 0,
        isLatestRevision = it.isLatestRevision
      )
    }
  }

  sealed interface ValidationResult {
    data object Success : ValidationResult
    data class ReadError(val exception: IOException) : ValidationResult
    data class MessageValidationError(
      val exception: ValidationError,
      val messageDetails: List<MessageDetails>
    ) : ValidationResult
    data class RecipientDuplicateE164Error(
      val exception: ValidationError,
      val details: DuplicateRecipientDetails
    ) : ValidationResult
  }

  data class MessageDetails(
    val messageId: Long,
    val dateSent: Long,
    val threadId: Long,
    val threadRecipientId: Long,
    val type: Long,
    val fromRecipientId: Long,
    val toRecipientId: Long,
    val hasBody: Boolean,
    val hasExtras: Boolean,
    val outgoing: Boolean,
    val viewOnce: Boolean,
    val isStory: Boolean,
    val isStoryReaction: Boolean,
    val originalMessageId: Long,
    val isLatestRevision: Boolean
  )

  data class DuplicateRecipientDetails(
    val recipientIdA: RecipientId?,
    val recipientIdB: RecipientId?,
    val e164A: String?,
    val e164B: String?
  )
}
