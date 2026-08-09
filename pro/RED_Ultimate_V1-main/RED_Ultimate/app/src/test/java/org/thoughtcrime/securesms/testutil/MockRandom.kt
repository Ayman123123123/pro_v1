/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.testutil

import java.util.LinkedList
import java.util.Random

class MockRandom(initialInts: List<Int>) : Random() {

  val nextInts = LinkedList(initialInts)

  override fun nextInt(): Int {
    return nextInts.remove()
  }

  override fun nextInt(bound: Int): Int {
    return nextInts.remove() % bound
  }
}
