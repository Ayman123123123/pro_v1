package com.red.sovereign.conversation

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.red.sovereign.contacts.paged.ContactSearchKey
import com.red.sovereign.conversation.v2.ConversationActivity
import com.red.sovereign.database.IdentityTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.DistributionListId
import com.red.sovereign.database.model.DistributionListPrivacyMode
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.profiles.ProfileName
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.safety.SafetyNumberBottomSheet
import com.red.sovereign.testing.REDActivityRule

/**
 * Android test to help show SNC dialog quickly with custom data to make sure it displays properly.
 */
@Ignore("For testing/previewing manually, no assertions")
@RunWith(AndroidJUnit4::class)
class SafetyNumberChangeDialogPreviewer {

  @get:Rule val harness = REDActivityRule(othersCount = 10)

  @Test
  fun testShowLongName() {
    val other: Recipient = Recipient.resolved(harness.others.first())

    REDDatabase.recipients.setProfileName(other.id, ProfileName.fromParts("Super really long name like omg", "But seriously it's long like really really long"))

    harness.setVerified(other, IdentityTable.VerifiedStatus.VERIFIED)
    harness.changeIdentityKey(other)

    val scenario: ActivityScenario<ConversationActivity> = harness.launchActivity { putExtra("recipient_id", other.id.serialize()) }
    scenario.onActivity {
      SafetyNumberBottomSheet.forRecipientId(other.id).show(it.supportFragmentManager)
    }

    // Uncomment to make dialog stay on screen, otherwise will show/dismiss immediately
    // ThreadUtil.sleep(15000)
  }

  @Test
  fun testShowLargeSheet() {
    REDDatabase.distributionLists.setPrivacyMode(DistributionListId.MY_STORY, DistributionListPrivacyMode.ONLY_WITH)

    val othersRecipients = harness.others.map { Recipient.resolved(it) }
    othersRecipients.forEach { other ->
      REDDatabase.recipients.setProfileName(other.id, ProfileName.fromParts("My", "Name"))

      harness.setVerified(other, IdentityTable.VerifiedStatus.DEFAULT)
      harness.changeIdentityKey(other)

      REDDatabase.distributionLists.addMemberToList(DistributionListId.MY_STORY, DistributionListPrivacyMode.ONLY_WITH, other.id)
    }

    val myStoryRecipientId = REDDatabase.distributionLists.getRecipientId(DistributionListId.MY_STORY)!!
    val scenario: ActivityScenario<ConversationActivity> = harness.launchActivity { putExtra("recipient_id", harness.others.first().serialize()) }
    scenario.onActivity { conversationActivity ->
      SafetyNumberBottomSheet
        .forIdentityRecordsAndDestinations(
          identityRecords = AppDependencies.protocolStore.aci().identities().getIdentityRecords(othersRecipients).identityRecords,
          destinations = listOf(ContactSearchKey.RecipientSearchKey(myStoryRecipientId, true))
        )
        .show(conversationActivity.supportFragmentManager)
    }

    // Uncomment to make dialog stay on screen, otherwise will show/dismiss immediately
    // ThreadUtil.sleep( 30000)
  }
}
