package com.red.sovereign.conversation.drafts

import android.content.Context
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.models.media.Media
import org.signal.core.util.Base64
import org.signal.core.util.StreamUtil
import org.signal.core.util.concurrent.MaybeCompat
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import com.red.sovereign.components.location.REDPlace
import com.red.sovereign.components.mention.MentionAnnotation
import com.red.sovereign.conversation.ConversationArgs
import com.red.sovereign.conversation.ConversationMessage
import com.red.sovereign.conversation.ConversationMessage.ConversationMessageFactory
import com.red.sovereign.conversation.MessageStyler
import com.red.sovereign.database.DraftTable
import com.red.sovereign.database.DraftTable.Drafts
import com.red.sovereign.database.MentionUtil
import com.red.sovereign.database.MessageTypes
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.ThreadTable
import com.red.sovereign.database.adjustBodyRanges
import com.red.sovereign.database.model.Mention
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.model.databaseprotos.BodyRangeList
import com.red.sovereign.database.withAttachments
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyboard.KeyboardUtil
import com.red.sovereign.mms.GifSlide
import com.red.sovereign.mms.ImageSlide
import com.red.sovereign.mms.PartAuthority
import com.red.sovereign.mms.QuoteId
import com.red.sovereign.mms.Slide
import com.red.sovereign.mms.SlideFactory
import com.red.sovereign.mms.StickerSlide
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.MediaUtil
import com.red.sovereign.util.concurrent.SerialMonoLifoExecutor
import com.red.sovereign.util.hasTextSlide
import com.red.sovereign.util.requireTextSlide
import java.io.IOException
import java.util.concurrent.Executor

