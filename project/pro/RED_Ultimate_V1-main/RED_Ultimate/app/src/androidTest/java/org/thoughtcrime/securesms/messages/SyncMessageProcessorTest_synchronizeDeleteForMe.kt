/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.Base64
import org.signal.core.util.Util
import org.signal.core.util.logging.Log
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.signal.network.api.AttachmentUploadResult
import com.red.sovereign.attachments.Attachment
import com.red.sovereign.attachments.DatabaseAttachment
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.database.CallTable
import com.red.sovereign.database.MessageTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.databaseprotos.SessionSwitchoverEvent
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.testing.MessageContentFuzzer.DeleteForMeSync
import com.red.sovereign.testing.REDActivityRule
import com.red.sovereign.util.IdentityUtil
import org.whispersystems.signalservice.api.messages.REDServiceAttachmentRemoteId
import java.util.UUID

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class SyncMessageProcessorTest_synchronizeDeleteForMe {

  companion object {
    private val TAG = "SyncDeleteForMeTest"
  }

  @get:Rule
  val harness = REDActivityRule(createGroup = true)

  private lateinit var messageHelper: MessageHelper

  @Before
  fun setUp() {
    messageHelper = MessageHelper(harness)
  }

  @After
  fun tearDown() {
    messageHelper.tearDown()
  }

  @Test
  fun singleMessageDelete() {
    // GIVEN
    val message1Timestamp = messageHelper.incomingText().timestamp
    messageHelper.incomingText()

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(2)

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.alice, messageHelper.alice to message1Timestamp)
    )

    // THEN
    messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(1)
  }

  @Test
  fun singleOutgoingMessageDelete() {
    // GIVEN
    val message1Timestamp = messageHelper.outgoingText().timestamp
    messageHelper.incomingText()

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(2)

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.alice, harness.self.id to message1Timestamp)
    )

    // THEN
    messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(1)
  }

  @Test
  fun singleGroupMessageDelete() {
    // GIVEN
    val message1Timestamp = messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId).timestamp
    messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId)
    messageHelper.incomingText(sender = messageHelper.bob, destination = messageHelper.group.recipientId)

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.group.recipientId)!!
    var messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(3)

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.group.recipientId, messageHelper.alice to message1Timestamp)
    )

    // THEN
    messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(2)
  }

  @Test
  fun multipleGroupMessageDelete() {
    // GIVEN
    val message1Timestamp = messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId).timestamp
    messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId)
    val message3Timestamp = messageHelper.incomingText(sender = messageHelper.bob, destination = messageHelper.group.recipientId).timestamp

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.group.recipientId)!!
    var messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(3)

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.group.recipientId, messageHelper.alice to message1Timestamp, messageHelper.bob to message3Timestamp)
    )

    // THEN
    messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(1)
  }

  @Test
  fun allMessagesDelete() {
    // GIVEN
    val message1Timestamp = messageHelper.incomingText().timestamp
    val message2Timestamp = messageHelper.incomingText().timestamp

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(2)

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.alice, messageHelper.alice to message1Timestamp, messageHelper.alice to message2Timestamp)
    )

    // THEN
    messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(0)

    val threadRecord = REDDatabase.threads.getThreadRecord(threadId)
    assertThat(threadRecord?.active).isEqualTo(false)
  }

  @Test
  fun earlyMessagesDelete() {
    // GIVEN
    messageHelper.incomingText().timestamp

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(1)

    // WHEN
    val nextTextMessageTimestamp = messageHelper.nextStartTime(2)
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.alice, messageHelper.alice to nextTextMessageTimestamp)
    )
    messageHelper.incomingText()

    // THEN
    messageCount = REDDatabase.messages.getMessageCountForThread(threadId)
    assertThat(messageCount).isEqualTo(1)
  }

  @Test
  fun multipleConversationMessagesDelete() {
    // GIVEN
    messageHelper.incomingText(sender = messageHelper.alice)
    val aliceMessage2 = messageHelper.incomingText(sender = messageHelper.alice).timestamp

    messageHelper.incomingText(sender = messageHelper.bob)
    val bobMessage2 = messageHelper.incomingText(sender = messageHelper.bob).timestamp

    val aliceThreadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    var aliceMessageCount = REDDatabase.messages.getMessageCountForThread(aliceThreadId)
    assertThat(aliceMessageCount).isEqualTo(2)

    val bobThreadId = REDDatabase.threads.getThreadIdFor(messageHelper.bob)!!
    var bobMessageCount = REDDatabase.messages.getMessageCountForThread(bobThreadId)
    assertThat(bobMessageCount).isEqualTo(2)

    // WHEN
    messageHelper.syncDeleteForMeMessage(
      DeleteForMeSync(conversationId = messageHelper.alice, messageHelper.alice to aliceMessage2),
      DeleteForMeSync(conversationId = messageHelper.bob, messageHelper.bob to bobMessage2)
    )

    // THEN
    aliceMessageCount = REDDatabase.messages.getMessageCountForThread(aliceThreadId)
    assertThat(aliceMessageCount).isEqualTo(1)

    bobMessageCount = REDDatabase.messages.getMessageCountForThread(bobThreadId)
    assertThat(bobMessageCount).isEqualTo(1)
  }

  @Test
  fun singleConversationDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(20)

    // WHEN
    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(
        conversationId = messageHelper.alice,
        messages = messages.takeLast(5).map { it.recipientId to it.timetamp },
        isFullDelete = true
      )
    )

    // THEN
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(0)
    assertThat(REDDatabase.threads.getThreadRecord(threadId)?.active).isEqualTo(false)
  }

  @Test
  fun singleConversationNoRecentsFoundDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(20)

    // WHEN
    val randomFutureMessages = (1..5).map {
      messageHelper.alice to messageHelper.nextStartTime(it)
    }

    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(conversationId = messageHelper.alice, randomFutureMessages, isFullDelete = true)
    )

    // THEN
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(20)
    assertThat(REDDatabase.threads.getThreadRecord(threadId)).isNotNull()

    harness.inMemoryLogger.flush()
    assertThat(harness.inMemoryLogger.entries().filter { it.message?.contains("Unable to find most recent received at timestamp") == true }).hasSize(1)
  }

  @Test
  fun singleConversationNoRecentsFoundNonExpiringRecentsFoundDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(20)

    // WHEN
    val nonExpiringMessages = messages.takeLast(5).map { it.recipientId to it.timetamp }

    val randomFutureMessages = (1..5).map {
      messageHelper.alice to messageHelper.nextStartTime(it)
    }

    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(conversationId = messageHelper.alice, randomFutureMessages, nonExpiringMessages, true)
    )

    // THEN
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(0)
    assertThat(REDDatabase.threads.getThreadRecord(threadId)?.active).isEqualTo(false)

    harness.inMemoryLogger.flush()
    assertThat(harness.inMemoryLogger.entries().filter { it.message?.contains("Using backup non-expiring messages") == true }).hasSize(1)
  }

  @Test
  fun localOnlyRemainingAfterConversationDeleteWithFullDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    Log.v(TAG, "Adding normal messages")
    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val alice = Recipient.resolved(messageHelper.alice)
    Log.v(TAG, "Adding identity message")
    IdentityUtil.markIdentityVerified(harness.context, alice, true, true)
    Log.v(TAG, "Adding profile message")
    REDDatabase.messages.insertProfileNameChangeMessages(alice, "new name", "previous name")
    Log.v(TAG, "Adding call message")
    REDDatabase.calls.insertOneToOneCall(1, System.currentTimeMillis(), alice.id, CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(23)

    // WHEN
    Log.v(TAG, "Processing sync message")
    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(
        conversationId = messageHelper.alice,
        messages = messages.takeLast(5).map { it.recipientId to it.timetamp },
        isFullDelete = true
      )
    )

    // THEN
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(0)
    assertThat(REDDatabase.threads.getThreadRecord(threadId)?.active).isEqualTo(false)
  }

  @Test
  fun localOnlyRemainingAfterConversationDeleteWithoutFullDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val alice = Recipient.resolved(messageHelper.alice)
    IdentityUtil.markIdentityVerified(harness.context, alice, true, true)
    REDDatabase.messages.insertProfileNameChangeMessages(alice, "new name", "previous name")
    REDDatabase.calls.insertOneToOneCall(1, System.currentTimeMillis(), alice.id, CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(23)

    // WHEN
    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(
        conversationId = messageHelper.alice,
        messages = messages.takeLast(5).map { it.recipientId to it.timetamp },
        isFullDelete = false
      )
    )

    // THEN
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(3)
    assertThat(REDDatabase.threads.getThreadRecord(threadId)?.active).isEqualTo(true)
  }

  @Test
  fun groupConversationDelete() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 50) {
      messages += when (i % 3) {
        1 -> MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText(sender = messageHelper.alice, destination = messageHelper.group.recipientId).timestamp)
        2 -> MessageTable.SyncMessageId(messageHelper.bob, messageHelper.incomingText(sender = messageHelper.bob, destination = messageHelper.group.recipientId).timestamp)
        else -> MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText(messageHelper.group.recipientId).timestamp)
      }
    }

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.group.recipientId)!!

    // WHEN
    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(
        conversationId = messageHelper.group.recipientId,
        messages = messages.takeLast(5).map { it.recipientId to it.timetamp },
        isFullDelete = true
      )
    )

    // THEN
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(0)
    assertThat(REDDatabase.threads.getThreadRecord(threadId)?.active).isEqualTo(false)
  }

  @Test
  fun multipleConversationDelete() {
    // GIVEN
    val allMessages = mapOf<RecipientId, MutableList<MessageTable.SyncMessageId>>(
      messageHelper.alice to mutableListOf(),
      messageHelper.bob to mutableListOf()
    )

    allMessages.forEach { (conversation, messages) ->
      for (i in 0 until 10) {
        messages += MessageTable.SyncMessageId(conversation, messageHelper.incomingText(sender = conversation).timestamp)
        messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText(conversationId = conversation).timestamp)
      }
    }

    val threadIds = allMessages.keys.map { REDDatabase.threads.getThreadIdFor(it)!! }
    threadIds.forEach { assertThat(REDDatabase.messages.getMessageCountForThread(it)).isEqualTo(20) }

    // WHEN
    messageHelper.syncDeleteForMeConversation(
      DeleteForMeSync(conversationId = messageHelper.alice, allMessages[messageHelper.alice]!!.takeLast(5).map { it.recipientId to it.timetamp }, isFullDelete = true),
      DeleteForMeSync(conversationId = messageHelper.bob, allMessages[messageHelper.bob]!!.takeLast(5).map { it.recipientId to it.timetamp }, isFullDelete = true)
    )

    // THEN
    threadIds.forEach {
      assertThat(REDDatabase.messages.getMessageCountForThread(it)).isEqualTo(0)
      assertThat(REDDatabase.threads.getThreadRecord(it)?.active).isEqualTo(false)
    }
  }

  @Test
  fun singleLocalOnlyConversation() {
    // GIVEN
    val alice = Recipient.resolved(messageHelper.alice)

    // Insert placeholder message to prevent early thread update deletes
    val oneToOnePlaceHolderMessage = messageHelper.outgoingText().messageId

    val aliceThreadId = REDDatabase.threads.getOrCreateThreadIdFor(messageHelper.alice, isGroup = false)

    IdentityUtil.markIdentityVerified(harness.context, alice, true, false)
    REDDatabase.calls.insertOneToOneCall(1, System.currentTimeMillis(), alice.id, CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)
    REDDatabase.messages.insertProfileNameChangeMessages(alice, "new name", "previous name")
    REDDatabase.messages.markAsSentFailed(messageHelper.outgoingText().messageId)

    // Cleanup and confirm setup
    REDDatabase.messages.deleteMessage(messageId = oneToOnePlaceHolderMessage, threadId = aliceThreadId, notify = false, updateThread = false)
    assertThat(REDDatabase.messages.getMessageCountForThread(aliceThreadId)).isGreaterThan(0)

    // WHEN
    messageHelper.syncDeleteForMeLocalOnlyConversation(messageHelper.alice)

    // THEN
    assertThat(REDDatabase.messages.getMessageCountForThread(aliceThreadId)).isEqualTo(0)
    assertThat(REDDatabase.threads.getThreadRecord(aliceThreadId)?.active).isEqualTo(false)
  }

  @Ignore("counts are consistent for some reason")
  @Test
  fun multipleLocalOnlyConversation() {
    // GIVEN
    val alice = Recipient.resolved(messageHelper.alice)

    // Insert placeholder messages in group and alice thread to prevent early thread update deletes
    val groupPlaceholderMessage = messageHelper.outgoingText(conversationId = messageHelper.group.recipientId).messageId
    val oneToOnePlaceHolderMessage = messageHelper.outgoingText().messageId

    val aliceThreadId = REDDatabase.threads.getOrCreateThreadIdFor(messageHelper.alice, isGroup = false)
    val groupThreadId = REDDatabase.threads.getOrCreateThreadIdFor(messageHelper.group.recipientId, isGroup = true)

    // Identity changes
    IdentityUtil.markIdentityVerified(harness.context, alice, true, true)
    IdentityUtil.markIdentityVerified(harness.context, alice, false, true)
    IdentityUtil.markIdentityVerified(harness.context, alice, true, false)
    IdentityUtil.markIdentityVerified(harness.context, alice, false, false)

    IdentityUtil.markIdentityUpdate(harness.context, alice.id)

    // Calls
    REDDatabase.calls.insertOneToOneCall(1, System.currentTimeMillis(), alice.id, CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, CallTable.Event.ACCEPTED)
    REDDatabase.calls.insertOneToOneCall(2, System.currentTimeMillis(), alice.id, CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, CallTable.Event.MISSED)
    REDDatabase.calls.insertOneToOneCall(3, System.currentTimeMillis(), alice.id, CallTable.Type.AUDIO_CALL, CallTable.Direction.INCOMING, CallTable.Event.MISSED_NOTIFICATION_PROFILE)

    REDDatabase.calls.insertAcceptedGroupCall(4, messageHelper.group.recipientId, CallTable.Direction.INCOMING, System.currentTimeMillis())
    REDDatabase.calls.insertDeclinedGroupCall(5, messageHelper.group.recipientId, System.currentTimeMillis())

    // Detected changes
    REDDatabase.messages.insertProfileNameChangeMessages(alice, "new name", "previous name")
    REDDatabase.messages.insertLearnedProfileNameChangeMessage(alice, null, "username.42")
    REDDatabase.messages.insertNumberChangeMessages(alice.id)
    REDDatabase.messages.insertSmsExportMessage(alice.id, REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!)
    REDDatabase.messages.insertSessionSwitchoverEvent(alice.id, aliceThreadId, SessionSwitchoverEvent())

    // Sent failed
    REDDatabase.messages.markAsSending(messageHelper.outgoingText().messageId)
    REDDatabase.messages.markAsSentFailed(messageHelper.outgoingText().messageId)
    messageHelper.outgoingText().let {
      REDDatabase.messages.markAsSending(it.messageId)
      REDDatabase.messages.markAsRateLimited(it.messageId)
    }

    // Group change
    messageHelper.outgoingGroupChange()

    // Cleanup and confirm setup
    REDDatabase.messages.deleteMessage(messageId = oneToOnePlaceHolderMessage, threadId = aliceThreadId, notify = false, updateThread = false)
    REDDatabase.messages.deleteMessage(messageId = groupPlaceholderMessage, threadId = aliceThreadId, notify = false, updateThread = false)

    REDDatabase.rawDatabase.withinTransaction {
      assertThat(REDDatabase.messages.getMessageCountForThread(aliceThreadId)).isEqualTo(16)
      assertThat(REDDatabase.messages.getMessageCountForThread(groupThreadId)).isEqualTo(10)
    }

    // WHEN
    messageHelper.syncDeleteForMeLocalOnlyConversation(messageHelper.alice, messageHelper.group.recipientId)

    // THEN
    assertThat(REDDatabase.messages.getMessageCountForThread(aliceThreadId)).isEqualTo(0)
    assertThat(REDDatabase.threads.getThreadRecord(aliceThreadId)?.active).isEqualTo(false)

    assertThat(REDDatabase.messages.getMessageCountForThread(groupThreadId)).isEqualTo(0)
    assertThat(REDDatabase.threads.getThreadRecord(groupThreadId)?.active).isEqualTo(false)
  }

  @Test
  fun singleLocalOnlyConversationHasAddressable() {
    // GIVEN
    val messages = mutableListOf<MessageTable.SyncMessageId>()

    for (i in 0 until 10) {
      messages += MessageTable.SyncMessageId(messageHelper.alice, messageHelper.incomingText().timestamp)
      messages += MessageTable.SyncMessageId(harness.self.id, messageHelper.outgoingText().timestamp)
    }

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(20)

    // WHEN
    messageHelper.syncDeleteForMeLocalOnlyConversation(messageHelper.alice)

    // THEN
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(20)
    assertThat(REDDatabase.threads.getThreadRecord(threadId)?.active).isEqualTo(true)

    harness.inMemoryLogger.flush()
    assertThat(harness.inMemoryLogger.entries().filter { it.message?.contains("Thread is not local only") == true }).hasSize(1)
  }

  @Test
  fun singleAttachmentDeletes() {
    // GIVEN
    val message1 = messageHelper.outgoingText { message ->
      message.copy(
        attachments = listOf(
          messageHelper.outgoingAttachment(byteArrayOf(1, 2, 3)),
          messageHelper.outgoingAttachment(byteArrayOf(2, 3, 4), null),
          messageHelper.outgoingAttachment(byteArrayOf(5, 6, 7), null),
          messageHelper.outgoingAttachment(byteArrayOf(10, 11, 12))
        )
      )
    }

    var attachments = REDDatabase.attachments.getAttachmentsForMessage(message1.messageId)
    assertThat(attachments).hasSize(4)

    val threadId = REDDatabase.threads.getThreadIdFor(messageHelper.alice)!!
    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(1)

    // Has all three
    REDDatabase.attachments.finalizeAttachmentAfterUpload(
      id = attachments[0].attachmentId,
      uploadResult = attachments[0].toUploadResult(
        digest = byteArrayOf(attachments[0].attachmentId.id.toByte()),
        uploadTimestamp = message1.timestamp + 1
      )
    )

    // Missing uuid and digest
    REDDatabase.attachments.finalizeAttachmentAfterUpload(
      id = attachments[1].attachmentId,
      uploadResult = attachments[1].toUploadResult(uploadTimestamp = message1.timestamp + 1)
    )

    // Missing uuid and plain text
    REDDatabase.attachments.finalizeAttachmentAfterUpload(
      id = attachments[2].attachmentId,
      uploadResult = attachments[2].toUploadResult(
        digest = byteArrayOf(attachments[2].attachmentId.id.toByte()),
        uploadTimestamp = message1.timestamp + 1
      )
    )
    REDDatabase.rawDatabase.update(AttachmentTable.TABLE_NAME).values(AttachmentTable.DATA_HASH_END to null).where("${AttachmentTable.ID} = ?", attachments[2].attachmentId).run()

    // Different has all three
    REDDatabase.attachments.finalizeAttachmentAfterUpload(
      id = attachments[3].attachmentId,
      uploadResult = attachments[3].toUploadResult(
        digest = byteArrayOf(attachments[3].attachmentId.id.toByte()),
        uploadTimestamp = message1.timestamp + 1
      )
    )

    attachments = REDDatabase.attachments.getAttachmentsForMessage(message1.messageId)

    // WHEN
    messageHelper.syncDeleteForMeAttachment(
      conversationId = messageHelper.alice,
      message = message1.author to message1.timestamp,
      attachments[0].uuid,
      attachments[0].remoteDigest,
      attachments[0].dataHash
    )

    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(1)
    var updatedAttachments = REDDatabase.attachments.getAttachmentsForMessage(message1.messageId)
    assertThat(updatedAttachments).hasSize(3)
    updatedAttachments.forEach { assertThat(it.attachmentId).isNotEqualTo(attachments[0].attachmentId) }

    messageHelper.syncDeleteForMeAttachment(
      conversationId = messageHelper.alice,
      message = message1.author to message1.timestamp,
      attachments[1].uuid,
      attachments[1].remoteDigest,
      attachments[1].dataHash
    )

    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(1)
    updatedAttachments = REDDatabase.attachments.getAttachmentsForMessage(message1.messageId)
    assertThat(updatedAttachments).hasSize(2)
    updatedAttachments.forEach { assertThat(it.attachmentId).isNotEqualTo(attachments[1].attachmentId) }

    messageHelper.syncDeleteForMeAttachment(
      conversationId = messageHelper.alice,
      message = message1.author to message1.timestamp,
      attachments[2].uuid,
      attachments[2].remoteDigest,
      attachments[2].dataHash
    )

    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(1)
    updatedAttachments = REDDatabase.attachments.getAttachmentsForMessage(message1.messageId)
    assertThat(updatedAttachments).hasSize(1)
    updatedAttachments.forEach { assertThat(it.attachmentId).isNotEqualTo(attachments[2].attachmentId) }

    messageHelper.syncDeleteForMeAttachment(
      conversationId = messageHelper.alice,
      message = message1.author to message1.timestamp,
      attachments[3].uuid,
      attachments[3].remoteDigest,
      attachments[3].dataHash
    )

    assertThat(REDDatabase.messages.getMessageCountForThread(threadId)).isEqualTo(0)
    updatedAttachments = REDDatabase.attachments.getAttachmentsForMessage(message1.messageId)
    assertThat(updatedAttachments).isEmpty()

    assertThat(REDDatabase.threads.getThreadRecord(threadId)?.active).isEqualTo(false)
  }

  private fun DatabaseAttachment.copy(
    uuid: UUID? = this.uuid,
    digest: ByteArray? = this.remoteDigest
  ): Attachment {
    return DatabaseAttachment(
      attachmentId = this.attachmentId,
      mmsId = this.mmsId,
      hasData = this.hasData,
      hasThumbnail = false,
      contentType = this.contentType,
      transferProgress = this.transferState,
      size = this.size,
      fileName = this.fileName,
      cdn = this.cdn,
      location = this.remoteLocation,
      key = this.remoteKey,
      digest = digest,
      incrementalDigest = this.incrementalDigest,
      incrementalMacChunkSize = this.incrementalMacChunkSize,
      fastPreflightId = this.fastPreflightId,
      voiceNote = this.voiceNote,
      borderless = this.borderless,
      videoGif = this.videoGif,
      width = this.width,
      height = this.height,
      quote = this.quote,
      caption = this.caption,
      stickerLocator = this.stickerLocator,
      blurHash = this.blurHash,
      audioHash = this.audioHash,
      transformProperties = this.transformProperties,
      displayOrder = this.displayOrder,
      uploadTimestamp = this.uploadTimestamp,
      dataHash = this.dataHash,
      archiveCdn = this.archiveCdn,
      thumbnailRestoreState = this.thumbnailRestoreState,
      archiveTransferState = this.archiveTransferState,
      uuid = uuid,
      quoteTargetContentType = this.quoteTargetContentType,
      metadata = null
    )
  }

  private fun Attachment.toUploadResult(
    digest: ByteArray = this.remoteDigest ?: byteArrayOf(),
    uploadTimestamp: Long = this.uploadTimestamp
  ): AttachmentUploadResult {
    return AttachmentUploadResult(
      remoteId = REDServiceAttachmentRemoteId.V4(this.remoteLocation ?: "some-location"),
      cdnNumber = this.cdn.cdnNumber,
      key = this.remoteKey?.let { Base64.decode(it) } ?: Util.getSecretBytes(64),
      digest = digest,
      incrementalDigest = this.incrementalDigest,
      incrementalDigestChunkSize = this.incrementalMacChunkSize,
      dataSize = this.size,
      uploadTimestamp = uploadTimestamp,
      blurHash = this.blurHash?.hash
    )
  }
}
