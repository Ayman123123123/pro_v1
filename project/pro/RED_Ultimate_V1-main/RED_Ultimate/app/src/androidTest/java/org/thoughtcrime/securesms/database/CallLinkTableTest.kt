/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.runner.RunWith
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.service.webrtc.links.CallLinkCredentials
import com.red.sovereign.service.webrtc.links.CallLinkRoomId
import com.red.sovereign.service.webrtc.links.REDCallLinkState
import com.red.sovereign.testing.REDActivityRule

@RunWith(AndroidJUnit4::class)
class CallLinkTableTest {

  companion object {
    private val ROOM_ID_A = byteArrayOf(1, 2, 3, 4)
    private val ROOM_ID_B = byteArrayOf(2, 2, 3, 4)
    private const val TIMESTAMP_A = 1000L
    private const val TIMESTAMP_B = 2000L
  }

  @get:Rule
  val harness = REDActivityRule(createGroup = true)

//  @Test
  fun givenTwoNonAdminCallLinks_whenIDeleteBeforeFirst_thenIExpectNeitherDeleted() {
    insertTwoNonAdminCallLinksWithEvents()
//    REDDatabase.callLinks.deleteNonAdminCallLinksOnOrBefore(TIMESTAMP_A - 500)
//    val callEvents = REDDatabase.calls.getCalls(0, 2, "", CallLogFilter.ALL)
//    assertEquals(2, callEvents.size)
  }

//  @Test
  fun givenTwoNonAdminCallLinks_whenIDeleteOnFirst_thenIExpectFirstDeleted() {
    insertTwoNonAdminCallLinksWithEvents()
    REDDatabase.callLinks.deleteNonAdminCallLinksOnOrBefore(TIMESTAMP_A)
//    val callEvents = REDDatabase.calls.getCalls(0, 2, "", CallLogFilter.ALL)
//    assertEquals(1, callEvents.size)
//    assertEquals(TIMESTAMP_B, callEvents.first().record.timestamp)
  }

//  @Test
  fun givenTwoNonAdminCallLinks_whenIDeleteAfterFirstAndBeforeSecond_thenIExpectFirstDeleted() {
    insertTwoNonAdminCallLinksWithEvents()
    REDDatabase.callLinks.deleteNonAdminCallLinksOnOrBefore(TIMESTAMP_B - 500)
//    val callEvents = REDDatabase.calls.getCalls(0, 2, "", CallLogFilter.ALL)
//    assertEquals(1, callEvents.size)
//    assertEquals(TIMESTAMP_B, callEvents.first().record.timestamp)
  }

//  @Test
  fun givenTwoNonAdminCallLinks_whenIDeleteOnSecond_thenIExpectBothDeleted() {
    insertTwoNonAdminCallLinksWithEvents()
    REDDatabase.callLinks.deleteNonAdminCallLinksOnOrBefore(TIMESTAMP_B)
//    val callEvents = REDDatabase.calls.getCalls(0, 2, "", CallLogFilter.ALL)
//    assertEquals(0, callEvents.size)
  }

//  @Test
  fun givenTwoNonAdminCallLinks_whenIDeleteAfterSecond_thenIExpectBothDeleted() {
    insertTwoNonAdminCallLinksWithEvents()
    REDDatabase.callLinks.deleteNonAdminCallLinksOnOrBefore(TIMESTAMP_B + 500)
//    val callEvents = REDDatabase.calls.getCalls(0, 2, "", CallLogFilter.ALL)
//    assertEquals(0, callEvents.size)
  }

  private fun insertTwoNonAdminCallLinksWithEvents() {
    insertCallLinkWithEvent(ROOM_ID_A, 1000)
    insertCallLinkWithEvent(ROOM_ID_B, 2000)
  }

  private fun insertCallLinkWithEvent(roomId: ByteArray, timestamp: Long) {
    REDDatabase.callLinks.insertCallLink(
      CallLinkTable.CallLink(
        recipientId = RecipientId.UNKNOWN,
        roomId = CallLinkRoomId.fromBytes(roomId),
        credentials = CallLinkCredentials(
          linkKeyBytes = roomId,
          adminPassBytes = null
        ),
        state = REDCallLinkState(),
        deletionTimestamp = 0L
      )
    )

    val callLinkRecipient = REDDatabase.recipients.getByCallLinkRoomId(CallLinkRoomId.fromBytes(roomId)).get()

    REDDatabase.calls.insertAcceptedGroupCall(
      1,
      callLinkRecipient,
      CallTable.Direction.INCOMING,
      timestamp
    )
  }
}
