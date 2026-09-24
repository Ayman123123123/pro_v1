package com.red.sovereign.components.settings.app.internal

import android.content.Context
import org.json.JSONObject
import org.signal.core.util.concurrent.REDExecutors
import org.signal.donations.InAppPaymentType
import com.red.sovereign.database.MessageTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.RemoteMegaphoneRecord
import com.red.sovereign.database.model.addButton
import com.red.sovereign.database.model.addLink
import com.red.sovereign.database.model.addStyle
import com.red.sovereign.database.model.databaseprotos.BodyRangeList
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.emoji.EmojiFiles
import com.red.sovereign.jobs.AttachmentDownloadJob
import com.red.sovereign.jobs.CreateReleaseChannelJob
import com.red.sovereign.jobs.FetchRemoteMegaphoneImageJob
import com.red.sovereign.jobs.InAppPaymentRecurringContextJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.notifications.v2.ConversationId
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.releasechannel.ReleaseChannel
import java.util.UUID
import kotlin.time.Duration.Companion.days

class InternalSettingsRepository(context: Context) {

  private val context = context.applicationContext

  fun getEmojiVersionInfo(consumer: (EmojiFiles.Version?) -> Unit) {
    REDExecutors.BOUNDED.execute {
      consumer(EmojiFiles.Version.readVersion(context))
    }
  }

  fun enqueueSubscriptionRedemption() {
    REDExecutors.BOUNDED.execute {
      val latest = REDDatabase.inAppPayments.getByLatestEndOfPeriod(InAppPaymentType.RECURRING_DONATION)
      if (latest != null) {
        InAppPaymentRecurringContextJob.createJobChain(latest).enqueue()
      }
    }
  }

  fun addSampleReleaseNote(callToAction: String) {
    REDExecutors.UNBOUNDED.execute {
      AppDependencies.jobManager.runSynchronously(CreateReleaseChannelJob.create(), 5000)

      val title = "Release Note Title"
      val bodyText = "Release note body. Aren't I awesome?"
      val linkUrl = "https://red.local"
      val body = "$title\n\n$bodyText\n\n$linkUrl"
      val linkStart = body.length - linkUrl.length
      val bodyRangeList = BodyRangeList.Builder()
        .addStyle(BodyRangeList.BodyRange.Style.BOLD, 0, title.length)
        .addLink(linkUrl, linkStart, linkUrl.length)

      bodyRangeList.addButton("Call to Action Text", callToAction, body.lastIndex, 0)

      val recipientId = REDStore.releaseChannel.releaseChannelRecipientId!!
      val threadId = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))

      val insertResult: MessageTable.InsertResult? = ReleaseChannel.insertReleaseChannelMessage(
        recipientId = recipientId,
        body = body,
        threadId = threadId,
        messageRanges = bodyRangeList.build(),
        media = "/static/release-notes/signal.png",
        mediaWidth = 1800,
        mediaHeight = 720
      )

      REDDatabase.messages.insertBoostRequestMessage(recipientId, threadId)

      if (insertResult != null) {
        REDDatabase.attachments.getAttachmentsForMessage(insertResult.messageId)
          .forEach { AppDependencies.jobManager.add(AttachmentDownloadJob(insertResult.messageId, it.attachmentId, false)) }

        AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
      }
    }
  }

  fun addRemoteMegaphone(actionId: RemoteMegaphoneRecord.ActionId) {
    REDExecutors.UNBOUNDED.execute {
      val record = RemoteMegaphoneRecord(
        uuid = UUID.randomUUID().toString(),
        priority = 100,
        countries = "*:1000000",
        minimumVersion = 1,
        doNotShowBefore = System.currentTimeMillis() - 2.days.inWholeMilliseconds,
        doNotShowAfter = System.currentTimeMillis() + 28.days.inWholeMilliseconds,
        showForNumberOfDays = 30,
        conditionalId = null,
        primaryActionId = actionId,
        secondaryActionId = RemoteMegaphoneRecord.ActionId.SNOOZE,
        imageUrl = "/static/release-notes/donate-heart.png",
        title = "Donate Test",
        body = "Donate body test.",
        primaryActionText = "Donate",
        secondaryActionText = "Snooze",
        primaryActionData = null,
        secondaryActionData = JSONObject("{ \"snoozeDurationDays\": [5, 7, 100] }")
      )

      REDDatabase.remoteMegaphones.insert(record)

      if (record.imageUrl != null) {
        AppDependencies.jobManager.add(FetchRemoteMegaphoneImageJob(record.uuid, record.imageUrl))
      }
    }
  }
}
