package com.red.sovereign.messages

import android.annotation.SuppressLint
import android.content.Context
import org.signal.core.util.Stopwatch
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.PushProcessEarlyMessagesJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.messages.MessageContentProcessor.Companion.log
import com.red.sovereign.messages.MessageContentProcessor.Companion.warn
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.util.EarlyMessageCacheEntry
import com.red.sovereign.util.REDTrace
import com.red.sovereign.util.TextSecurePreferences
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.push.ReceiptMessage

object ReceiptMessageProcessor {
  private val TAG = MessageContentProcessor.TAG

  private const val VERBOSE = false

  fun process(context: Context, senderRecipient: Recipient, envelope: Envelope, content: Content, metadata: EnvelopeMetadata, earlyMessageCacheEntry: EarlyMessageCacheEntry?, batchCache: BatchCache) {
    val receiptMessage = content.receiptMessage!!

    when (receiptMessage.type) {
      ReceiptMessage.Type.DELIVERY -> handleDeliveryReceipt(envelope, metadata, receiptMessage, senderRecipient.id, batchCache)
      ReceiptMessage.Type.READ -> handleReadReceipt(context, senderRecipient.id, envelope, metadata, receiptMessage, earlyMessageCacheEntry, batchCache)
      ReceiptMessage.Type.VIEWED -> handleViewedReceipt(context, envelope, metadata, receiptMessage, senderRecipient.id, earlyMessageCacheEntry)
      else -> warn(envelope.clientTimestamp!!, "Unknown recipient message type ${receiptMessage.type}")
    }
  }

  @SuppressLint("DefaultLocale")
  private fun handleDeliveryReceipt(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    deliveryReceipt: ReceiptMessage,
    senderRecipientId: RecipientId,
    batchCache: BatchCache
  ) {
    log(envelope.clientTimestamp!!, "Processing delivery receipts. Sender: $senderRecipientId, Device: ${metadata.sourceDeviceId}, Timestamps: ${deliveryReceipt.timestamp.joinToString(", ")}")
    val stopwatch: Stopwatch? = if (VERBOSE) Stopwatch("delivery-receipt", decimalPlaces = 2) else null

    REDTrace.beginSection("ReceiptMessageProcessor#incrementDeliveryReceiptCounts")
    val missingTargetTimestamps: Set<Long> = REDDatabase.messages.incrementDeliveryReceiptCounts(deliveryReceipt.timestamp, senderRecipientId, envelope.clientTimestamp!!, stopwatch, batchCache.deliveryReceiptLookupCache)
    REDTrace.endSection()

    for (targetTimestamp in missingTargetTimestamps) {
      warn(envelope.clientTimestamp!!, "[handleDeliveryReceipt] Could not find matching message! targetTimestamp: $targetTimestamp, receiptAuthor: $senderRecipientId")
      // Early delivery receipts are special-cased in the database methods
    }

    if (missingTargetTimestamps.isNotEmpty()) {
      PushProcessEarlyMessagesJob.enqueue()
    }

    REDDatabase.pendingPniSignatureMessages.acknowledgeReceipts(senderRecipientId, deliveryReceipt.timestamp, metadata.sourceDeviceId)
    stopwatch?.split("pni-signatures")

    batchCache.addMslDelete(senderRecipientId, metadata.sourceDeviceId, deliveryReceipt.timestamp)
    stopwatch?.split("msl")

    stopwatch?.stop(TAG)
  }

  @SuppressLint("DefaultLocale")
  private fun handleReadReceipt(
    context: Context,
    senderRecipientId: RecipientId,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    readReceipt: ReceiptMessage,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?,
    batchCache: BatchCache
  ) {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
      log(envelope.clientTimestamp!!, "Ignoring read receipts for IDs: " + readReceipt.timestamp.joinToString(", "))
      return
    }

    log(envelope.clientTimestamp!!, "Processing read receipts. Sender: $senderRecipientId, Device: ${metadata.sourceDeviceId}, Timestamps: ${readReceipt.timestamp.joinToString(", ")}")

    REDTrace.beginSection("ReceiptMessageProcessor#incrementReadReceiptCounts")
    val missingTargetTimestamps: Set<Long> = REDDatabase.messages.incrementReadReceiptCounts(readReceipt.timestamp, senderRecipientId, envelope.clientTimestamp!!, batchCache.readReceiptLookupCache)
    REDTrace.endSection()

    if (missingTargetTimestamps.isNotEmpty()) {
      val selfId = Recipient.self().id

      for (targetTimestamp in missingTargetTimestamps) {
        warn(envelope.clientTimestamp!!, "[handleReadReceipt] Could not find matching message! targetTimestamp: $targetTimestamp, receiptAuthor: $senderRecipientId | Receipt, so associating with message from self ($selfId)")
        if (earlyMessageCacheEntry != null) {
          AppDependencies.earlyMessageCache.store(selfId, targetTimestamp, earlyMessageCacheEntry)
        }
      }
    }

    if (missingTargetTimestamps.isNotEmpty() && earlyMessageCacheEntry != null) {
      PushProcessEarlyMessagesJob.enqueue()
    }
  }

  private fun handleViewedReceipt(
    context: Context,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    viewedReceipt: ReceiptMessage,
    senderRecipientId: RecipientId,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?
  ) {
    val readReceipts = TextSecurePreferences.isReadReceiptsEnabled(context)
    val storyViewedReceipts = REDStore.story.viewedReceiptsEnabled

    if (!readReceipts && !storyViewedReceipts) {
      log(envelope.clientTimestamp!!, "Ignoring viewed receipts for IDs: ${viewedReceipt.timestamp.joinToString(", ")}")
      return
    }

    log(envelope.clientTimestamp!!, "Processing viewed receipts. Sender: $senderRecipientId, Device: ${metadata.sourceDeviceId}, Only Stories: ${!readReceipts}, Timestamps: ${viewedReceipt.timestamp.joinToString(", ")}")

    val missingTargetTimestamps: Set<Long> = if (readReceipts && storyViewedReceipts) {
      REDDatabase.messages.incrementViewedReceiptCounts(viewedReceipt.timestamp, senderRecipientId, envelope.clientTimestamp!!)
    } else if (readReceipts) {
      REDDatabase.messages.incrementViewedNonStoryReceiptCounts(viewedReceipt.timestamp, senderRecipientId, envelope.clientTimestamp!!)
    } else {
      REDDatabase.messages.incrementViewedStoryReceiptCounts(viewedReceipt.timestamp, senderRecipientId, envelope.clientTimestamp!!)
    }

    val foundTargetTimestamps: Set<Long> = viewedReceipt.timestamp.toSet() - missingTargetTimestamps.toSet()
    REDDatabase.messages.updateViewedStories(foundTargetTimestamps)

    if (missingTargetTimestamps.isNotEmpty()) {
      val selfId = Recipient.self().id

      for (targetTimestamp in missingTargetTimestamps) {
        warn(envelope.clientTimestamp!!, "[handleViewedReceipt] Could not find matching message! targetTimestamp: $targetTimestamp, receiptAuthor: $senderRecipientId | Receipt so associating with message from self ($selfId)")
        if (earlyMessageCacheEntry != null) {
          AppDependencies.earlyMessageCache.store(selfId, targetTimestamp, earlyMessageCacheEntry)
        }
      }
    }

    if (missingTargetTimestamps.isNotEmpty() && earlyMessageCacheEntry != null) {
      PushProcessEarlyMessagesJob.enqueue()
    }
  }
}
