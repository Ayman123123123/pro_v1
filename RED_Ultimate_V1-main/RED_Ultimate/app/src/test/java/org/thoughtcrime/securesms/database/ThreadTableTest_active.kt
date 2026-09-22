/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.red.sovereign.components.settings.app.chats.folders.ChatFolderRecord
import com.red.sovereign.conversationlist.model.ConversationFilter
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ThreadTableTest_active {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var recipientId: RecipientId
  private val allChats: ChatFolderRecord = ChatFolderRecord(folderType = ChatFolderRecord.FolderType.ALL)

  @Before
  fun setUp() {
    recipientId = recipients.createRecipient("Alice Android")
  }

  @Test
  fun givenActiveUnarchivedThread_whenIGetUnarchivedConversationList_thenIExpectThread() {
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
    recipients.insertOutgoingMessage(recipientId)
    REDDatabase.threads.update(threadId, false)

    REDDatabase.threads.getUnarchivedConversationList(
      ConversationFilter.OFF,
      false,
      0,
      10,
      allChats
    ).use { threads ->
      assertEquals(1, threads.count)

      val record = ThreadTable.StaticReader(threads, ApplicationProvider.getApplicationContext()).getNext()

      assertNotNull(record)
      assertEquals(record!!.recipient.id, recipientId)
    }
  }

  @Test
  fun givenInactiveUnarchivedThread_whenIGetUnarchivedConversationList_thenIExpectNoThread() {
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
    recipients.insertOutgoingMessage(recipientId)
    REDDatabase.threads.update(threadId, false)
    REDDatabase.threads.deleteConversation(threadId)

    REDDatabase.threads.getUnarchivedConversationList(
      ConversationFilter.OFF,
      false,
      0,
      10,
      allChats
    ).use { threads ->
      assertEquals(0, threads.count)
    }

    val threadId2 = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
    assertEquals(threadId2, threadId)
  }

  @Test
  fun givenActiveArchivedThread_whenIGetUnarchivedConversationList_thenIExpectNoThread() {
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
    recipients.insertOutgoingMessage(recipientId)
    REDDatabase.threads.update(threadId, false)
    REDDatabase.threads.setArchived(setOf(threadId), true)

    REDDatabase.threads.getUnarchivedConversationList(
      ConversationFilter.OFF,
      false,
      0,
      10,
      allChats
    ).use { threads ->
      assertEquals(0, threads.count)
    }
  }

  @Test
  fun givenActiveArchivedThread_whenIGetArchivedConversationList_thenIExpectThread() {
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
    recipients.insertOutgoingMessage(recipientId)
    REDDatabase.threads.update(threadId, false)
    REDDatabase.threads.setArchived(setOf(threadId), true)

    REDDatabase.threads.getArchivedConversationList(
      ConversationFilter.OFF,
      0,
      10
    ).use { threads ->
      assertEquals(1, threads.count)
    }
  }

  @Test
  fun givenInactiveArchivedThread_whenIGetArchivedConversationList_thenIExpectNoThread() {
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
    recipients.insertOutgoingMessage(recipientId)
    REDDatabase.threads.update(threadId, false)
    REDDatabase.threads.deleteConversation(threadId)
    REDDatabase.threads.setArchived(setOf(threadId), true)

    REDDatabase.threads.getArchivedConversationList(
      ConversationFilter.OFF,
      0,
      10
    ).use { threads ->
      assertEquals(0, threads.count)
    }

    val threadId2 = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
    assertEquals(threadId2, threadId)
  }

  @Test
  fun givenActiveArchivedThread_whenIDeactivateThread_thenIExpectNoMessages() {
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
    recipients.insertOutgoingMessage(recipientId)
    REDDatabase.threads.update(threadId, false)

    REDDatabase.messages.getConversation(threadId).use {
      assertEquals(1, it.count)
    }

    REDDatabase.threads.deleteConversation(threadId)

    REDDatabase.messages.getConversation(threadId).use {
      assertEquals(0, it.count)
    }
  }
}
