/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.storage

import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertFalse
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import com.red.sovereign.service.webrtc.links.CallLinkCredentials
import com.red.sovereign.testutil.EmptyLogger
import org.whispersystems.signalservice.api.storage.REDCallLinkRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.CallLinkRecord

/**
 * See [CallLinkRecordProcessor]
 */
class CallLinkRecordProcessorTest {
  companion object {
    val STORAGE_ID: StorageId = StorageId.forCallLink(byteArrayOf(1, 2, 3, 4))

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  private val testSubject = CallLinkRecordProcessor()
  private val mockCredentials = CallLinkCredentials(
    "root key".toByteArray(),
    "admin pass".toByteArray()
  )

  @Test
  fun `Given a valid proto with only an admin pass key and not a deletion timestamp, assert valid`() {
    // GIVEN
    val proto = CallLinkRecord.Builder().apply {
      rootKey = mockCredentials.linkKeyBytes.toByteString()
      adminPasskey = mockCredentials.adminPassBytes!!.toByteString()
      deletedAtTimestampMs = 0L
    }.build()

    val record = REDCallLinkRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given a valid proto with only a deletion timestamp and not an admin pass key, assert valid`() {
    // GIVEN
    val proto = CallLinkRecord.Builder().apply {
      rootKey = mockCredentials.linkKeyBytes.toByteString()
      adminPasskey = EMPTY
      deletedAtTimestampMs = System.currentTimeMillis()
    }.build()

    val record = REDCallLinkRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `Given a proto with neither an admin pass key nor a deletion timestamp, assert valid`() {
    // GIVEN
    val proto = CallLinkRecord.Builder().apply {
      rootKey = mockCredentials.linkKeyBytes.toByteString()
      adminPasskey = EMPTY
      deletedAtTimestampMs = 0L
    }.build()

    val record = REDCallLinkRecord(STORAGE_ID, proto)

    // WHEN
    val result = testSubject.isInvalid(record)

    // THEN
    assertFalse(result)
  }
}
