/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.importer

import org.signal.archive.proto.CallLink
import org.signal.core.util.isEmpty
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallLinkRootKey
import org.signal.ringrtc.CallLinkState
import com.red.sovereign.backup.v2.ArchiveCallLink
import com.red.sovereign.database.CallLinkTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.service.webrtc.links.CallLinkCredentials
import com.red.sovereign.service.webrtc.links.CallLinkRoomId
import com.red.sovereign.service.webrtc.links.REDCallLinkState
import java.time.Instant

/**
 * Handles the importing of [ArchiveCallLink] models into the local database.
 */
object CallLinkArchiveImporter {

  private val TAG = Log.tag(CallLinkArchiveImporter::class)

  fun import(callLink: ArchiveCallLink): RecipientId? {
    val rootKey: CallLinkRootKey = try {
      CallLinkRootKey(callLink.rootKey.toByteArray())
    } catch (e: Exception) {
      if (callLink.rootKey.isEmpty()) {
        Log.w(TAG, "Missing root key!")
      } else {
        Log.w(TAG, "Failed to parse a non-empty root key!")
      }
      return null
    }

    return REDDatabase.callLinks.insertCallLink(
      CallLinkTable.CallLink(
        recipientId = RecipientId.UNKNOWN,
        roomId = CallLinkRoomId.fromCallLinkRootKey(rootKey),
        credentials = CallLinkCredentials(callLink.rootKey.toByteArray(), callLink.adminKey?.toByteArray()),
        state = REDCallLinkState(
          name = callLink.name,
          restrictions = callLink.restrictions.toLocal(),
          expiration = Instant.ofEpochMilli(callLink.expirationMs)
        ),
        deletionTimestamp = 0L
      )
    )
  }
}

private fun CallLink.Restrictions.toLocal(): CallLinkState.Restrictions {
  return when (this) {
    CallLink.Restrictions.ADMIN_APPROVAL -> CallLinkState.Restrictions.ADMIN_APPROVAL
    CallLink.Restrictions.NONE -> CallLinkState.Restrictions.NONE
    CallLink.Restrictions.UNKNOWN -> CallLinkState.Restrictions.UNKNOWN
  }
}
