/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.database

import org.signal.core.models.database.AttachmentId
import com.red.sovereign.attachments.Attachment
import com.red.sovereign.database.AttachmentTable

fun AttachmentTable.restoreWallpaperAttachment(attachment: Attachment): AttachmentId? {
  return insertAttachmentsForMessage(AttachmentTable.WALLPAPER_MESSAGE_ID, listOf(attachment), emptyList()).values.firstOrNull()
}
