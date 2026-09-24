package com.red.sovereign.migrations

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.count
import org.signal.core.util.readToSingleInt
import org.signal.donations.PaymentSourceType
import com.red.sovereign.database.InAppPaymentSubscriberTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData
import com.red.sovereign.keyvalue.REDStore
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.util.Currency

@RunWith(AndroidJUnit4::class)
class SubscriberIdMigrationJobTest {

  private val testSubject = SubscriberIdMigrationJob()

  @Test
  fun givenNoSubscriber_whenIRunSubscriberIdMigrationJob_thenIExpectNoDatabaseEntries() {
    testSubject.run()

    val actual = REDDatabase.inAppPaymentSubscribers.readableDatabase.count()
      .from(InAppPaymentSubscriberTable.TABLE_NAME)
      .run()
      .readToSingleInt()

    assertThat(actual).isEqualTo(0)
  }

  @Test
  fun givenUSDSubscriber_whenIRunSubscriberIdMigrationJob_thenIExpectASingleEntry() {
    val subscriberId = SubscriberId.generate()
    REDStore.inAppPayments.setRecurringDonationCurrency(Currency.getInstance("USD"))
    REDStore.inAppPayments.setSubscriber("USD", subscriberId)
    REDStore.inAppPayments.setSubscriptionPaymentSourceType(PaymentSourceType.PayPal)
    REDStore.inAppPayments.shouldCancelSubscriptionBeforeNextSubscribeAttempt = true

    testSubject.run()

    val actual = REDDatabase.inAppPaymentSubscribers.getByCurrencyCode("USD")

    assertThat(actual)
      .isNotNull()
      .given {
        assertThat(it.subscriberId.bytes).isEqualTo(subscriberId.bytes)
        assertThat(it.paymentMethodType).isEqualTo(InAppPaymentData.PaymentMethodType.PAYPAL)
        assertThat(it.requiresCancel).isTrue()
        assertThat(it.currency).isEqualTo(Currency.getInstance("USD"))
        assertThat(it.type).isEqualTo(InAppPaymentSubscriberRecord.Type.DONATION)
      }
  }
}
