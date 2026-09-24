/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.testing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.junit.rules.ExternalResource
import org.signal.core.util.concurrent.REDDispatchers

/**
 * Rule that allows for injection of test dispatchers when operating with ViewModels.
 */
class CoroutineDispatcherRule(
  defaultDispatcher: TestDispatcher,
  mainDispatcher: TestDispatcher = defaultDispatcher,
  ioDispatcher: TestDispatcher = defaultDispatcher,
  unconfinedDispatcher: TestDispatcher = defaultDispatcher
) : ExternalResource() {

  private val testDispatcherProvider = TestDispatcherProvider(
    main = mainDispatcher,
    io = ioDispatcher,
    default = defaultDispatcher,
    unconfined = unconfinedDispatcher
  )

  override fun before() {
    REDDispatchers.setDispatcherProvider(testDispatcherProvider)
  }

  override fun after() {
    REDDispatchers.setDispatcherProvider()
  }

  private class TestDispatcherProvider(
    override val main: CoroutineDispatcher,
    override val io: CoroutineDispatcher,
    override val default: CoroutineDispatcher,
    override val unconfined: CoroutineDispatcher
  ) : REDDispatchers.DispatcherProvider
}
