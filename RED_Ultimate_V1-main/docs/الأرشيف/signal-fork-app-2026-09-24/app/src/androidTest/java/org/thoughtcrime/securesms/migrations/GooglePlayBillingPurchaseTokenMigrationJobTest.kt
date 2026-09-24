package com.red.sovereign.migrations

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.billing.BillingPurchaseResult
import org.signal.core.util.billing.BillingPurchaseState
import org.signal.core.util.billing.BillingResponseCode
import org.signal.core.util.deleteAll
import com.red.sovereign.database.InAppPaymentSubscriberTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.testing.REDActivityRule
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.api.subscriptions.SubscriberId

@RunWith(AndroidJUnit4::class)
class GooglePlayBillingPurchaseTokenMigrationJobTest {
  @get:Rule
  val harness = REDActivityRule()

  @Before
  fun setUp() {
    REDDatabase.inAppPaymentSubscribers.writableDatabase.deleteAll(InAppPaymentSubscriberTable.TABLE_NAME)
  }

  @Test
  fun givenNoSubscribers_whenIRunJob_thenIExpectNoBillingAccess() {
    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    verify { AppDependencies.billingApi wasNot Called }
  }

  @Test
  fun givenSubscriberWithAppleData_whenIRunJob_thenIExpectNoBillingAccess() {
    REDDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.AppleIAPOriginalTransactionId(1000L)
      )
    )

    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    verify { AppDependencies.billingApi wasNot Called }
  }

  @Test
  fun givenSubscriberWithGoogleToken_whenIRunJob_thenIExpectNoBillingAccess() {
    REDDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("testToken")
      )
    )

    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    verify { AppDependencies.billingApi wasNot Called }
  }

  @Test
  fun givenSubscriberWithPlaceholderAndNoBillingAccess_whenIRunJob_thenIExpectNoUpdate() {
    REDDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("-")
      )
    )

    coEvery { AppDependencies.billingApi.getApiAvailability() } returns BillingResponseCode.BILLING_UNAVAILABLE

    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    val sub = REDDatabase.inAppPaymentSubscribers.getBackupsSubscriber()

    assertThat(sub?.iapSubscriptionId?.purchaseToken).isEqualTo("-")
  }

  @Test
  fun givenSubscriberWithPlaceholderAndNoPurchase_whenIRunJob_thenIExpectNoUpdate() {
    REDDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("-")
      )
    )

    coEvery { AppDependencies.billingApi.getApiAvailability() } returns BillingResponseCode.OK
    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.None

    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    val sub = REDDatabase.inAppPaymentSubscribers.getBackupsSubscriber()

    assertThat(sub?.iapSubscriptionId?.purchaseToken).isEqualTo("-")
  }

  @Test
  fun givenSubscriberWithPurchase_whenIRunJob_thenIExpectUpdate() {
    REDDatabase.inAppPaymentSubscribers.insertOrReplace(
      InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.generate(),
        currency = null,
        type = InAppPaymentSubscriberRecord.Type.BACKUP,
        requiresCancel = false,
        paymentMethodType = InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING,
        iapSubscriptionId = IAPSubscriptionId.GooglePlayBillingPurchaseToken("-")
      )
    )

    coEvery { AppDependencies.billingApi.getApiAvailability() } returns BillingResponseCode.OK
    coEvery { AppDependencies.billingApi.queryPurchases() } returns BillingPurchaseResult.Success(
      purchaseState = BillingPurchaseState.PURCHASED,
      purchaseToken = "purchaseToken",
      isAcknowledged = true,
      purchaseTime = System.currentTimeMillis(),
      isAutoRenewing = true
    )

    val job = GooglePlayBillingPurchaseTokenMigrationJob()

    job.run()

    val sub = REDDatabase.inAppPaymentSubscribers.getBackupsSubscriber()

    assertThat(sub?.iapSubscriptionId?.purchaseToken).isEqualTo("purchaseToken")
  }
}
