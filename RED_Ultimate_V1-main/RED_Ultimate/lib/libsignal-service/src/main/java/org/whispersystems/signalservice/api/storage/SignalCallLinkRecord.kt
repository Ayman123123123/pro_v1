/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.CallLinkRecord
import java.io.IOException

/**
 * Wrapper around a [CallLinkRecord] to pair it with a [StorageId].
 */
data class REDCallLinkRecord(
  override val id: StorageId,
  override val proto: CallLinkRecord
) : REDRecord<CallLinkRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): CallLinkRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: CallLinkRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): CallLinkRecord.Builder {
      return try {
        CallLinkRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        CallLinkRecord.Builder()
      }
    }
  }
}
