package com.red.sovereign.jobs

import androidx.core.content.ContextCompat
import org.signal.core.util.logging.Log
import com.red.sovereign.R
import com.red.sovereign.avatar.Avatar
import com.red.sovereign.avatar.AvatarRenderer
import com.red.sovereign.avatar.Avatars
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.profiles.AvatarHelper
import com.red.sovereign.profiles.ProfileName
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.transport.RetryLaterException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Creates the Release Channel (RED) recipient.
 */
class CreateReleaseChannelJob private constructor(parameters: Parameters) : BaseJob(parameters) {
  companion object {
    const val KEY = "CreateReleaseChannelJob"

    private val TAG = Log.tag(CreateReleaseChannelJob::class.java)

    fun create(): CreateReleaseChannelJob {
      return CreateReleaseChannelJob(
        Parameters.Builder()
          .setQueue("CreateReleaseChannelJob")
          .setMaxInstancesForFactory(1)
          .setMaxAttempts(3)
          .build()
      )
    }
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    if (!REDStore.account.isRegistered) {
      Log.i(TAG, "Not registered, skipping.")
      return
    }

    if (REDStore.releaseChannel.releaseChannelRecipientId != null) {
      val existingId = REDStore.releaseChannel.releaseChannelRecipientId!!
      val recipient = REDDatabase.recipients.getRecord(existingId)

      val hasServiceId = recipient.serviceId != null
      val hasE164 = recipient.e164 != null
      val isGroup = recipient.groupId != null
      val isDistributionList = recipient.distributionListId != null
      val isCallLink = recipient.callLinkRoomId != null

      if (hasServiceId || hasE164 || isGroup || isDistributionList || isCallLink) {
        Log.w(TAG, "Release channel recipient $existingId is not a valid release channel recipient (hasServiceId: $hasServiceId, hasE164: $hasE164, isGroup: $isGroup, isDistributionList: $isDistributionList, isCallLink: $isCallLink). Clearing and recreating.")
        REDStore.releaseChannel.clearReleaseChannelRecipientId()
      } else {
        Log.i(TAG, "Already created Release Channel recipient $existingId")
        if (recipient.signalProfileAvatar.isNullOrEmpty() || !REDStore.releaseChannel.hasUpdatedAvatar) {
          REDStore.releaseChannel.hasUpdatedAvatar = true
          setAvatar(recipient.id)
        }
        return
      }
    }

    val recipients = REDDatabase.recipients

    val releaseChannelId: RecipientId = recipients.insertReleaseChannelRecipient()
    REDStore.releaseChannel.setReleaseChannelRecipientId(releaseChannelId)
    REDStore.releaseChannel.hasUpdatedAvatar = true

    recipients.setProfileName(releaseChannelId, ProfileName.asGiven("RED"))
    recipients.setMuted(releaseChannelId, Long.MAX_VALUE)
    setAvatar(releaseChannelId)
  }

  private fun setAvatar(id: RecipientId) {
    val latch = CountDownLatch(1)
    AvatarRenderer.renderAvatar(
      context,
      Avatar.Resource(
        R.drawable.ic_signal_logo_large,
        Avatars.ColorPair(ContextCompat.getColor(context, R.color.notification_background_ultramarine), ContextCompat.getColor(context, R.color.core_white), "")
      ),
      onAvatarRendered = { media ->
        AvatarHelper.setAvatar(context, id, AppDependencies.blobs.getStream(context, media.uri))
        REDDatabase.recipients.setProfileAvatar(id, "local")
        latch.countDown()
      },
      onRenderFailed = { t ->
        Log.w(TAG, t)
        latch.countDown()
      }
    )

    try {
      val completed: Boolean = latch.await(30, TimeUnit.SECONDS)
      if (!completed) {
        throw RetryLaterException()
      }
    } catch (e: InterruptedException) {
      throw RetryLaterException()
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = e is RetryLaterException

  class Factory : Job.Factory<CreateReleaseChannelJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CreateReleaseChannelJob {
      return CreateReleaseChannelJob(parameters)
    }
  }
}
