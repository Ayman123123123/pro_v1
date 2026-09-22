/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.messages.protocol

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.libsignal.protocol.ReusedBaseKeyException
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.testing.REDDatabaseRule
import com.red.sovereign.util.KyberPreKeysTestUtil

class BufferedKyberPreKeyStoreTest {

  @get:Rule
  val harness = REDDatabaseRule()

  private lateinit var aci: ServiceId
  private lateinit var testSubject: BufferedKyberPreKeyStore
  private lateinit var dataStore: BufferedREDServiceAccountDataStore

  @Before
  fun setUp() {
    REDStore.account.generateAciIdentityKeyIfNecessary()

    aci = harness.localAci
    testSubject = BufferedKyberPreKeyStore(aci)
    dataStore = BufferedREDServiceAccountDataStore(aci)
  }

  @Test
  fun givenALastResortKey_whenIMarkKyberPreKeyUsed_thenIExpectNoIssues() {
    KyberPreKeysTestUtil.insertTestRecord(aci, 1, lastResort = true)
    val publicKey = KyberPreKeysTestUtil.generateECPublicKey()

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )
  }

  @Test(expected = ReusedBaseKeyException::class)
  fun givenALastResortKey_whenIMarkKyberPreKeyUsedTwice_thenIExpectException() {
    KyberPreKeysTestUtil.insertTestRecord(aci, 1, lastResort = true)
    val publicKey = KyberPreKeysTestUtil.generateECPublicKey()

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )
  }

  @Test
  fun givenAMarkedLastResortKey_whenIFlushTwice_thenIExpectNoIssues() {
    KyberPreKeysTestUtil.insertTestRecord(aci, 1, lastResort = true)
    val publicKey = KyberPreKeysTestUtil.generateECPublicKey()

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )

    testSubject.flushToDisk(dataStore)
    testSubject.flushToDisk(dataStore)
  }
}
