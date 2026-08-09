package com.red.sovereign.stories.viewer

import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.CompletableSubject
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import com.red.sovereign.R
import com.red.sovereign.contacts.paged.ContactSearchKey
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.mediasend.MediaSendActivityResult
import com.red.sovereign.mediasend.v2.MediaSelectionActivity
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.sharing.MultiShareArgs
import com.red.sovereign.sharing.MultiShareSender
import com.red.sovereign.sms.MessageSender

/**
 * Delegate for dealing with sending stories directly to a group.
 */
class AddToGroupStoryDelegate(
  private val fragment: Fragment
) {

  companion object {
    private val TAG = Log.tag(AddToGroupStoryDelegate::class.java)
  }

  private val lifecycleDisposable = LifecycleDisposable().apply {
    bindTo(fragment.viewLifecycleOwner)
  }

  private val addToStoryLauncher: ActivityResultLauncher<Intent> = fragment.registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val data = result.data
    if (data == null) {
      Log.d(TAG, "No result data.")
    } else {
      Log.d(TAG, "Processing result...")
      val mediaSelectionResult: MediaSendActivityResult = MediaSendActivityResult.fromData(data)
      handleResult(mediaSelectionResult)
    }
  }

  fun addToStory(recipientId: RecipientId) {
    val addToStoryIntent = MediaSelectionActivity.addToGroupStory(
      fragment.requireContext(),
      recipientId
    )

    addToStoryLauncher.launch(addToStoryIntent)
  }

  private fun handleResult(result: MediaSendActivityResult) {
    lifecycleDisposable += ResultHandler.handleResult(result)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        Toast.makeText(fragment.requireContext(), R.string.TextStoryPostCreationFragment__sent_story, Toast.LENGTH_SHORT).show()
      }
  }

  /**
   * Dispatches the send result on a background thread, isolated from the fragment.
   */
  private object ResultHandler {

    /**
     * Handles the result, completing after sending the message.
     */
    @CheckResult
    fun handleResult(result: MediaSendActivityResult): Completable {
      Log.d(TAG, "Dispatching result handler.")
      val subject = CompletableSubject.create()
      REDExecutors.BOUNDED_IO.execute {
        if (result.isPushPreUpload) {
          sendPreUploadedMedia(result)
        } else {
          sendNonPreUploadedMedia(result)
        }

        subject.onComplete()
      }

      return subject
    }

    @WorkerThread
    private fun sendPreUploadedMedia(result: MediaSendActivityResult) {
      Log.d(TAG, "Sending preupload media.")

      val recipient = Recipient.resolved(result.recipientId)
      val secureMessages = result.preUploadResults
        .mapNotNull { REDDatabase.attachments.getAttachment(it.attachmentId) }
        .map {
          Thread.sleep(5)
          OutgoingMessage(
            recipient = recipient,
            timestamp = System.currentTimeMillis(),
            storyType = result.storyType,
            isSecure = true,
            body = result.body,
            attachments = listOf(it)
          )
        }

      MessageSender.sendStories(
        AppDependencies.application,
        secureMessages,
        null
      ) {
        Log.d(TAG, "Sent.")
      }
    }

    @WorkerThread
    private fun sendNonPreUploadedMedia(result: MediaSendActivityResult) {
      Log.d(TAG, "Sending non-preupload media.")

      val multiShareArgs = MultiShareArgs.Builder(setOf(ContactSearchKey.RecipientSearchKey(result.recipientId, true)))
        .withMedia(result.nonUploadedMedia.toList())
        .withDraftText(result.body)
        .withMentions(result.mentions.toList())
        .withBodyRanges(result.bodyRanges)
        .build()

      val results = MultiShareSender.sendSync(multiShareArgs)

      Log.d(TAG, "Sent. Failures? ${results.containsFailures()}")
    }
  }
}
