/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.CursorUtil
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ThreadTableTest_recents {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var recipientId: RecipientId

  @Before
  fun setUp() {
    recipientId = recipients.createRecipient("Alice Android")
  }

  @Test
  fun getRecentConversationList_excludes_blocked_recipients() {
    createActiveThreadFor(recipientId)

    REDDatabase.recipients.setBlocked(recipientId, true)

    assertFalse(recipientId in getRecentConversationRecipients(limit = 10))
  }

  @Test
  fun getRecentConversationList_excludes_hidden_recipients() {
    createActiveThreadFor(recipientId)

    REDDatabase.recipients.markHidden(recipientId)

    assertFalse(recipientId in getRecentConversationRecipients(limit = 10))
  }

  private fun createActiveThreadFor(id: RecipientId) {
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(id))
    recipients.insertOutgoingMessage(id)
    REDDatabase.threads.update(threadId, true)
  }

  @Suppress("SameParameterValue")
  private fun getRecentConversationRecipients(limit: Int = 10): Set<RecipientId> {
    return REDDatabase.threads
      .getRecentConversationList(limit = limit, includeInactiveGroups = false, individualsOnly = false, groupsOnly = false, hideV1Groups = false, hideSms = false, hideSelf = false)
      .use { cursor ->
        buildSet {
          while (cursor.moveToNext()) {
            add(RecipientId.from(CursorUtil.requireLong(cursor, ThreadTable.RECIPIENT_ID)))
          }
        }
      }
  }
}
