package com.red.sovereign.conversation.ui.inlinequery

import com.red.sovereign.R
import com.red.sovereign.util.adapter.mapping.AnyMappingModel
import com.red.sovereign.util.adapter.mapping.MappingAdapter

class InlineQueryAdapter(listener: (AnyMappingModel) -> Unit) : MappingAdapter() {
  init {
    registerFactory(InlineQueryEmojiResult.Model::class.java, { InlineQueryEmojiResult.ViewHolder(it, listener) }, R.layout.inline_query_emoji_result)
  }
}
