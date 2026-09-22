/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.stickers

import com.red.sovereign.database.model.StickerPackRecord
import com.red.sovereign.database.model.StickerRecord
import com.red.sovereign.stickers.manage.AvailableStickerPack
import com.red.sovereign.stickers.manage.InstalledStickerPack
import java.util.UUID

/**
 * Generates sample sticker data to use in compose UI previews.
 */
object StickerPreviewDataFactory {
  fun availablePack(
    packId: String = UUID.randomUUID().toString(),
    title: String,
    author: String,
    isBlessed: Boolean = false,
    downloadStatus: AvailableStickerPack.DownloadStatus = AvailableStickerPack.DownloadStatus.NotDownloaded
  ): AvailableStickerPack = AvailableStickerPack(
    record = StickerPackRecord(
      packId = packId,
      packKey = "packKey",
      title = title,
      author = author,
      cover = StickerRecord(
        rowId = 11,
        packId = packId,
        packKey = "packKey",
        stickerId = 111,
        emoji = "",
        contentType = "image/webp",
        size = 1111,
        isCover = true
      ),
      isInstalled = false
    ),
    isBlessed = isBlessed,
    downloadStatus = downloadStatus
  )

  fun installedPack(
    packId: String = UUID.randomUUID().toString(),
    title: String,
    author: String,
    isBlessed: Boolean = false
  ): InstalledStickerPack = InstalledStickerPack(
    record = StickerPackRecord(
      packId = packId,
      packKey = "packKey",
      title = title,
      author = author,
      cover = StickerRecord(
        rowId = 11,
        packId = packId,
        packKey = "packKey",
        stickerId = 111,
        emoji = "",
        contentType = "image/webp",
        size = 1111,
        isCover = true
      ),
      isInstalled = true
    ),
    isBlessed = isBlessed,
    sortOrder = 0
  )

  fun manifestStickers(count: Int): List<StickerManifest.Sticker> = buildList {
    for (index in 0 until count) {
      add(
        StickerManifest.Sticker(
          "packId-$index",
          "packKey-$index",
          index,
          "😎",
          "image/webp"
        )
      )
    }
  }
}
