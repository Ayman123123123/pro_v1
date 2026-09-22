/**
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.calls.log

import com.red.sovereign.database.CallLinkTable
import com.red.sovereign.database.CallTable
import com.red.sovereign.database.model.databaseprotos.GroupCallUpdateDetails
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.service.webrtc.CallLinkPeekInfo
import com.red.sovereign.service.webrtc.links.CallLinkRoomId

/**
 * A row to be displayed in the call log
 */
sealed class CallLogRow {

  abstract val id: Id

  /**
   * A call link with no "active" events.
   */
  data class CallLink(
    val record: CallLinkTable.CallLink,
    val recipient: Recipient,
    val searchQuery: String?,
    val callLinkPeekInfo: CallLinkPeekInfo?,
    override val id: Id = Id.CallLink(record.roomId)
  ) : CallLogRow()

  /**
   * An incoming, outgoing, or missed call.
   */
  data class Call(
    val record: CallTable.Call,
    val peer: Recipient,
    val date: Long,
    val groupCallState: GroupCallState,
    val children: Set<Long>,
    val searchQuery: String?,
    val callLinkPeekInfo: CallLinkPeekInfo?,
    val canUserBeginCall: CanStartCall,
    override val id: Id = Id.Call(children)
  ) : CallLogRow()

  /**
   * A row which can be used to clear the current filter.
   */
  data object ClearFilter : CallLogRow() {
    override val id: Id = Id.ClearFilter
  }

  data object ClearFilterEmpty : CallLogRow() {
    override val id: Id = Id.ClearFilterEmpty
  }

  data object CreateCallLink : CallLogRow() {
    override val id: Id = Id.CreateCallLink
  }

  sealed class Id {
    data class Call(val children: Set<Long>) : Id()
    data class CallLink(val roomId: CallLinkRoomId) : Id()
    data object ClearFilter : Id()
    data object ClearFilterEmpty : Id()
    data object CreateCallLink : Id()
  }

  enum class GroupCallState {
    /**
     * No group call available.
     */
    NONE,

    /**
     * Active, but the local user is not in the call.
     */
    ACTIVE,

    /**
     * Active and the local user is in the call
     */
    LOCAL_USER_JOINED,

    /**
     * Active but the call is full.
     */
    FULL;

    companion object {
      fun fromDetails(groupCallUpdateDetails: GroupCallUpdateDetails?): GroupCallState {
        if (groupCallUpdateDetails == null) {
          return NONE
        }

        if (groupCallUpdateDetails.isCallFull) {
          return FULL
        }

        if (groupCallUpdateDetails.inCallUuids.contains(Recipient.self().requireAci().rawUuid.toString())) {
          return LOCAL_USER_JOINED
        }

        return if (groupCallUpdateDetails.inCallUuids.isNotEmpty()) {
          ACTIVE
        } else {
          NONE
        }
      }
    }
  }

  enum class CanStartCall {
    ALLOWED,
    ADMIN_ONLY,
    NOT_A_MEMBER,
    GROUP_TERMINATED
  }
}
