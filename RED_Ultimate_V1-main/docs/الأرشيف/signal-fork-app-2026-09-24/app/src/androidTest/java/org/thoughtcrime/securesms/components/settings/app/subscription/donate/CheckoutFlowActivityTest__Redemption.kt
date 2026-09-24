package com.red.sovereign.components.settings.app.subscription.donate

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isNotEmpty
import io.mockk.unmockkObject
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.deleteAll
import org.signal.donations.InAppPaymentType
import com.red.sovereign.R
import com.red.sovereign.components.settings.app.subscription.permits.DonationPermits
import com.red.sovereign.database.DonationReceiptTable
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.InAppPaymentReceiptRecord
import com.red.sovereign.testing.DisableAnimationsRule
import com.red.sovereign.testing.GooglePayTestRule
import com.red.sovereign.testing.InAppPaymentsRule
import com.red.sovereign.testing.RxTestSchedulerRule
import com.red.sovereign.testing.REDActivityRule
import com.red.sovereign.testing.actions.scrollToDescendant
import com.red.sovereign.testing.endpoints.MockEndpoints
import com.red.sovereign.testing.endpoints.registerStripeHappyPath
import com.red.sovereign.testing.flushUntil

/**
 * Milestone integration test: drives a Google Pay one-time donation through the entire lifecycle to
 * receipt redemption, exercising all three outer boundaries at once — the RED-service websocket
 * responder (boost intent + receipt credential + redeem), the Stripe HTTP interceptor (payment
 * method + confirm + intent status), and the test zk server (real client-side credential validation).
 */
@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class CheckoutFlowActivityTest__Redemption {
  @get:Rule
  val harness = REDActivityRule(othersCount = 10)

  @get:Rule
  val iapRule = InAppPaymentsRule()

  @get:Rule
  val rxRule = RxTestSchedulerRule()

  @get:Rule
  val googlePayRule = GooglePayTestRule()

  @get:Rule
  val composeRule = createEmptyComposeRule()

  @get:Rule
  val animationsRule = DisableAnimationsRule()

  private val intent = CheckoutFlowActivity.createIntent(InstrumentationRegistry.getInstrumentation().targetContext, InAppPaymentType.ONE_TIME_DONATION)

  @Before
  fun setUp() {
    REDDatabase.inAppPayments.writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
    REDDatabase.donationReceipts.writableDatabase.deleteAll(DonationReceiptTable.TABLE_NAME)
    startJobLoopForTests()
    succeedDonationPermitAcquisition()
    MockEndpoints.responder.registerStripeHappyPath()
  }

  @After
  fun tearDown() {
    unmockkObject(DonationPermits)
  }

  @Test
  fun givenAllEndpointsSucceed_whenIDonateOnceWithGooglePay_thenIExpectAReceipt() {
    val scenario = ActivityScenario.launch<CheckoutFlowActivity>(intent)
    rxRule.defaultTestScheduler.triggerActions()

    scrollToDescendant(R.id.recycler, withId(R.id.boost_1), rxRule.defaultTestScheduler)
    onView(allOf(withId(R.id.boost_1), isClickable())).perform(ViewActions.click())
    rxRule.defaultTestScheduler.triggerActions()

    scrollToDescendant(R.id.recycler, withText(R.string.DonateToREDFragment__continue), rxRule.defaultTestScheduler)
    onView(withText(R.string.DonateToREDFragment__continue)).perform(ViewActions.click())
    rxRule.defaultTestScheduler.triggerActions()

    scenario.selectGooglePay(composeRule, rxRule.defaultTestScheduler, InAppPaymentType.ONE_TIME_DONATION)

    rxRule.defaultTestScheduler.flushUntil {
      REDDatabase.donationReceipts.getReceipts(InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION).isNotEmpty()
    }

    assertThat(REDDatabase.donationReceipts.getReceipts(InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION)).isNotEmpty()
  }
}