class DraftRepository(
  private val context: Context = AppDependencies.application,
  private val threadTable: ThreadTable = REDDatabase.threads,
  private val draftTable: DraftTable = REDDatabase.drafts,
  private val saveDraftsExecutor: Executor = SerialMonoLifoExecutor(REDExecutors.BOUNDED),
  private val conversationArguments: ConversationArgs? = null
) {

  companion object {
    val TAG = Log.tag(DraftRepository::class.java)
  }

  fun getShareOrDraftData(lastShareDataTimestamp: Long): Maybe<Pair<ShareOrDraftData?, Drafts?>> {
    return MaybeCompat.fromCallable { getShareOrDraftDataInternal(lastShareDataTimestamp) }
      .observeOn(Schedulers.io())
  }

  /**
   * Loads share data from the intent and draft data from the database and provides a one-spot initial
   * load of data.
   *
   * Note: Voice note drafts are handled differently and via the [DraftViewModel.state]
   */
  @Suppress("ConvertTwoComparisonsToRangeCheck")
  private fun getShareOrDraftDataInternal(lastShareDataTimestamp: Long): Pair<ShareOrDraftData?, Drafts?>? {
    val sharedDataTimestamp: Long = conversationArguments?.shareDataTimestamp ?: -1
    Log.d(TAG, "Shared this data at $sharedDataTimestamp and last processed share data at $lastShareDataTimestamp")
    if (sharedDataTimestamp > 0 && sharedDataTimestamp <= lastShareDataTimestamp) {
      Log.d(TAG, "Already processed this share data. Skipping.")
      return null
    } else {
      Log.d(TAG, "Have not processed this share data. Proceeding.")
    }

    val shareText = conversationArguments?.draftText
    val shareMedia = conversationArguments?.draftMedia
    val shareContentType = conversationArguments?.draftContentType
    val shareMediaType = conversationArguments?.draftMediaType
    val shareMediaList = conversationArguments?.media ?: emptyList()
    val stickerLocator = conversationArguments?.stickerLocator
    val borderless = conversationArguments?.isBorderless ?: false

    if (stickerLocator != null && shareMedia != null) {
      val slide = StickerSlide(context, shareMedia, 0, stickerLocator, shareContentType!!)
      return ShareOrDraftData.SendSticker(slide) to null
    }

    if (shareMedia != null && shareContentType != null && borderless) {
      val details = KeyboardUtil.getImageDetails(shareMedia)

      if (details == null || !details.isSticker) {
        return ShareOrDraftData.SetMedia(shareMedia, shareMediaType!!, null) to null
      }

      val slide: Slide? = if (MediaUtil.isGif(shareContentType)) {
        GifSlide(context, shareMedia, 0, details.width, details.height, true, null)
      } else if (MediaUtil.isImageType(shareContentType)) {
        ImageSlide(context, shareMedia, shareContentType, 0, details.width, details.height, true, null, null)
      } else {
        Log.w(TAG, "Attempting to send unsupported non-image via keyboard share")
        null
      }

      return if (slide != null) ShareOrDraftData.SendKeyboardImage(slide) to null else null
    }

    if (shareMediaList.isNotEmpty()) {
      return ShareOrDraftData.StartSendMedia(shareMediaList.filterNotNull(), shareText) to null
    }

    if (shareMedia != null && shareMediaType != null) {
      return ShareOrDraftData.SetMedia(shareMedia, shareMediaType, shareText) to null
    }

    if (shareText != null) {
      return ShareOrDraftData.SetText(shareText) to null
    }

    if (conversationArguments?.canInitializeFromDatabase() == true) {
      val (drafts, updatedText) = loadDraftsInternal(conversationArguments.threadId)

      val draftText: CharSequence? = drafts.firstOrNull { it.type == DraftTable.Draft.TEXT }?.let { updatedText ?: it.value }

      val messageEdit: ConversationMessage? = drafts.firstOrNull { it.type == DraftTable.Draft.MESSAGE_EDIT }?.let { loadDraftMessageEditInternal(it.value) }
      if (messageEdit != null) {
        return ShareOrDraftData.SetEditMessage(messageEdit, draftText, clearQuote = drafts.none { it.type == DraftTable.Draft.QUOTE }) to drafts
      }

      val location: REDPlace? = drafts.firstOrNull { it.type == DraftTable.Draft.LOCATION }?.let { REDPlace.deserialize(it.value) }
      if (location != null) {
        return ShareOrDraftData.SetLocation(location, draftText) to drafts
      }

      val quote: ConversationMessage? = drafts.firstOrNull { it.type == DraftTable.Draft.QUOTE }?.let { loadDraftQuoteInternal(it.value) }
      if (quote != null) {
        return ShareOrDraftData.SetQuote(quote, draftText) to drafts
      }

      if (draftText != null) {
        return ShareOrDraftData.SetText(draftText) to drafts
      }

      return null to drafts
    }

    // no share or draft
    return null
  }

  fun deleteVoiceNoteDraftData(draft: DraftTable.Draft?) {
    if (draft != null) {
      REDExecutors.BOUNDED.execute {
        AppDependencies.blobs.delete(context, Uri.parse(draft.value).buildUpon().clearQuery().build())
      }
    }
  }

  fun saveDrafts(threadId: Long, drafts: Drafts) {
    require(threadId != -1L)

    saveDraftsExecutor.execute {
      if (drafts.isNotEmpty()) {
        draftTable.replaceDrafts(threadId, drafts)
        if (drafts.shouldUpdateSnippet()) {
          threadTable.updateSnippet(threadId, drafts.getSnippet(context), drafts.getUriSnippet(), System.currentTimeMillis(), MessageTypes.BASE_DRAFT_TYPE, true)
        } else {
          threadTable.update(threadId, unarchive = false, allowDeletion = false)
        }
      } else if (threadId > 0) {
        draftTable.clearDrafts(threadId)
        threadTable.update(threadId, unarchive = false, allowDeletion = false)
      }
    }
  }

  private fun loadDraftsInternal(threadId: Long): DatabaseDraft {
    val drafts: Drafts = draftTable.getDrafts(threadId)
    val bodyRangesDraft: DraftTable.Draft? = drafts.getDraftOfType(DraftTable.Draft.BODY_RANGES)
    val textDraft: DraftTable.Draft? = drafts.getDraftOfType(DraftTable.Draft.TEXT)
    var updatedText: Spannable? = null

    if (textDraft != null && bodyRangesDraft != null) {
      val bodyRanges: BodyRangeList = BodyRangeList.ADAPTER.decode(Base64.decodeOrThrow(bodyRangesDraft.value))
      val mentions: List<Mention> = MentionUtil.bodyRangeListToMentions(bodyRanges)

      val updated = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, textDraft.value, mentions)

      updatedText = SpannableString(updated.body)
      MentionAnnotation.setMentionAnnotations(updatedText, updated.mentions)
      MessageStyler.style(id = MessageStyler.DRAFT_ID, messageRanges = bodyRanges.adjustBodyRanges(updated.bodyAdjustments), span = updatedText, hideSpoilerText = false)
    }

    return DatabaseDraft(drafts, updatedText)
  }

  private fun loadDraftQuoteInternal(serialized: String): ConversationMessage? {
    val quoteId: QuoteId = QuoteId.deserialize(context, serialized) ?: return null
    val messageRecord: MessageRecord = REDDatabase.messages.getMessageFor(quoteId.id, quoteId.author)?.let {
      if (it is MmsMessageRecord) {
        it.withAttachments(REDDatabase.attachments.getAttachmentsForMessage(it.id))
      } else {
        it
      }
    } ?: return null

    val threadRecipient = requireNotNull(REDDatabase.threads.getRecipientForThreadId(messageRecord.threadId))
    return ConversationMessageFactory.createWithUnresolvedData(context, messageRecord, threadRecipient)
  }

  private fun loadDraftMessageEditInternal(serialized: String): ConversationMessage? {
    val messageId = MessageId.deserialize(serialized)
    var messageRecord: MessageRecord = REDDatabase.messages.getMessageRecordOrNull(messageId.id) ?: return null
    messageRecord = messageRecord.withAttachments()
    val threadRecipient: Recipient = requireNotNull(REDDatabase.threads.getRecipientForThreadId(messageRecord.threadId))
    if (messageRecord.hasTextSlide()) {
      val textSlide = messageRecord.requireTextSlide()
      if (textSlide.uri != null) {
        try {
          PartAuthority.getAttachmentStream(context, textSlide.uri!!).use { stream ->
            val body = StreamUtil.readFullyAsString(stream)
            return ConversationMessageFactory.createWithUnresolvedData(context, messageRecord, body, threadRecipient)
          }
        } catch (e: IOException) {
          Log.e(TAG, "Failed to load text slide", e)
        }
      }
    }
    return ConversationMessageFactory.createWithUnresolvedData(context, messageRecord, threadRecipient)
  }

  data class DatabaseDraft(val drafts: Drafts, val updatedText: CharSequence?)

  sealed interface ShareOrDraftData {
    data class SendSticker(val slide: Slide) : ShareOrDraftData
    data class SendKeyboardImage(val slide: Slide) : ShareOrDraftData
    data class StartSendMedia(val mediaList: List<Media>, val text: CharSequence?) : ShareOrDraftData
    data class SetMedia(val media: Uri, val mediaType: SlideFactory.MediaType, val text: CharSequence?) : ShareOrDraftData
    data class SetText(val text: CharSequence) : ShareOrDraftData
    data class SetLocation(val location: REDPlace, val draftText: CharSequence?) : ShareOrDraftData
    data class SetQuote(val quote: ConversationMessage, val draftText: CharSequence?) : ShareOrDraftData
    data class SetEditMessage(val messageEdit: ConversationMessage, val draftText: CharSequence?, val clearQuote: Boolean) : ShareOrDraftData
  }
}
