/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.v2

import com.red.sovereign.groups.GroupId
import com.red.sovereign.recipients.Recipient

/**
 * Indicates if we should present an additional review warning banner
 * for an individual or group.
 */
data class RequestReviewState(
  val individualReviewState: IndividualReviewState? = null,
  val groupReviewState: GroupReviewState? = null
) {

  fun shouldShowReviewBanner(): Boolean {
    return individualReviewState != null || groupReviewState != null
  }

  /** Recipient is in message request state and has similar name as someone else */
  data class IndividualReviewState(val target: Recipient, val firstDuplicate: Recipient)

  /** Group has multiple members with similar names */
  data class GroupReviewState(val groupId: GroupId.V2, val target: Recipient, val firstDuplicate: Recipient, val count: Int)
}
