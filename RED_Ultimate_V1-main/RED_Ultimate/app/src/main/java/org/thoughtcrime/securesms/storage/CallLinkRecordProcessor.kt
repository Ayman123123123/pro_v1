/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.storage

import okio.ByteString.Companion.toByteString
import org.signal.core.util.isNotEmpty
import org.signal.core.util.logging.Log
import org.signal.core.util.toOptional
import org.signal.ringrtc.CallLinkRootKey
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.service.webrtc.links.CallLinkRoomId
import org.whispersystems.signalservice.api.storage.REDCallLinkRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.toREDCallLinkRecord
import java.util.Optional

/**
 * Record processor for [REDCallLinkRecord].
 * Handles merging and updating our local store when processing remote call link storage records.
 */
class CallLinkRecordProcessor : DefaultStorageRecordProcessor<REDCallLinkRecord>() {

  companion object {
    private val TAG = Log.tag(CallLinkRecordProcessor::class)
  }

  override fun compare(o1: REDCallLinkRecord?, o2: REDCallLinkRecord?): Int {
    return if (o1?.proto?.rootKey == o2?.proto?.rootKey) {
      0
    } else {
      1
    }
  }

  override fun isInvalid(remote: REDCallLinkRecord): Boolean {
    return remote.proto.adminPasskey.isNotEmpty() && remote.proto.deletedAtTimestampMs > 0L
  }

  override fun getMatching(remote: REDCallLinkRecord, keyGenerator: StorageKeyGenerator): Optional<REDCallLinkRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val callRootKey = CallLinkRootKey(remote.proto.rootKey.toByteArray())
    val roomId = CallLinkRoomId.fromCallLinkRootKey(callRootKey)
    val callLink = REDDatabase.callLinks.getCallLinkByRoomId(roomId)

    if (callLink != null && callLink.credentials?.adminPassBytes != null) {
      return REDCallLinkRecord.newBuilder(null).apply {
        rootKey = callRootKey.keyBytes.toByteString()
        adminPasskey = callLink.credentials.adminPassBytes.toByteString()
        deletedAtTimestampMs = callLink.deletionTimestamp
      }.build().toREDCallLinkRecord(StorageId.forCallLink(keyGenerator.generate())).toOptional()
    } else {
      return Optional.empty<REDCallLinkRecord>()
    }
  }

  /**
   * A deleted record takes precedence over a non-deleted record
   * An earlier deletion takes precedence over a later deletion
   * Other fields should not change, except for the clearing of the admin passkey on deletion
   */
  override fun merge(remote: REDCallLinkRecord, local: REDCallLinkRecord, keyGenerator: StorageKeyGenerator): REDCallLinkRecord {
    return if (remote.proto.deletedAtTimestampMs > 0 && local.proto.deletedAtTimestampMs > 0) {
      if (remote.proto.deletedAtTimestampMs < local.proto.deletedAtTimestampMs) {
        remote
      } else {
        local
      }
    } else if (remote.proto.deletedAtTimestampMs > 0) {
      remote
    } else if (local.proto.deletedAtTimestampMs > 0) {
      local
    } else {
      remote
    }
  }

  override fun insertLocal(record: REDCallLinkRecord) {
    insertOrUpdateRecord(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<REDCallLinkRecord>) {
    insertOrUpdateRecord(update.new)
  }

  private fun insertOrUpdateRecord(record: REDCallLinkRecord) {
    val rootKey = CallLinkRootKey(record.proto.rootKey.toByteArray())

    REDDatabase.callLinks.insertOrUpdateCallLinkByRootKey(
      callLinkRootKey = rootKey,
      adminPassKey = record.proto.adminPasskey.toByteArray(),
      deletionTimestamp = record.proto.deletedAtTimestampMs,
      storageId = record.id
    )
  }
}
