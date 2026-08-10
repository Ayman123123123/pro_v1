package com.red.sovereign.keyboard.sticker

import android.net.Uri
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.nullIfBlank
import com.red.sovereign.R
import com.red.sovereign.database.StickerTables
import com.red.sovereign.database.StickerTables.StickerPackRecordReader
import com.red.sovereign.database.StickerTables.StickerRecordReader
import com.red.sovereign.database.model.StickerPackRecord
import com.red.sovereign.database.model.StickerRecord
import java.util.function.Consumer

private const val RECENT_LIMIT = 24
private const val RECENT_PACK_ID = "RECENT"

class StickerKeyboardRepository(private val stickerTables: StickerTables) {
  fun getStickerPacks(consumer: Consumer<List<KeyboardStickerPack>>) {
    REDExecutors.BOUNDED.execute {
      val packs: MutableList<KeyboardStickerPack> = mutableListOf()

      StickerPackRecordReader(stickerTables.getInstalledStickerPacks()).use { reader ->
        var pack: StickerPackRecord? = reader.getNext()
        while (pack != null) {
          packs += KeyboardStickerPack(packId = pack.packId, title = pack.title.nullIfBlank(), coverUri = pack.cover.uri)
          pack = reader.getNext()
        }
      }

      val fullPacks: MutableList<KeyboardStickerPack> = packs.map { p ->
        val stickers: MutableList<StickerRecord> = mutableListOf()

        StickerRecordReader(stickerTables.getStickersForPack(p.packId)).use { reader ->
          var sticker: StickerRecord? = reader.getNext()
          while (sticker != null) {
            stickers.add(sticker)
            sticker = reader.getNext()
          }
        }

        p.copy(stickers = stickers)
      }.toMutableList()

      val recentStickerPack: KeyboardStickerPack = getRecentStickerPack()
      if (recentStickerPack.stickers.isNotEmpty()) {
        fullPacks.add(0, recentStickerPack)
      }
      consumer.accept(fullPacks)
    }
  }

  private fun getRecentStickerPack(): KeyboardStickerPack {
    val recentStickers: MutableList<StickerRecord> = mutableListOf()

    StickerRecordReader(stickerTables.getRecentlyUsedStickers(RECENT_LIMIT)).use { reader ->
      var recentSticker: StickerRecord? = reader.getNext()
      while (recentSticker != null) {
        recentStickers.add(recentSticker)
        recentSticker = reader.getNext()
      }
    }

    return KeyboardStickerPack(
      packId = RECENT_PACK_ID,
      title = null,
      titleResource = R.string.StickerKeyboard__recently_used,
      coverUri = null,
      coverResource = R.drawable.ic_recent_20,
      stickers = recentStickers
    )
  }

  data class KeyboardStickerPack(
    val packId: String,
    val title: String?,
    val titleResource: Int? = null,
    val coverUri: Uri?,
    val coverResource: Int? = null,
    val stickers: List<StickerRecord> = emptyList()
  )
}
