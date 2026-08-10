/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.internal.conversation.test

import org.signal.paging.PagedDataSource
import com.red.sovereign.conversation.v2.data.ConversationElementKey
import com.red.sovereign.conversation.v2.data.ConversationMessageElement
import com.red.sovereign.util.adapter.mapping.MappingModel
import kotlin.math.min

class InternalConversationTestDataSource(
  private val size: Int,
  private val generator: ConversationElementGenerator
) : PagedDataSource<ConversationElementKey, MappingModel<*>> {
  override fun size(): Int = size

  override fun load(start: Int, length: Int, totalSize: Int, cancellationRED: PagedDataSource.CancellationRED): MutableList<MappingModel<*>> {
    val end = min(start + length, totalSize)
    return (start until end).map {
      load(ConversationElementKey.forMessage(it.toLong()))!!
    }.toMutableList()
  }

  override fun getKey(data: MappingModel<*>): ConversationElementKey {
    check(data is ConversationMessageElement)

    return ConversationElementKey.forMessage(data.conversationMessage.messageRecord.id)
  }

  override fun load(key: ConversationElementKey?): MappingModel<*>? {
    return key?.let { generator.getMappingModel(it) }
  }
}
