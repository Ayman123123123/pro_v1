/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.CursorUtil
import com.red.sovereign.components.settings.app.chats.folders.ChatFolderRecord
import com.red.sovereign.conversationlist.model.ConversationFilter
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ThreadTableTest_pinned {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var recipient: RecipientId
  private val allChats: ChatFolderRecord = ChatFolderRecord(folderType = ChatFolderRecord.FolderType.ALL)

  @Before
  fun setUp() {
    recipient = recipients.createRecipient("Alice Android")
  }

  @Test
  fun givenAPinnedThread_whenIDeleteTheLastMessage_thenIDoNotDeleteOrUnpinTheThread() {
    // GIVEN
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    val messageId = recipients.insertOutgoingMessage(recipient)
    REDDatabase.threads.pinConversations(listOf(threadId))

    // WHEN
    REDDatabase.messages.deleteMessage(messageId)

    // THEN
    val pinned = REDDatabase.threads.getPinnedThreadIds()
    assertTrue(threadId in pinned)
  }

  @Test
  fun givenAPinnedThread_whenIDeleteTheLastMessage_thenIExpectTheThreadInUnarchivedCount() {
    // GIVEN
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    val messageId = recipients.insertOutgoingMessage(recipient)
    REDDatabase.threads.pinConversations(listOf(threadId))

    // WHEN
    REDDatabase.messages.deleteMessage(messageId)

    // THEN
    val unarchivedCount = REDDatabase.threads.getUnarchivedConversationListCount(ConversationFilter.OFF, allChats)
    assertEquals(1, unarchivedCount)
  }

  @Test
  fun givenAPinnedThread_whenIDeleteTheLastMessage_thenIExpectPinnedThreadInUnarchivedList() {
    // GIVEN
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipient))
    val messageId = recipients.insertOutgoingMessage(recipient)
    REDDatabase.threads.pinConversations(listOf(threadId))

    // WHEN
    REDDatabase.messages.deleteMessage(messageId)

    // THEN
    REDDatabase.threads.getUnarchivedConversationList(ConversationFilter.OFF, true, 0, 1, allChats).use {
      it.moveToFirst()
      assertEquals(threadId, CursorUtil.requireLong(it, ThreadTable.ID))
    }
  }
}
