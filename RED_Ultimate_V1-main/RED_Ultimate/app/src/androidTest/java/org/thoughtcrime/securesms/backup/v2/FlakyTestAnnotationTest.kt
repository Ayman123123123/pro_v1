/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.red.sovereign.testing.REDFlakyTest
import com.red.sovereign.testing.REDFlakyTestRule

@RunWith(AndroidJUnit4::class)
class FlakyTestAnnotationTest {

  @get:Rule
  val flakyTestRule = REDFlakyTestRule()

  companion object {
    private var count = 0
  }

  @REDFlakyTest
  @Test
  fun purposelyFlaky() {
    count++
    assertEquals(3, count)
  }
}
