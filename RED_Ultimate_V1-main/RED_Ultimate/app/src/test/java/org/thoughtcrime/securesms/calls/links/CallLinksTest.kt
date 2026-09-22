/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.calls.links

import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import com.red.sovereign.testutil.EmptyLogger

/**
 * See [CallLinks]
 */
class CallLinksTest {
  companion object {
    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }

  @Test
  fun `parseUrl returns null for malformed percent escape instead of throwing`() {
    assertNull(CallLinks.parseUrl("https://signal.link/call/#key=abcdef&n=%ZZ"))
  }

  @Test
  fun `parseUrl returns null for malformed percent escape in key instead of throwing`() {
    assertNull(CallLinks.parseUrl("https://signal.link/call/#key=%ZZ"))
  }
}
