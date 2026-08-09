/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.red.sovereign.messagerequests

import com.red.sovereign.recipients.Recipient

/**
 * Thread recipient and message request state information necessary to render
 * a thread header.
 */
data class MessageRequestRecipientInfo(
  val recipient: Recipient,
  val groupInfo: GroupInfo = GroupInfo.ZERO,
  val sharedGroups: List<String> = emptyList(),
  val messageRequestState: MessageRequestState? = null
)
