package com.red.sovereign.jobs

import android.app.Application
import io.mockk.every
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.red.sovereign.database.DraftTable.Draft
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.database.model.ReactionRecord
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.testutil.RecipientTestRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupDeletedBackfillWorkerJobTest {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun run_clearsLeftGroupWithNoActiveThread() {
    val left = recipients.createGroup(recipients.createRecipient(""))
    REDDatabase.threads.getOrCreateThreadIdFor(left.recipientId, isGroup = true)
    REDDatabase.groups.setMember(left.groupId, false)

    GroupDeletedBackfillWorkerJob().run()

    assertFalse(REDDatabase.groups.getGroup(left.groupId).isPresent)
  }

  @Test
  fun run_clearsTerminatedGroupWithNoActiveThread() {
    val terminated = recipients.createGroup(recipients.createRecipient(""))
    REDDatabase.groups.setTerminatedBy(terminated.groupId, recipients.self)

    GroupDeletedBackfillWorkerJob().run()

    assertFalse(REDDatabase.groups.getGroup(terminated.groupId).isPresent)
  }

  @Test
  fun run_leavesActiveGroupUntouched() {
    val active = recipients.createGroup(recipients.createRecipient(""))
    REDDatabase.threads.getOrCreateThreadIdFor(active.recipientId, isGroup = true)

    GroupDeletedBackfillWorkerJob().run()

    val activeGroup = REDDatabase.groups.getGroup(active.groupId)
    assertTrue(activeGroup.isPresent)
    assertTrue(activeGroup.get().isActive)
  }

  @Test
  fun run_leavesLeftGroupWithActiveThreadUntouched() {
    val group = recipients.createGroup(recipients.createRecipient(""))
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(group.recipientId, isGroup = true)
    REDDatabase.threads.markAsActiveEarly(threadId)
    REDDatabase.groups.setMember(group.groupId, false)

    GroupDeletedBackfillWorkerJob().run()

    val groupRecord = REDDatabase.groups.getGroup(group.groupId)
    assertTrue(groupRecord.isPresent)
    assertTrue(groupRecord.get().hasV2GroupProperties)
  }

  @Test
  fun run_triggersRecipientIdDatabaseReferenceCascade_forClearedGroupsRecipientOnly() {
    val clearedGroup = recipients.createGroup(recipients.createRecipient(""))
    REDDatabase.groups.setMember(clearedGroup.groupId, false)

    val keptGroup = recipients.createGroup(recipients.createRecipient(""))
    REDDatabase.groups.setMember(keptGroup.groupId, true)

    insertReaction(clearedGroup.recipientId)
    insertReaction(keptGroup.recipientId)

    GroupDeletedBackfillWorkerJob().run()

    assertFalse(REDDatabase.reactions.hasReactions(MessageId(clearedGroup.recipientId.toLong())))
    assertTrue(REDDatabase.reactions.hasReactions(MessageId(keptGroup.recipientId.toLong())))
  }

  @Test
  fun run_triggersThreadIdDatabaseReferenceCascade_forClearedGroupsThreadOnly() {
    val cleared = recipients.createGroup(recipients.createRecipient(""))
    val clearedThreadId = REDDatabase.threads.getOrCreateThreadIdFor(cleared.recipientId, isGroup = true)
    REDDatabase.groups.setMember(cleared.groupId, false)

    val keepRecipientId = recipients.createGroup(recipients.createRecipient(""))
    val keepThreadId = REDDatabase.threads.getOrCreateThreadIdFor(keepRecipientId.recipientId, isGroup = true)

    REDDatabase.drafts.replaceDrafts(clearedThreadId, listOf(Draft(type = Draft.TEXT, value = "text")))
    REDDatabase.drafts.replaceDrafts(keepThreadId, listOf(Draft(type = Draft.TEXT, value = "text")))

    GroupDeletedBackfillWorkerJob().run()

    assertEquals(0, REDDatabase.drafts.getDrafts(clearedThreadId).count())
    assertEquals(1, REDDatabase.drafts.getDrafts(keepThreadId).count())
  }

  @Test
  fun run_keepsStubForMultiDeviceLeftGroup() {
    val group = recipients.createGroup(recipients.createRecipient(""))
    REDDatabase.groups.setMember(group.groupId, false)

    every { recipients.signalStore.account.isMultiDevice } returns true

    GroupDeletedBackfillWorkerJob().run()

    val record = REDDatabase.groups.getGroup(group.groupId)
    assertTrue(record.isPresent)
    assertFalse(record.get().hasV2GroupProperties)
  }

  private fun insertReaction(id: RecipientId) {
    REDDatabase.reactions.addReaction(MessageId(id.toLong()), ReactionRecord(emoji = "👍", author = id, dateSent = 1L, dateReceived = 1L))
  }
}
