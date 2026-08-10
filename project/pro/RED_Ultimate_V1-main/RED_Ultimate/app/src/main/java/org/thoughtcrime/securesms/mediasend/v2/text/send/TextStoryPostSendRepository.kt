package com.red.sovereign.mediasend.v2.text.send

import android.graphics.Bitmap
import android.net.Uri
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.Base64
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import com.red.sovereign.contacts.paged.ContactSearchKey
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.StoryType
import com.red.sovereign.database.model.databaseprotos.StoryTextPost
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.fonts.TextFont
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.StorySend
import com.red.sovereign.linkpreview.LinkPreview
import com.red.sovereign.mediasend.v2.UntrustedRecords
import com.red.sovereign.mediasend.v2.text.TextStoryPostCreationState
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.stories.Stories
import java.io.ByteArrayOutputStream

private val TAG = Log.tag(TextStoryPostSendRepository::class.java)

class TextStoryPostSendRepository {

  fun compressToBlob(bitmap: Bitmap): Single<Uri> {
    return Single.fromCallable {
      val outputStream = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
      bitmap.recycle()
      AppDependencies.blobs.forData(outputStream.toByteArray()).createForSingleSessionInMemory()
    }.subscribeOn(Schedulers.computation())
  }

  fun send(contactSearchKey: Set<ContactSearchKey>, textStoryPostCreationState: TextStoryPostCreationState, linkPreview: LinkPreview?, identityChangesSince: Long): Single<TextStoryPostSendResult> {
    return UntrustedRecords
      .checkForBadIdentityRecords(contactSearchKey.filterIsInstance(ContactSearchKey.RecipientSearchKey::class.java).toSet(), identityChangesSince)
      .toSingleDefault<TextStoryPostSendResult>(TextStoryPostSendResult.Success)
      .onErrorReturn {
        if (it is UntrustedRecords.UntrustedRecordsException) {
          TextStoryPostSendResult.UntrustedRecordsError(it.untrustedRecords)
        } else {
          Log.w(TAG, "Unexpected error occurred", it)
          TextStoryPostSendResult.Failure
        }
      }
      .flatMap { result ->
        if (result is TextStoryPostSendResult.Success) {
          performSend(contactSearchKey, textStoryPostCreationState, linkPreview)
        } else {
          Single.just(result)
        }
      }
  }

  private fun performSend(contactSearchKey: Set<ContactSearchKey>, textStoryPostCreationState: TextStoryPostCreationState, linkPreview: LinkPreview?): Single<TextStoryPostSendResult> {
    return Single.fromCallable {
      val messages: MutableList<OutgoingMessage> = mutableListOf()
      val distributionListSentTimestamp = System.currentTimeMillis()

      for (contact in contactSearchKey) {
        val recipient = Recipient.resolved(contact.requireShareContact().recipientId.get())
        val isStory = contact.requireRecipientSearchKey().isStory || recipient.isDistributionList

        if (isStory && !recipient.isMyStory) {
          REDStore.story.setLatestStorySend(StorySend.newSend(recipient))
        }

        val storyType: StoryType = when {
          recipient.isDistributionList -> REDDatabase.distributionLists.getStoryType(recipient.requireDistributionListId())
          isStory -> StoryType.STORY_WITH_REPLIES
          else -> StoryType.NONE
        }

        val message = OutgoingMessage(
          recipient = recipient,
          body = serializeTextStoryState(textStoryPostCreationState),
          timestamp = if (recipient.isDistributionList) distributionListSentTimestamp else System.currentTimeMillis(),
          storyType = storyType.toTextStoryType(),
          previews = listOfNotNull(linkPreview),
          isSecure = true
        )

        messages.add(message)
        ThreadUtil.sleep(5)
      }

      Stories.sendTextStories(messages)
    }.flatMap { messages ->
      messages.toSingleDefault<TextStoryPostSendResult>(TextStoryPostSendResult.Success)
    }
  }

  private fun serializeTextStoryState(textStoryPostCreationState: TextStoryPostCreationState): String {
    val builder = StoryTextPost.Builder()

    builder.body = textStoryPostCreationState.body.toString()
    builder.background = textStoryPostCreationState.backgroundColor.serialize()
    builder.style = when (textStoryPostCreationState.textFont) {
      TextFont.REGULAR -> StoryTextPost.Style.REGULAR
      TextFont.BOLD -> StoryTextPost.Style.BOLD
      TextFont.SERIF -> StoryTextPost.Style.SERIF
      TextFont.SCRIPT -> StoryTextPost.Style.SCRIPT
      TextFont.CONDENSED -> StoryTextPost.Style.CONDENSED
    }
    builder.textBackgroundColor = textStoryPostCreationState.textBackgroundColor
    builder.textForegroundColor = textStoryPostCreationState.textForegroundColor

    return Base64.encodeWithPadding(builder.build().encode())
  }
}
