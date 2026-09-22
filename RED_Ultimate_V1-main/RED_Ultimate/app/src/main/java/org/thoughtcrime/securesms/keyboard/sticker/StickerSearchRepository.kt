package com.red.sovereign.keyboard.sticker

import androidx.annotation.WorkerThread
import com.red.sovereign.components.emoji.EmojiUtil
import com.red.sovereign.database.EmojiSearchTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.StickerTables
import com.red.sovereign.database.StickerTables.StickerRecordReader
import com.red.sovereign.database.model.StickerRecord

private const val RECENT_LIMIT = 24
private const val EMOJI_SEARCH_RESULTS_LIMIT = 20

class StickerSearchRepository {

  private val emojiSearchTable: EmojiSearchTable = REDDatabase.emojiSearch
  private val stickerTables: StickerTables = REDDatabase.stickers

  @WorkerThread
  fun search(query: String): List<StickerRecord> {
    if (query.isEmpty()) {
      return StickerRecordReader(stickerTables.getRecentlyUsedStickers(RECENT_LIMIT)).readAll()
    }

    val maybeEmojiQuery: List<StickerRecord> = findStickersForEmoji(query)
    val searchResults: List<StickerRecord> = emojiSearchTable.query(query, EMOJI_SEARCH_RESULTS_LIMIT)
      .map { findStickersForEmoji(it) }
      .flatten()

    return (maybeEmojiQuery + searchResults).distinctBy { it.rowId }
  }

  @WorkerThread
  private fun findStickersForEmoji(emoji: String): List<StickerRecord> {
    val searchEmoji: String = EmojiUtil.getCanonicalRepresentation(emoji)

    return EmojiUtil.getAllRepresentations(searchEmoji)
      .filterNotNull()
      .map { candidate -> StickerRecordReader(stickerTables.getStickersByEmoji(candidate)).readAll() }
      .flatten()
  }
}

private fun StickerRecordReader.readAll(): List<StickerRecord> {
  val stickers: MutableList<StickerRecord> = mutableListOf()
  use { reader ->
    var record: StickerRecord? = reader.getNext()
    while (record != null) {
      stickers.add(record)
      record = reader.getNext()
    }
  }
  return stickers
}
