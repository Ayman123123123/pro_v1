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
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsTestRule
import com.red.sovereign.testutil.MockREDStoreRule

class InAppPaymentOneTimeContextJobTest {

  @get:Rule
  val mockREDStore = MockREDStoreRule()

  @get:Rule
  val iapRule = InAppPaymentsTestRule()

  @Test
  fun `Given an unregistered local user, when I run, then I expect failure`() {
    every { mockREDStore.account.isRegistered } returns false

    val job = InAppPaymentOneTimeContextJob.create(iapRule.createInAppPayment(InAppPaymentType.ONE_TIME_DONATION, PaymentSourceType.PayPal))

    val result = job.run()

    assertThat(result.isFailure).isTrue()
  }
}
