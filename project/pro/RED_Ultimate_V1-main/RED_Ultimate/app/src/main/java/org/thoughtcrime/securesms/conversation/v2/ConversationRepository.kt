/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.conversation.v2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.SpannableStringBuilder
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.DrawableUtil
import org.signal.core.util.StreamUtil
import org.signal.core.util.Util
import org.signal.core.util.concurrent.MaybeCompat
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.dp
import org.signal.core.util.logging.Log
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import com.red.sovereign.R
import com.red.sovereign.ShortcutLauncherActivity
import com.red.sovereign.attachments.TombstoneAttachment
import com.red.sovereign.avatar.fallback.FallbackAvatarDrawable
import com.red.sovereign.components.emoji.EmojiStrings
import com.red.sovereign.contactshare.Contact
import com.red.sovereign.contactshare.ContactUtil
import com.red.sovereign.conversation.ConversationMessage
import com.red.sovereign.conversation.mutiselect.MultiselectPart
import com.red.sovereign.conversation.v2.RequestReviewState.GroupReviewState
import com.red.sovereign.conversation.v2.RequestReviewState.IndividualReviewState
import com.red.sovereign.conversation.v2.data.ConversationDataSource
import com.red.sovereign.crypto.ProfileKeyUtil
import com.red.sovereign.crypto.ReentrantSessionLock
import com.red.sovereign.database.GroupTable
import com.red.sovereign.database.IdentityTable.VerifiedStatus
import com.red.sovereign.database.MessageTable
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.RxDatabaseObserver
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.REDDatabase.Companion.attachments
import com.red.sovereign.database.REDDatabase.Companion.recipients
import com.red.sovereign.database.model.GroupRecord
import com.red.sovereign.database.model.IdentityRecord
import com.red.sovereign.database.model.Mention
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.model.ReactionRecord
import com.red.sovereign.database.model.StickerRecord
import com.red.sovereign.database.model.databaseprotos.BodyRangeList
import com.red.sovereign.database.model.databaseprotos.MessageExtras
import com.red.sovereign.database.model.databaseprotos.PinnedMessage
import com.red.sovereign.database.model.databaseprotos.PollTerminate
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.dependencies.AppDependencies.databaseObserver
import com.red.sovereign.dependencies.AppDependencies.expiringMessageManager
import com.red.sovereign.groups.GroupNotAMemberException
import com.red.sovereign.jobs.GroupSendJobHelper
import com.red.sovereign.jobs.MultiDeviceViewOnceOpenJob
import com.red.sovereign.jobs.UnpinMessageJob
import com.red.sovereign.keyboard.KeyboardUtil
import com.red.sovereign.keyvalue.REDStore.Companion.settings
import com.red.sovereign.linkpreview.LinkPreview
import com.red.sovereign.messagerequests.MessageRequestState
import com.red.sovereign.messages.GroupSendUtil
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.mms.PartAuthority
import com.red.sovereign.mms.QuoteModel
import com.red.sovereign.mms.Slide
import com.red.sovereign.mms.SlideDeck
import com.red.sovereign.polls.Poll
import com.red.sovereign.profiles.spoofing.ReviewRecipient
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.recipients.RecipientUtil
import com.red.sovereign.sms.MessageSender
import com.red.sovereign.sms.MessageSender.PreUploadResult
import com.red.sovereign.transport.UndeliverableMessageException
import com.red.sovereign.util.AdaptiveBitmapMetrics
import com.red.sovereign.util.GroupUtil
import com.red.sovereign.util.MediaUtil
import com.red.sovereign.util.MessageUtil
import com.red.sovereign.util.REDLocalMetrics
import com.red.sovereign.util.hasLinkPreview
import com.red.sovereign.util.hasSharedContact
import com.red.sovereign.util.hasTextSlide
import com.red.sovereign.util.isViewOnceMessage
import com.red.sovereign.util.requireTextSlide
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.messages.SendMessageResult
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage.Companion.newBuilder
import java.io.IOException
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConversationRepository(
  private val localContext: Context,
  val isInBubble: Boolean
) {

  companion object {
    private val TAG = Log.tag(ConversationRepository::class.java)
    private val POLL_TERMINATE_TIMEOUT = 6000.milliseconds
  }

  private val applicationContext = localContext.applicationContext
  private val oldConversationRepository = com.red.sovereign.conversation.ConversationRepository()

  /**
   * Gets image details for an image sent from the keyboard
   */
  fun getKeyboardImageDetails(uri: Uri): Maybe<KeyboardUtil.ImageDetails> {
    return MaybeCompat.fromCallable {
      KeyboardUtil.getImageDetails(uri)
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Loads the details necessary to display the conversation thread.
   */
  fun getConversationThreadState(threadId: Long, requestedStartPosition: Int): Single<ConversationThreadState> {
    return Single.fromCallable {
      val recipient = REDDatabase.threads.getRecipientForThreadId(threadId)!!

      REDLocalMetrics.ConversationOpen.onMetadataLoadStarted()
      val metadata = oldConversationRepository.getConversationData(threadId, recipient, requestedStartPosition)
      REDLocalMetrics.ConversationOpen.onMetadataLoaded()

      val messageRequestData = metadata.messageRequestData
      val dataSource = ConversationDataSource(
        localContext,
        threadId,
        messageRequestData,
        metadata.showUniversalExpireTimerMessage,
        metadata.threadSize
      )
      val config = PagingConfig.Builder().setPageSize(25)
        .setBufferPages(3)
        .setStartIndex(max(metadata.getStartPosition(), 0))
        .build()

      ConversationThreadState(
        items = PagedData.createForObservable(dataSource, config),
        meta = metadata
      )
    }.subscribeOn(Schedulers.io())
  }

  fun sendReactionRemoval(messageRecord: MessageRecord, oldRecord: ReactionRecord): Completable {
    return Completable.fromAction {
      MessageSender.sendReactionRemoval(
        applicationContext,
        MessageId(messageRecord.id),
        oldRecord
      )
    }.subscribeOn(Schedulers.io())
  }

  fun sendNewReaction(messageRecord: MessageRecord, emoji: String): Completable {
    return Completable.fromAction {
      MessageSender.sendNewReaction(
        applicationContext,
        MessageId(messageRecord.id),
        emoji
      )
    }.subscribeOn(Schedulers.io())
  }

  fun sendPoll(threadRecipient: Recipient, poll: Poll): Completable {
    return Completable.create { emitter ->

      val threadId = REDDatabase.threads.getOrCreateThreadIdFor(threadRecipient)
      val message = OutgoingMessage.pollMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = System.currentTimeMillis(),
        expiresIn = threadRecipient.expiresInSeconds.seconds.inWholeMilliseconds,
        poll = poll.copy(authorId = Recipient.self().id.toLong()),
        question = poll.question
      )

      Log.i(TAG, "Sending poll create to " + message.threadRecipient.id + ", thread: " + threadId)

      MessageSender.sendPollAction(
        AppDependencies.application,
        message,
        threadId,
        MessageSender.SendType.SIGNAL,
        null,
        { emitter.onComplete() }
      )
    }.subscribeOn(Schedulers.io())
  }

  fun endPoll(pollId: Long): Completable {
    return Completable.create { emitter ->
      val poll = REDDatabase.polls.getPollFromId(pollId)
      val messageRecord = REDDatabase.messages.getMessageRecord(poll!!.messageId)
      val threadRecipient = REDDatabase.threads.getRecipientForThreadId(messageRecord.threadId)!!
      val pollSentTimestamp = messageRecord.dateSent

      if (threadRecipient.isPushV2Group && threadRecipient.groupId.getOrNull()?.isV2 != true) {
        Log.w(TAG, "Missing group id")
        emitter.tryOnError(Exception("Poll terminate failed"))
        return@create
      }

      if (threadRecipient.isPushV2Group && !REDDatabase.groups.isActive(threadRecipient.requireGroupId())) {
        Log.w(TAG, "Cannot end poll in terminated or inactive group")
        emitter.tryOnError(Exception("Poll terminate failed"))
        return@create
      }

      val message = OutgoingMessage.pollTerminateMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = System.currentTimeMillis(),
        expiresIn = threadRecipient.expiresInSeconds.seconds.inWholeMilliseconds,
        messageExtras = MessageExtras(pollTerminate = PollTerminate(question = poll.question, messageId = poll.messageId, targetTimestamp = pollSentTimestamp))
      )

      Log.i(TAG, "Sending poll terminate to " + message.threadRecipient.id + ", thread: " + messageRecord.threadId)

      val possibleTargets: List<Recipient> = if (threadRecipient.isPushV2Group) {
        REDDatabase.groups.getGroupMembers(threadRecipient.requireGroupId().requireV2(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF)
          .map { it.resolve() }
          .distinctBy { it.id }
      } else {
        listOf(threadRecipient)
      }
      val isSelf = threadRecipient.isSelf

      val eligibleTargets: List<Recipient> = RecipientUtil.getEligibleForSending(possibleTargets)
      val results = sendEndPoll(threadRecipient, message, eligibleTargets, isSelf, poll.messageId)
      val sendResults = GroupSendJobHelper.getCompletedSends(eligibleTargets, results)

      if (sendResults.completed.isNotEmpty() || possibleTargets.isEmpty()) {
        val allocatedThreadId = REDDatabase.threads.getOrCreateValidThreadId(threadRecipient, messageRecord.threadId, message.distributionType)
        val outgoingMessage = applyUniversalExpireTimerIfNecessary(applicationContext, threadRecipient, message, allocatedThreadId)
        val insertResult = REDDatabase.messages.insertMessageOutbox(outgoingMessage, allocatedThreadId, false, null)
        val messageId = insertResult.messageId

        REDDatabase.threads.update(threadId = allocatedThreadId, unarchive = true, syncThreadDelete = true)
        databaseObserver.notifyMessageUpdateObservers(MessageId(poll.messageId))
        databaseObserver.notifyMessageInsertObservers(messageRecord.threadId, MessageId(messageId))
        if (outgoingMessage.expiresIn > 0) {
          REDDatabase.messages.markExpireStarted(messageId)
          expiringMessageManager.scheduleDeletion(messageId, true, message.expiresIn)
        }

        if (sendResults.skipped.isNotEmpty()) {
          val messageRecord = REDDatabase.messages.getMessageRecord(messageId)
          val filterRecipientIds = (sendResults.skipped - sendResults.completed.map { it.id }).toSet()
          Log.i(TAG, "Some recipients skipped when sending end poll. Resending to $filterRecipientIds")
          MessageSender.resendGroupMessage(applicationContext, messageRecord, filterRecipientIds)
        } else {
          REDDatabase.messages.markAsSent(messageId)
        }
        emitter.onComplete()
      } else {
        emitter.tryOnError(Exception("Poll terminate failed"))
      }
    }.subscribeOn(Schedulers.io())
  }

  @Throws(IOException::class, GroupNotAMemberException::class, UndeliverableMessageException::class)
  fun sendEndPoll(threadRecipient: Recipient, message: OutgoingMessage, destinations: List<Recipient>, isSelf: Boolean, messageId: Long): List<SendMessageResult?> {
    val groupId = if (threadRecipient.isPushV2Group) threadRecipient.requireGroupId().requireV2() else null
    val groupRecord: GroupRecord? = if (threadRecipient.isPushV2Group) REDDatabase.groups.getGroup(threadRecipient.requireGroupId()).getOrNull() else null

    if (groupRecord != null && groupRecord.isAnnouncementGroup && !groupRecord.isAdmin(Recipient.self())) {
      throw UndeliverableMessageException("Non-admins cannot send messages in announcement groups!")
    }

    val builder = newBuilder()

    if (groupId != null) {
      GroupUtil.setDataMessageGroupContext(AppDependencies.application, builder, groupId)
    }

    val sentTime = System.currentTimeMillis()
    val message = builder
      .withTimestamp(sentTime)
      .withExpiration((message.expiresIn / 1000).toInt())
      .withProfileKey(ProfileKeyUtil.getSelfProfileKey().serialize())
      .withPollTerminate(REDServiceDataMessage.PollTerminate(message.messageExtras!!.pollTerminate!!.targetTimestamp))
      .build()

    return if (isSelf) {
      listOf(AppDependencies.signalServiceMessageSender.sendSyncMessage(message))
    } else {
      GroupSendUtil.sendResendableDataMessage(
        applicationContext,
        groupId,
        null,
        destinations,
        false,
        ContentHint.RESENDABLE,
        MessageId(messageId),
        message,
        true,
        false,
        null
      ) { System.currentTimeMillis() - sentTime > POLL_TERMINATE_TIMEOUT.inWholeMilliseconds }
    }
  }

  fun getPinnedMessages(threadId: Long): List<MmsMessageRecord> {
    return REDDatabase.messages.getPinnedMessages(threadId = threadId, orderByPinned = true)
  }

  fun pinMessage(messageRecord: MessageRecord, duration: Duration, threadRecipient: Recipient): Completable {
    return Completable.create { emitter ->
      val isGroup = threadRecipient.isPushV2Group
      if (isGroup && threadRecipient.groupId.getOrNull()?.isV2 != true) {
        emitter.tryOnError(Exception("Pin message failed - missing group id"))
      }

      val message = OutgoingMessage.pinMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = System.currentTimeMillis(),
        expiresIn = threadRecipient.expiresInSeconds.seconds.inWholeMilliseconds,
        messageExtras = MessageExtras(
          pinnedMessage = PinnedMessage(
            pinnedMessageId = messageRecord.id,
            targetAuthorAci = messageRecord.fromRecipient.requireAci().toByteString(),
            targetTimestamp = messageRecord.dateSent,
            pinDurationInSeconds = if (duration.isInfinite()) MessageTable.PIN_FOREVER else duration.inWholeSeconds
          )
        )
      )

      Log.i(TAG, "Sending pin create to ${message.threadRecipient.id}, thread: ${messageRecord.threadId}")

      val possibleTargets: List<Recipient> = if (isGroup) {
        REDDatabase.groups.getGroupMembers(threadRecipient.requireGroupId().requireV2(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF).map { it.resolve() }.distinctBy { it.id }
      } else {
        listOf(threadRecipient)
      }

      val includeSelf = threadRecipient.isSelf
      val eligibleTargets = RecipientUtil.getEligibleForSending(possibleTargets)
      val results = PinSendUtil.sendPinMessage(applicationContext, threadRecipient, message, eligibleTargets, includeSelf, messageRecord.id)

      val sendResults = GroupSendJobHelper.getCompletedSends(eligibleTargets, results)

      if (sendResults.completed.isNotEmpty() || possibleTargets.isEmpty()) {
        val allocatedThreadId = REDDatabase.threads.getOrCreateValidThreadId(threadRecipient, messageRecord.threadId, message.distributionType)
        val outgoingMessage = applyUniversalExpireTimerIfNecessary(applicationContext, threadRecipient, message, allocatedThreadId)
        val insertResult = REDDatabase.messages.insertMessageOutbox(outgoingMessage, allocatedThreadId, false, null)

        REDDatabase.threads.update(threadId = allocatedThreadId, unarchive = true, syncThreadDelete = true)
        databaseObserver.notifyConversationListeners(messageRecord.threadId)
        if (outgoingMessage.expiresIn > 0) {
          REDDatabase.messages.markExpireStarted(insertResult.messageId)
          expiringMessageManager.scheduleDeletion(insertResult.messageId, true, message.expiresIn)
        }

        if (sendResults.skipped.isNotEmpty()) {
          val messageRecord = REDDatabase.messages.getMessageRecord(insertResult.messageId)
          val filterRecipientIds = (sendResults.skipped - sendResults.completed.map { it.id }).toSet()
          Log.i(TAG, "Some recipients skipped when sending pin message. Resending to $filterRecipientIds")
          MessageSender.resendGroupMessage(applicationContext, messageRecord, filterRecipientIds)
        } else {
          REDDatabase.messages.markAsSent(insertResult.messageId)
        }
        emitter.onComplete()
      } else {
        emitter.tryOnError(Exception("Pin message failed"))
      }
    }.subscribeOn(Schedulers.io())
  }

  fun unpinMessage(messageId: Long): Completable {
    return Completable.create { emitter ->
      val message = REDDatabase.messages.getMessageRecordOrNull(messageId)
      if (message == null) {
        emitter.tryOnError(Exception("Unpin message failed - missing message"))
      }

      val threadRecipient = REDDatabase.threads.getRecipientForThreadId(message!!.threadId)
      if (threadRecipient == null) {
        emitter.tryOnError(Exception("Unpin message failed - missing thread recipient"))
      }

      val isGroup = threadRecipient!!.isPushV2Group
      if (isGroup && threadRecipient.groupId.getOrNull()?.isV2 != true) {
        emitter.tryOnError(Exception("Unpin message failed - missing group id"))
      }

      Log.i(TAG, "Sending unpin message to ${threadRecipient.id}")

      val possibleTargets: List<Recipient> = if (isGroup) {
        REDDatabase.groups.getGroupMembers(threadRecipient.requireGroupId().requireV2(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF).map { it.resolve() }.distinctBy { it.id }
      } else {
        listOf(threadRecipient)
      }

      val includeSelf = threadRecipient.isSelf
      val eligibleTargets: List<Recipient> = RecipientUtil.getEligibleForSending(possibleTargets)
      val results = PinSendUtil.sendUnpinMessage(applicationContext, threadRecipient, message.fromRecipient.requireServiceId(), message.dateSent, eligibleTargets, includeSelf, messageId)
      val sendResults = GroupSendJobHelper.getCompletedSends(eligibleTargets, results)

      if (sendResults.completed.isNotEmpty() || possibleTargets.isEmpty()) {
        REDDatabase.messages.unpinMessage(messageId = messageId, threadId = message.threadId)
        databaseObserver.notifyConversationListeners(message.threadId)

        if (sendResults.skipped.isNotEmpty()) {
          val filterRecipientIds = (sendResults.skipped - sendResults.completed.map { it.id }).toSet()
          Log.i(TAG, "Some recipients skipped when sending unpin message. Resending to $filterRecipientIds")
          val unpinJob = UnpinMessageJob.create(messageId = messageId, initialRecipientIds = filterRecipientIds)
          if (unpinJob != null) {
            AppDependencies.jobManager.add(unpinJob)
          }
        }
        emitter.onComplete()
      } else {
        emitter.tryOnError(Exception("Unpin message failed"))
      }
    }.subscribeOn(Schedulers.io())
  }

  fun setMessageStarred(messageId: Long, starred: Boolean): Completable {
    return setMessagesStarred(setOf(messageId), starred)
  }

  fun setMessagesStarred(messageIds: Set<Long>, starred: Boolean): Completable {
    return Completable.fromAction {
      REDDatabase.messages.setStarred(messageIds, starred)
    }.subscribeOn(Schedulers.io())
  }

  private fun applyUniversalExpireTimerIfNecessary(context: Context, recipient: Recipient, outgoingMessage: OutgoingMessage, threadId: Long): OutgoingMessage {
    if (!outgoingMessage.isExpirationUpdate && outgoingMessage.expiresIn == 0L) {
      val expireTimerVersion = RecipientUtil.setAndSendUniversalExpireTimerIfNecessary(context, recipient, threadId)

      if (expireTimerVersion != null) {
        return outgoingMessage.withExpiry(settings.universalExpireTimer.seconds.inWholeMilliseconds, expireTimerVersion)
      }
    }

    return outgoingMessage
  }

  fun sendMessage(
    threadId: Long,
    threadRecipient: Recipient,
    metricId: String?,
    body: String,
    slideDeck: SlideDeck?,
    scheduledDate: Long,
    messageToEdit: MessageId?,
    quote: QuoteModel?,
    mentions: List<Mention>,
    bodyRanges: BodyRangeList?,
    contacts: List<Contact>,
    linkPreviews: List<LinkPreview>,
    preUploadResults: List<PreUploadResult>,
    isViewOnce: Boolean
  ): Completable {
    val sendCompletable = Completable.create { emitter ->
      val splitMessage: MessageUtil.SplitResult = MessageUtil.getSplitMessage(
        applicationContext,
        body
      )

      val outgoingMessageSlideDeck: SlideDeck? = splitMessage.textSlide.map {
        (slideDeck ?: SlideDeck()).apply {
          addSlide(it)
        }
      }.orElse(slideDeck)

      val message = OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = System.currentTimeMillis(),
        body = if (slideDeck != null) OutgoingMessage.buildMessage(slideDeck, splitMessage.body) else splitMessage.body,
        expiresIn = threadRecipient.expiresInSeconds.seconds.inWholeMilliseconds,
        isUrgent = true,
        isSecure = true,
        bodyRanges = bodyRanges,
        scheduledDate = scheduledDate,
        outgoingQuote = quote,
        messageToEdit = messageToEdit?.id ?: 0,
        mentions = mentions,
        sharedContacts = contacts,
        linkPreviews = linkPreviews,
        attachments = outgoingMessageSlideDeck?.asAttachments() ?: emptyList(),
        isViewOnce = isViewOnce
      )

      if (preUploadResults.isEmpty()) {
        MessageSender.send(
          AppDependencies.application,
          message,
          threadId,
          MessageSender.SendType.SIGNAL,
          metricId
        ) {
          emitter.onComplete()
        }
      } else {
        val sendSuccessful = MessageSender.sendPushWithPreUploadedMedia(
          AppDependencies.application,
          message,
          preUploadResults,
          threadId
        ) {
          emitter.onComplete()
        }

        if (!sendSuccessful) {
          emitter.tryOnError(IllegalStateException("Could not send pre-uploaded attachments because they did not exist!"))
        }
      }
    }

    return sendCompletable
      .subscribeOn(Schedulers.io())
  }

  fun setLastVisibleMessageTimestamp(threadId: Long, lastVisibleMessageTimestamp: Long) {
    REDExecutors.BOUNDED_IO.execute { REDDatabase.threads.setLastScrolled(threadId, lastVisibleMessageTimestamp) }
  }

  fun markGiftBadgeRevealed(messageId: Long) {
    oldConversationRepository.markGiftBadgeRevealed(messageId)
  }

  fun getQuotedMessagePosition(threadId: Long, quoteId: Long, authorId: RecipientId): Single<Int> {
    return Single.fromCallable {
      REDDatabase.messages.getQuotedMessagePosition(threadId, quoteId, authorId)
    }.subscribeOn(Schedulers.io())
  }

  fun getMessageResultPosition(threadId: Long, receivedTimestamp: Long): Single<Int> {
    return Single.fromCallable {
      REDDatabase.messages.getMessagePositionInConversation(threadId, receivedTimestamp)
    }.subscribeOn(Schedulers.io())
  }

  fun getNextMentionPosition(threadId: Long): Single<Int> {
    return Single.fromCallable {
      val details = REDDatabase.messages.getOldestUnreadMentionDetails(threadId)
      if (details == null) {
        -1
      } else {
        REDDatabase.messages.getMessagePositionInConversation(threadId, details.second, details.first)
      }
    }.subscribeOn(Schedulers.io())
  }

  fun getMessagePosition(threadId: Long, messageId: Long): Single<Int> {
    return Single.fromCallable {
      val message = REDDatabase.messages.getMessageRecord(messageId)
      REDDatabase.messages.getMessagePositionInConversation(threadId, message.dateReceived, message.fromRecipient.id)
    }.subscribeOn(Schedulers.io())
  }

  fun getMessagePosition(threadId: Long, dateReceived: Long, authorId: RecipientId): Single<Int> {
    return Single.fromCallable {
      REDDatabase.messages.getMessagePositionInConversation(threadId, dateReceived, authorId)
    }.subscribeOn(Schedulers.io())
  }

  fun getMessageCounts(threadId: Long): Flowable<MessageCounts> {
    return RxDatabaseObserver.conversation(threadId)
      .map { getUnreadCount(threadId) }
      .distinctUntilChanged()
      .map { MessageCounts(it, getUnreadMentionsCount(threadId)) }
  }

  private fun getUnreadCount(threadId: Long): Int {
    return REDDatabase.messages.getUnreadCount(threadId)
  }

  private fun getUnreadMentionsCount(threadId: Long): Int {
    return REDDatabase.messages.getUnreadMentionCount(threadId)
  }

  @Suppress("IfThenToElvis")
  fun getIdentityRecords(recipient: Recipient, groupRecord: GroupRecord?): Single<IdentityRecordsState> {
    return Single.fromCallable {
      val recipients = if (groupRecord == null) {
        listOf(recipient)
      } else if (groupRecord.hasV2GroupProperties) {
        groupRecord.requireV2GroupProperties().getMemberRecipients(GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF)
      } else {
        emptyList()
      }

      val records = AppDependencies.protocolStore.aci().identities().getIdentityRecords(recipients)
      val isVerified = recipient.registered == RecipientTable.RegisteredState.REGISTERED &&
        Recipient.self().isRegistered &&
        records.isVerified &&
        !recipient.isSelf

      IdentityRecordsState(recipient, groupRecord, isVerified, records, isGroup = groupRecord != null)
    }.subscribeOn(Schedulers.io())
  }

  fun resetVerifiedStatusToDefault(unverifiedIdentities: List<IdentityRecord>): Completable {
    return Completable.fromCallable {
      ReentrantSessionLock.INSTANCE.acquire().use {
        val identityStore = AppDependencies.protocolStore.aci().identities()
        for ((recipientId, identityKey) in unverifiedIdentities) {
          identityStore.setVerified(recipientId, identityKey, VerifiedStatus.DEFAULT)
        }
      }
    }.subscribeOn(Schedulers.io())
  }

  fun dismissRequestReviewState(threadRecipientId: RecipientId) {
    REDExecutors.BOUNDED_IO.execute {
      REDDatabase.nameCollisions.markCollisionsForThreadRecipientDismissed(threadRecipientId)
    }
  }

  fun getRequestReviewState(recipient: Recipient, group: GroupRecord?, messageRequest: MessageRequestState): Single<RequestReviewState> {
    return Single.fromCallable {
      if (group == null && messageRequest.state != MessageRequestState.State.INDIVIDUAL) {
        return@fromCallable RequestReviewState()
      }

      if (group == null) {
        val recipientsToReview = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(recipient.id)
        if (recipientsToReview.isNotEmpty()) {
          return@fromCallable RequestReviewState(
            individualReviewState = IndividualReviewState(
              target = recipient,
              firstDuplicate = recipientsToReview.first().recipient
            )
          )
        }
      }

      if (group != null && group.hasV2GroupProperties) {
        val groupId = group.id.requireV2()
        val duplicateRecipients: List<ReviewRecipient> = REDDatabase.nameCollisions.getCollisionsForThreadRecipientId(group.recipientId)

        if (duplicateRecipients.isNotEmpty()) {
          return@fromCallable RequestReviewState(
            groupReviewState = GroupReviewState(
              groupId,
              duplicateRecipients[0].recipient,
              duplicateRecipients[1].recipient,
              duplicateRecipients.size
            )
          )
        }
      }

      RequestReviewState()
    }.subscribeOn(Schedulers.io())
  }

  fun getTemporaryViewOnceUri(mmsMessageRecord: MmsMessageRecord): Maybe<Uri> {
    return MaybeCompat.fromCallable {
      Log.i(TAG, "Copying the view-once photo to temp storage and deleting underlying media.")

      try {
        val thumbnailSlide = mmsMessageRecord.slideDeck.thumbnailSlide ?: return@fromCallable null
        val thumbnailUri = thumbnailSlide.uri ?: return@fromCallable null

        val inputStream = PartAuthority.getAttachmentStream(applicationContext, thumbnailUri)
        val tempUri = AppDependencies.blobs.forData(inputStream, thumbnailSlide.fileSize)
          .withMimeType(thumbnailSlide.contentType)
          .createForSingleSessionOnDisk(applicationContext)

        attachments.deleteAttachmentFilesForViewOnceMessage(mmsMessageRecord.id)
        AppDependencies.viewOnceMessageManager.scheduleIfNecessary()
        AppDependencies.jobManager.add(MultiDeviceViewOnceOpenJob(MessageTable.SyncMessageId(mmsMessageRecord.fromRecipient.id, mmsMessageRecord.dateSent)))

        tempUri
      } catch (e: IOException) {
        null
      }
    }.doOnComplete {
      Log.w(TAG, "Failed to open view-once photo. Deleting the attachments for the message just in case.")
      attachments.deleteAttachmentFilesForViewOnceMessage(mmsMessageRecord.id)
    }.subscribeOn(Schedulers.io())
  }

  fun setConversationMuted(recipientId: RecipientId, until: Long) {
    REDExecutors.BOUNDED_IO.execute { recipients.setMuted(recipientId, until) }
  }

  /**
   * Copies the selected content to the clipboard. Maybe will emit either the copied contents or
   * a complete which means there were no contents to be copied.
   */
  fun copyToClipboard(context: Context, messageParts: Set<MultiselectPart>): Maybe<CharSequence> {
    return Maybe.fromCallable { extractBodies(context, messageParts) }
      .subscribeOn(Schedulers.computation())
      .observeOn(AndroidSchedulers.mainThread())
      .doOnSuccess {
        Util.copyToClipboard(context, it)
      }
  }

  fun resendMessage(messageRecord: MessageRecord): Completable {
    return Completable.fromAction {
      MessageSender.resend(applicationContext, messageRecord)
    }.subscribeOn(Schedulers.io())
  }

  private fun extractBodies(context: Context, messageParts: Set<MultiselectPart>): CharSequence {
    return messageParts
      .asSequence()
      .sortedBy { it.getMessageRecord().dateReceived }
      .map { it.conversationMessage }
      .distinct()
      .mapNotNull { message ->
        if (message.messageRecord.hasTextSlide()) {
          val textSlideUri = message.messageRecord.requireTextSlide().uri
          if (textSlideUri == null) {
            message.getDisplayBody(context)
          } else {
            try {
              PartAuthority.getAttachmentStream(context, textSlideUri).use {
                val body = StreamUtil.readFullyAsString(it)
                ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, message.messageRecord, body, message.threadRecipient)
                  .getDisplayBody(context)
              }
            } catch (e: IOException) {
              Log.w(TAG, "failed to read text slide data.")
              null
            }
          }
        } else {
          message.getDisplayBody(context)
        }
      }
      .filterNot(Util::isEmpty)
      .joinTo(buffer = SpannableStringBuilder(), separator = "\n")
  }

  fun getRecipientContactPhotoBitmap(context: Context, requestManager: RequestManager, recipient: Recipient): Single<ShortcutInfoCompat> {
    val fallback = FallbackAvatarDrawable(context, recipient.getFallbackAvatar())

    return Single
      .create { emitter ->
        requestManager
          .asBitmap()
          .load(recipient.contactPhoto)
          .error(fallback)
          .into(ContactPhotoTarget(recipient.id, emitter))
      }
      .flatMap(ContactPhotoResult::transformToFinalBitmap)
      .map(IconCompat::createWithAdaptiveBitmap)
      .map {
        val name = if (recipient.isSelf) context.getString(R.string.note_to_self) else recipient.getDisplayName(context)

        ShortcutInfoCompat.Builder(context, "${recipient.id.serialize()}-${System.currentTimeMillis()}")
          .setShortLabel(name)
          .setIcon(it)
          .setIntent(ShortcutLauncherActivity.createIntent(context, recipient.id))
          .build()
      }
      .subscribeOn(Schedulers.computation())
  }

  fun getSlideDeckAndBodyForReply(context: Context, conversationMessage: ConversationMessage): Pair<SlideDeck, CharSequence> {
    val messageRecord = conversationMessage.messageRecord

    return if (messageRecord.isMms && messageRecord.hasSharedContact()) {
      val contact: Contact = (messageRecord as MmsMessageRecord).sharedContacts.first()
      val displayName: String = ContactUtil.getDisplayName(contact)
      val body: String = context.getString(R.string.ConversationActivity_quoted_contact_message, EmojiStrings.BUST_IN_SILHOUETTE, displayName)
      val slideDeck = SlideDeck()

      if (contact.avatarAttachment != null) {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(contact.avatarAttachment))
      }

      slideDeck to body
    } else if (messageRecord.isMms && messageRecord.hasLinkPreview()) {
      val linkPreview = (messageRecord as MmsMessageRecord).linkPreviews.first()
      val slideDeck = SlideDeck()

      linkPreview.thumbnail.ifPresent {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(it))
      }

      slideDeck to conversationMessage.getDisplayBody(context)
    } else {
      var slideDeck = if (messageRecord.isMms) {
        (messageRecord as MmsMessageRecord).slideDeck
      } else {
        SlideDeck()
      }

      if (messageRecord.isViewOnceMessage()) {
        val attachment = TombstoneAttachment.forQuote()
        slideDeck = SlideDeck()
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(attachment))
      }

      slideDeck to conversationMessage.getDisplayBody(context)
    }
  }

  fun resolveMessageToEdit(conversationMessage: ConversationMessage): Single<ConversationMessage> {
    return oldConversationRepository.resolveMessageToEdit(conversationMessage)
  }

  fun deleteSlideData(slides: List<Slide>) {
    REDExecutors.BOUNDED_IO.execute {
      slides
        .mapNotNull(Slide::getUri)
        .filter { AppDependencies.blobs.isAuthority(it) }
        .forEach {
          AppDependencies.blobs.delete(applicationContext, it)
        }
    }
  }

  fun updateStickerLastUsedTime(stickerRecord: StickerRecord, timestamp: Duration) {
    REDExecutors.BOUNDED_IO.execute {
      REDDatabase.stickers.updateStickerLastUsedTime(stickerRecord.rowId, timestamp.inWholeMilliseconds)
    }
  }

  fun startExpirationTimeout(expirationInfos: List<MessageTable.ExpirationInfo>) {
    REDDatabase.messages.markExpireStarted(expirationInfos.map { it.id to it.expireStarted })
    AppDependencies.expiringMessageManager.scheduleDeletion(expirationInfos)
  }

  fun getEarliestMessageSentDate(threadId: Long): Single<Long> {
    return Single
      .fromCallable { REDDatabase.messages.getEarliestMessageSentDate(threadId) }
      .subscribeOn(Schedulers.io())
  }

  fun collapseEvents(messageId: Long) {
    REDDatabase.messages.collapseEvents(messageId)
  }

  fun collapseAllEvents() {
    REDDatabase.messages.collapseAllEvents()
  }

  fun expandEvents(messageId: Long) {
    REDDatabase.messages.expandEvents(messageId)
  }

  /**
   * Glide target for a contact photo which expects an error drawable, and publishes
   * the result to the given emitter.
   *
   * The recipient is only used for displaying logging information.
   */
  private class ContactPhotoTarget(
    private val recipientId: RecipientId,
    private val emitter: SingleEmitter<ContactPhotoResult>
  ) : CustomTarget<Bitmap>() {
    override fun onLoadFailed(errorDrawable: Drawable?) {
      requireNotNull(errorDrawable)
      Log.w(TAG, "Utilizing fallback photo for shortcut for recipient $recipientId")
      emitter.onSuccess(ContactPhotoResult.DrawableResult(errorDrawable))
    }

    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
      emitter.onSuccess(ContactPhotoResult.BitmapResult(resource))
    }

    override fun onLoadCleared(placeholder: Drawable?) = Unit
  }

  /**
   * The result of the Glide load to get a user's contact photo. This can then be transformed into
   * something that the Android system likes via [transformToFinalBitmap]
   */
  private sealed interface ContactPhotoResult {

    companion object {
      private val SHORTCUT_ICON_SIZE = if (Build.VERSION.SDK_INT >= 26) 72.dp else (48 + 16 * 2).dp
    }

    class DrawableResult(private val drawable: Drawable) : ContactPhotoResult {
      override fun transformToFinalBitmap(): Single<Bitmap> {
        return Single.create {
          val bitmap = if (Build.VERSION.SDK_INT <= 25) {
            AdaptiveBitmapMetrics.wrapBitmap(DrawableUtil.toBitmap(drawable, SHORTCUT_ICON_SIZE, SHORTCUT_ICON_SIZE))
          } else {
            AdaptiveBitmapMetrics.wrapBitmap(drawable.toBitmap(SHORTCUT_ICON_SIZE, SHORTCUT_ICON_SIZE))
          }
          it.setCancellable {
            bitmap.recycle()
          }
          it.onSuccess(bitmap)
        }
      }
    }

    class BitmapResult(private val bitmap: Bitmap) : ContactPhotoResult {
      override fun transformToFinalBitmap(): Single<Bitmap> {
        return Single.create {
          val bitmap = AdaptiveBitmapMetrics.wrapBitmap(bitmap)
          it.setCancellable {
            bitmap.recycle()
          }
          it.onSuccess(bitmap)
        }
      }
    }

    fun transformToFinalBitmap(): Single<Bitmap>
  }

  data class MessageCounts(
    val unread: Int,
    val mentions: Int
  )
}
