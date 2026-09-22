/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.CursorUtil
import com.red.sovereign.profiles.ProfileName
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.testutil.RecipientTestRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecipientTableTest_letterHeaders {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun `letter header anchors are always in getREDContacts`() {
    recipients.createRecipient("Alice Anderson")
    recipients.createRecipient("Bob Baker")
    recipients.createRecipient("Charlie Chaplin")
    recipients.createRecipient("David Dunn")

    assertHeaderAnchorsAreVisible()
  }

  @Test
  fun `hidden contact is not a letter header anchor`() {
    recipients.createRecipient("Alice Anderson")
    val hidden = recipients.createRecipient("Carrolyn Carter")
    REDDatabase.recipients.markHidden(hidden)

    assertHeaderAnchorsAreVisible()
  }

  @Test
  fun `blocked contact is not a letter header anchor`() {
    recipients.createRecipient("Alice Anderson")
    val blocked = recipients.createRecipient("Carrolyn Carter")
    REDDatabase.recipients.setBlocked(blocked, true)

    assertHeaderAnchorsAreVisible()
  }

  @Test
  fun `every visible letter section has a header anchor`() {
    recipients.createRecipient(ProfileName.fromParts("Alice", "Anderson"))
    recipients.createRecipient(ProfileName.fromParts("Bob", "Baker"))
    recipients.createRecipient(ProfileName.fromParts("Charlie", "Chaplin"))
    REDDatabase.recipients.setSystemContactName(recipients.createRecipient(ProfileName.fromParts("Dave", "Dunn")), "Dave Dunn")

    val visibleLetters: Set<String> = visibleREDContacts().values
      .filter { it.isNotEmpty() }
      .mapNotNull { name -> name.firstOrNull()?.uppercaseChar()?.toString() }
      .toSet()

    val headerLetters: Set<String> = REDDatabase.recipients.queryREDContactLetterHeaders(
      "",
      RecipientTable.IncludeSelfMode.Exclude,
      includePush = true,
      includeSms = false
    ).values.toSet()

    assertTrue(
      "Every visible letter must have a header anchor. visible=$visibleLetters headers=$headerLetters",
      visibleLetters.all { it in headerLetters }
    )
  }

  private fun assertHeaderAnchorsAreVisible() {
    val visibleIds = visibleREDContactIds()
    val headers = REDDatabase.recipients.queryREDContactLetterHeaders(
      "",
      RecipientTable.IncludeSelfMode.Exclude,
      includePush = true,
      includeSms = false
    )
    val orphaned = headers.keys - visibleIds
    assertTrue(
      "Header anchors must all appear in getREDContacts. orphaned=$orphaned headers=$headers visible=$visibleIds",
      orphaned.isEmpty()
    )
  }

  private fun visibleREDContactIds(): Set<RecipientId> {
    return REDDatabase.recipients.getREDContacts(RecipientTable.IncludeSelfMode.Exclude).use { cursor ->
      val ids = mutableSetOf<RecipientId>()
      while (cursor.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(cursor, RecipientTable.ID)))
      }
      ids
    }
  }

  private fun visibleREDContacts(): Map<RecipientId, String> {
    return REDDatabase.recipients.getREDContacts(RecipientTable.IncludeSelfMode.Exclude).use { cursor ->
      val rows = mutableMapOf<RecipientId, String>()
      while (cursor.moveToNext()) {
        val id = RecipientId.from(CursorUtil.requireLong(cursor, RecipientTable.ID))
        val systemName = CursorUtil.requireString(cursor, RecipientTable.SYSTEM_JOINED_NAME)
        val profileName = CursorUtil.requireString(cursor, RecipientTable.SEARCH_PROFILE_NAME)
        rows[id] = systemName ?: profileName ?: ""
      }
      rows
    }
  }
}
