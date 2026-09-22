/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.subscription

import android.app.Application
import io.mockk.every
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.InAppPaymentKeepAliveJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.testutil.MockAppDependenciesRule
import com.red.sovereign.testutil.MockREDStoreRule
import com.red.sovereign.testutil.SystemOutLogger
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.util.Currency
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Focused on the keep-alive enqueue decision inside [RecurringInAppPaymentRepository.getActiveSubscriptionSync].
 *
 * The decision must be gated on the type-scoped last end-of-period (recorded per-type in the database), not the
 * donation-only [REDStore.inAppPayments] watermark. Previously the comparison always used the donation watermark,
 * which for a BACKUP subscription never advances (it stays at 0 for backup-only users), so the guard never latched
 * and a permit-less keep-alive was enqueued on every subscription status check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecurringInAppPaymentRepositoryKeepAliveTest {

  @get:Rule
  val mockREDStore = MockREDStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val inAppPaymentsTestRule = InAppPaymentsTestRule()

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    InAppPaymentsTestRule.mockLocalSubscriberAccess(subscriber())

    every { mockREDStore.account.isLinkedDevice } returns false
    every { REDStore.backup.backupTierInternalOverride } returns null
    every { REDStore.inAppPayments.setLastKeepAliveLaunchTime(any()) } returns Unit
  }

  @Test
  fun `Given a backup subscription whose period has not advanced, when I getActiveSubscription, then I do not enqueue a keep-alive`() {
    val endOfCurrentPeriod = setUpActiveSubscription()

    // Donation watermark stays at zero for a backup-only user, but the backup's own last end-of-period matches.
    every { REDStore.inAppPayments.getLastEndOfPeriod() } returns 0L
    stubLatestEndOfPeriod(InAppPaymentType.RECURRING_BACKUP, endOfCurrentPeriod.seconds)

    RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP)

    verify(exactly = 0) { AppDependencies.jobManager.add(any()) }
  }

  @Test
  fun `Given a backup subscription whose period has advanced, when I getActiveSubscription, then I enqueue a keep-alive`() {
    val endOfCurrentPeriod = setUpActiveSubscription()

    every { REDStore.inAppPayments.getLastEndOfPeriod() } returns 0L
    stubLatestEndOfPeriod(InAppPaymentType.RECURRING_BACKUP, endOfCurrentPeriod.seconds - 30.days)

    RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP)

    verify(atLeast = 1) { AppDependencies.jobManager.add(ofType(InAppPaymentKeepAliveJob::class)) }
  }

  @Test
  fun `Given a donation subscription whose period has not advanced, when I getActiveSubscription, then I do not enqueue a keep-alive`() {
    val endOfCurrentPeriod = setUpActiveSubscription()

    every { REDStore.inAppPayments.getLastEndOfPeriod() } returns endOfCurrentPeriod
    stubLatestEndOfPeriod(InAppPaymentType.RECURRING_DONATION, endOfCurrentPeriod.seconds)

    RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.DONATION)

    verify(exactly = 0) { AppDependencies.jobManager.add(any()) }
  }

  @Test
  fun `Given a donation subscription whose period has advanced, when I getActiveSubscription, then I enqueue a keep-alive`() {
    val endOfCurrentPeriod = setUpActiveSubscription()

    every { REDStore.inAppPayments.getLastEndOfPeriod() } returns endOfCurrentPeriod - 30.days.inWholeSeconds
    stubLatestEndOfPeriod(InAppPaymentType.RECURRING_DONATION, endOfCurrentPeriod.seconds - 30.days)

    RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.DONATION)

    verify(atLeast = 1) { AppDependencies.jobManager.add(ofType(InAppPaymentKeepAliveJob::class)) }
  }

  /**
   * Stubs [AppDependencies.donationsService] to return an active subscription and returns its end-of-current-period in seconds.
   */
  private fun setUpActiveSubscription(): Long {
    val activeSubscription = inAppPaymentsTestRule.createActiveSubscription(isActive = true)
    inAppPaymentsTestRule.initializeActiveSubscriptionMock(activeSubscription = activeSubscription)
    return activeSubscription.activeSubscription!!.endOfCurrentPeriod
  }

  private fun stubLatestEndOfPeriod(type: InAppPaymentType, endOfPeriod: kotlin.time.Duration) {
    every { REDDatabase.inAppPayments.getByLatestEndOfPeriod(type) } returns
      inAppPaymentsTestRule.createInAppPayment(type, PaymentSourceType.Stripe.CreditCard)
        .copy(endOfPeriod = endOfPeriod)
  }

  private fun subscriber(): InAppPaymentSubscriberRecord {
    return InAppPaymentSubscriberRecord(
      subscriberId = SubscriberId.generate(),
      type = InAppPaymentSubscriberRecord.Type.BACKUP,
      requiresCancel = false,
      paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
      currency = Currency.getInstance("USD"),
      iapSubscriptionId = null
    )
  }
}
