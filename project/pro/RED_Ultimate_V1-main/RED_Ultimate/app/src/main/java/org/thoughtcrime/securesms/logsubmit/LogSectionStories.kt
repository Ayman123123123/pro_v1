package com.red.sovereign.logsubmit

import android.content.Context
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.DistributionListId
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import org.whispersystems.signalservice.api.push.DistributionId

/**
 * Prints off the current SMS settings
 */

class LogSectionStories : LogSection {
  override fun getTitle(): String = "STORIES"

  override fun getContent(context: Context): CharSequence {
    val output = StringBuilder()
    output.append("--- My Story\n\n")

    if (Recipient.isSelfSet) {
      val myStoryRecord = REDDatabase.distributionLists.getList(DistributionListId.MY_STORY)
      val myStoryRecipientId = REDDatabase.distributionLists.getRecipientId(DistributionListId.MY_STORY)

      if (myStoryRecord != null) {
        output.append("Database ID        : ${myStoryRecord.id}\n")
        output.append("Distribution ID    : ${myStoryRecord.distributionId} (Matches expected value? ${myStoryRecord.distributionId == DistributionId.MY_STORY})\n")
        output.append("Raw Distribution ID: ${REDDatabase.distributionLists.getRawDistributionId(myStoryRecord.id)}\n")
        output.append("Recipient ID       : ${presentRecipientId(myStoryRecipientId)}\n")
        output.append("toString() Test    : ${DistributionId.MY_STORY} | ${DistributionId.MY_STORY.asUuid()}")
      } else {
        output.append("< My story does not exist >\n")
      }
    } else {
      output.append("< Self is not set yet, my story does not exist >\n")
    }

    return output
  }

  private fun presentRecipientId(recipientId: RecipientId?): String {
    return recipientId?.serialize() ?: "Not set"
  }
}
