package com.red.sovereign.keyboard.emoji

import com.red.sovereign.components.emoji.EmojiPageModel
import com.red.sovereign.components.emoji.EmojiPageViewGridAdapter
import com.red.sovereign.components.emoji.RecentEmojiPageModel
import com.red.sovereign.components.emoji.parsing.EmojiTree
import com.red.sovereign.emoji.EmojiCategory
import com.red.sovereign.emoji.EmojiSource
import com.red.sovereign.util.adapter.mapping.MappingModel

fun EmojiPageModel.toMappingModels(): List<MappingModel<*>> {
  val emojiTree: EmojiTree = EmojiSource.latest.emojiTree

  return displayEmoji.map {
    val isTextEmoji = EmojiCategory.EMOTICONS.key == key || (RecentEmojiPageModel.KEY == key && emojiTree.getEmoji(it.value, 0, it.value.length) == null)

    if (isTextEmoji) {
      EmojiPageViewGridAdapter.EmojiTextModel(key, it)
    } else {
      EmojiPageViewGridAdapter.EmojiModel(key, it)
    }
  }
}
