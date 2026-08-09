/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.testutil.RecipientTestRule
import com.red.sovereign.transport.UndeliverableMessageException
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroupUtilTest {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun setDataMessageGroupContext_attachesContextForHealthyGroup() {
    val group = recipients.createGroup(recipients.createRecipient("Member One"))
    val builder = REDServiceDataMessage.newBuilder().withTimestamp(1L)

    GroupUtil.setDataMessageGroupContext(ApplicationProvider.getApplicationContext(), builder, group.groupId.requirePush())

    assertTrue(builder.build().groupContext.isPresent)
  }

  @Test(expected = UndeliverableMessageException::class)
  fun setDataMessageGroupContext_throwsWhenGroupPropertiesMissing() {
    val group = recipients.createGroup(recipients.createRecipient("Member One"))

    // Multi-device keeps the group stub but strips its V2 properties once left and deleted.
    every { recipients.signalStore.account.isMultiDevice } returns true
    REDDatabase.groups.setMember(group.groupId, false)
    REDDatabase.groups.clearGroupIfLeftAndDeleted(group.groupId)

    val builder = REDServiceDataMessage.newBuilder().withTimestamp(1L)
    GroupUtil.setDataMessageGroupContext(ApplicationProvider.getApplicationContext(), builder, group.groupId.requirePush())
  }
}
