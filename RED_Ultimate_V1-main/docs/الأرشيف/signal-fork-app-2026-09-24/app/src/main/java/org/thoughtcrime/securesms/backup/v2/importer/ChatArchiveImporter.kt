/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.importer

import androidx.core.content.contentValuesOf
import org.signal.archive.proto.Chat
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.SqlUtil
import org.signal.core.util.insertInto
import org.signal.core.util.toInt
import com.red.sovereign.backup.v2.ImportState
import com.red.sovereign.backup.v2.database.restoreWallpaperAttachment
import com.red.sovereign.backup.v2.util.parseChatWallpaper
import com.red.sovereign.backup.v2.util.toLocal
import com.red.sovereign.backup.v2.util.toLocalAttachment
import com.red.sovereign.conversation.colors.ChatColors
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.ThreadTable
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.wallpaper.UriChatWallpaper
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles the importing of [Chat] models into the local database.
 */
object ChatArchiveImporter {

  fun import(chat: Chat, recipientId: RecipientId, importState: ImportState): Long? {
    val chatColor = chat.style?.toLocal(importState)

    val wallpaperAttachmentId: AttachmentId? = chat.style?.wallpaperPhoto?.let { filePointer ->
      filePointer.toLocalAttachment()?.let {
        REDDatabase.attachments.restoreWallpaperAttachment(it)
      }
    }

    val chatWallpaper = chat.style?.parseChatWallpaper(wallpaperAttachmentId)

    val threadId = REDDatabase.writableDatabase
      .insertInto(ThreadTable.TABLE_NAME)
      .values(
        ThreadTable.RECIPIENT_ID to recipientId.serialize(),
        ThreadTable.PINNED_ORDER to chat.pinnedOrder,
        ThreadTable.ARCHIVED to chat.archived.toInt(),
        ThreadTable.READ to if (chat.markedUnread) ThreadTable.ReadStatus.ForcedUnread.serialize() else ThreadTable.ReadStatus.Read.serialize(),
        ThreadTable.ACTIVE to 1
      )
      .run()
      .takeIf { it > 0L }

    if (threadId == null) {
      return null
    }

    REDDatabase.writableDatabase
      .update(
        RecipientTable.TABLE_NAME,
        contentValuesOf(
          RecipientTable.MENTION_SETTING to (if (chat.dontNotifyForMentionsIfMuted) RecipientTable.NotificationSetting.DO_NOT_NOTIFY.id else RecipientTable.NotificationSetting.ALWAYS_NOTIFY.id),
          RecipientTable.MUTE_UNTIL to (chat.muteUntilMs ?: 0),
          RecipientTable.MESSAGE_EXPIRATION_TIME to (chat.expirationTimerMs?.milliseconds?.inWholeSeconds ?: 0),
          RecipientTable.MESSAGE_EXPIRATION_TIME_VERSION to chat.expireTimerVersion,
          RecipientTable.CHAT_COLORS to chatColor?.serialize()?.encode(),
          RecipientTable.CUSTOM_CHAT_COLORS_ID to (chatColor?.id ?: ChatColors.Id.NotSet).longValue,
          RecipientTable.WALLPAPER_URI to if (chatWallpaper is UriChatWallpaper) chatWallpaper.uri.toString() else null,
          RecipientTable.WALLPAPER to chatWallpaper?.serialize()?.encode()
        ),
        "${RecipientTable.ID} = ?",
        SqlUtil.buildArgs(recipientId.toLong())
      )

    return threadId
  }
}
