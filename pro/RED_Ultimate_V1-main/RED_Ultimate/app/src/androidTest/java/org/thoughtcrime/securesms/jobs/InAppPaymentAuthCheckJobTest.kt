package com.red.sovereign.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEmpty
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import com.red.sovereign.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import com.red.sovereign.database.DonationReceiptTable
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.InAppPaymentReceiptRecord
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData
import com.red.sovereign.testing.REDActivityRule
import java.math.BigDecimal
import java.util.Currency

@RunWith(AndroidJUnit4::class)
class InAppPaymentAuthCheckJobTest {

  companion object {
    private const val TEST_INTENT_ID = "test-intent-id"
    private const val TEST_CLIENT_SECRET = "test-client-secret"
  }

  @get:Rule
  val harness = REDActivityRule()

  @Before
  fun setUp() {
    REDDatabase.inAppPayments.writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
    REDDatabase.donationReceipts.writableDatabase.deleteAll(DonationReceiptTable.TABLE_NAME)
  }

  @Test
  fun givenCanceledOneTimeAuthRequiredPayment_whenICheck_thenIDoNotExpectAReceipt() {
    REDDatabase.inAppPayments.insert(
      type = InAppPaymentType.ONE_TIME_DONATION,
      state = InAppPaymentTable.State.WAITING_FOR_AUTHORIZATION,
      subscriberId = null,
      endOfPeriod = null,
      inAppPaymentData = InAppPaymentData(
        amount = FiatMoney(BigDecimal.ONE, Currency.getInstance("USD")).toFiatValue(),
        waitForAuth = InAppPaymentData.WaitingForAuthorizationState(
          stripeIntentId = TEST_INTENT_ID,
          stripeClientSecret = TEST_CLIENT_SECRET
        )
      )
    )

    InAppPaymentAuthCheckJob().run()

    val receipts = REDDatabase.donationReceipts.getReceipts(InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION)
    assertThat(receipts).isEmpty()
  }
}
