/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.ui.edit

import com.red.sovereign.conversation.ConversationAdapter
import com.red.sovereign.conversation.ConversationBottomSheetCallback
import com.red.sovereign.conversation.ConversationMessage
import com.red.sovereign.database.model.MessageRecord

object EmptyConversationBottomSheetCallback : ConversationBottomSheetCallback {
  override fun getConversationAdapterListener(): ConversationAdapter.ItemClickListener = EmptyConversationAdapterListener
  override fun jumpToMessage(messageRecord: MessageRecord) = Unit
  override fun unpin(conversationMessage: ConversationMessage) = Unit
  override fun copy(conversationMessage: ConversationMessage) = Unit
  override fun delete(conversationMessage: ConversationMessage) = Unit
  override fun save(conversationMessage: ConversationMessage) = Unit
}
