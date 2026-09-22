/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import assertk.assertThat
import assertk.assertions.isTrue
import io.mockk.every
import org.junit.Rule
import org.junit.Test
import com.red.sovereign.testutil.MockREDStoreRule

class InAppPaymentRedemptionJobTest {

  @get:Rule
  val mockREDStore = MockREDStoreRule()

  @Test
  fun `Given an unregistered local user, when I run, then I expect failure`() {
    every { mockREDStore.account.isRegistered } returns false

    val job = InAppPaymentRedemptionJob.create()

    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }
}
