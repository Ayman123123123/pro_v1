/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.v2

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.getSerializableCompat
import org.signal.donations.InAppPaymentType
import com.red.sovereign.MainActivity
import com.red.sovereign.R
import com.red.sovereign.badges.models.Badge
import com.red.sovereign.components.settings.app.subscription.donate.DonateToREDFragment
import com.red.sovereign.conversation.ConversationIntents
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.testing.InAppPaymentsRule
import com.red.sovereign.testing.REDActivityRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Entry-path coverage for the inline chat donation prompt: the Release-Channel "donation request"
 * conversation item that renders a "Donate" action button and opens the one-time checkout.
 *
 * The conversation is hosted by [MainActivity], so this drives it the same way
 * [com.red.sovereign.main.MainNavigationLaunchTest] does — a manual lifecycle-callback launch
 * plus direct view interaction, since ActivityScenario/Espresso misbehave for the conversation
 * custom-action intent.
 *
 * Unlike the megaphone and subscriber-upsell paths, this prompt is intentionally NOT gated by sustainer
 * status; [sustainer_donatePromptStillShown] documents that.
 */
@RunWith(AndroidJUnit4::class)
class InlineDonationPromptTest {

  @get:Rule
  val harness = REDActivityRule()

  @get:Rule
  val iapRule = InAppPaymentsRule()

  private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setUp() {
    REDStore.inAppPayments.setLastEndOfPeriod(0L)
    REDDatabase.recipients.setBadges(Recipient.self().id, emptyList())
    Recipient.self().fresh()
  }

  @Test
  fun nonSustainer_donatePromptOpensOneTimeCheckout() {
    seedDonationRequest()

    assertDonatePromptOpensCheckout()
  }

  @Test
  fun sustainer_donatePromptStillShown() {
    REDStore.inAppPayments.setLastEndOfPeriod(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TimeUnit.DAYS.toSeconds(30))
    REDDatabase.recipients.setBadges(Recipient.self().id, listOf(donorBadge()))
    Recipient.self().fresh()

    seedDonationRequest()

    assertDonatePromptOpensCheckout()
  }

  private fun assertDonatePromptOpensCheckout() {
    val activity = launchConversation()

    val donateButton = awaitView(activity, R.id.conversation_update_action)
    runOnMainSync { donateButton.performClick() }

    val dialog = await(description = "one-time checkout dialog") {
      activity.supportFragmentManager.findFragmentByTag(ONE_TIME_NAV_TAG)
    }

    assertThat(dialog).isInstanceOf(DonateToREDFragment.Dialog::class)
    val type = dialog.requireArguments().getSerializableCompat(DonateToREDFragment.Dialog.ARG, InAppPaymentType::class.java)
    assertThat(type).isEqualTo(InAppPaymentType.ONE_TIME_DONATION)
  }

  private fun seedDonationRequest() {
    val recipientId: RecipientId = REDDatabase.recipients.insertReleaseChannelRecipient()
    REDStore.releaseChannel.setReleaseChannelRecipientId(recipientId)

    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
    REDDatabase.messages.insertBoostRequestMessage(recipientId, threadId)
  }

  private fun launchConversation(): MainActivity {
    val recipientId = REDStore.releaseChannel.releaseChannelRecipientId!!
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))

    val conversationIntent = ConversationIntents.createBuilder(context, recipientId, threadId).blockingGet().build()
    val launchIntent = Intent(context, MainActivity::class.java).apply {
      action = ConversationIntents.ACTION
      putExtras(conversationIntent)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val app = context.applicationContext as Application
    val resumed = CountDownLatch(1)
    val holder = arrayOfNulls<MainActivity>(1)
    val callbacks = object : Application.ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

      // Capture on resume rather than create: a CLEAR_TOP|SINGLE_TOP conversation intent may be
      // delivered to an existing MainActivity via onNewIntent, which never fires onActivityCreated.
      override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) {
          holder[0] = activity
          resumed.countDown()
        }
      }

      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityPaused(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) = Unit
    }
    app.registerActivityLifecycleCallbacks(callbacks)

    app.startActivity(launchIntent)
    if (!resumed.await(15, TimeUnit.SECONDS)) {
      app.unregisterActivityLifecycleCallbacks(callbacks)
      error("MainActivity did not reach RESUMED within 15s")
    }
    app.unregisterActivityLifecycleCallbacks(callbacks)

    return holder[0] ?: error("MainActivity was not captured")
  }

  private fun awaitView(activity: FragmentActivity, viewId: Int): View {
    return await(description = "view $viewId") {
      activity.findViewById<View>(viewId)?.takeIf { it.isShown }
    }
  }

  private fun <T> await(
    timeoutMs: Long = 15_000,
    pollMs: Long = 50,
    description: String,
    supplier: () -> T?
  ): T {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val result = runOnMainSync(supplier)
      if (result != null) return result
      Thread.sleep(pollMs)
    }
    error("Timed out after ${timeoutMs}ms waiting for $description")
  }

  private fun <T> runOnMainSync(block: () -> T): T {
    var result: Result<T> = Result.failure(IllegalStateException("runOnMainSync produced no result"))
    InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
    return result.getOrThrow()
  }

  private fun donorBadge(): Badge {
    return Badge(
      id = "test-donor-badge",
      category = Badge.Category.Donor,
      name = "RED Sustainer",
      description = "",
      imageUrl = Uri.EMPTY,
      imageDensity = "xxhdpi",
      expirationTimestamp = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30),
      visible = true,
      duration = TimeUnit.DAYS.toMillis(30)
    )
  }

  companion object {
    private const val ONE_TIME_NAV_TAG = "one_time_nav"
  }
}
