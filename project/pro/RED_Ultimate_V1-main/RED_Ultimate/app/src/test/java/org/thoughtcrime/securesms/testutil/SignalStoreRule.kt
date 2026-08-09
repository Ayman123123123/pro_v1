/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.testutil

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import com.red.sovereign.keyvalue.KeyValueDataSet
import com.red.sovereign.keyvalue.KeyValueStore
import com.red.sovereign.keyvalue.MockKeyValuePersistentStorage
import com.red.sovereign.keyvalue.REDStore

/**
 * Installs a real [REDStore] backed by in-memory storage, letting tests arrange and assert on actual
 * stored state rather than stubbing interactions. Use [MockREDStoreRule] instead when you need
 * interaction-based mocking.
 */
class REDStoreRule : ExternalResource() {

  override fun before() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    REDStore.testInject(REDStore(application, KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet()))))
  }

  override fun after() {
    REDStore.testInject(null)
  }
}
