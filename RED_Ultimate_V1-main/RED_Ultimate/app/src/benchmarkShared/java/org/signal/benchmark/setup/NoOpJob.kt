package org.signal.benchmark.setup

import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.AccountConsistencyWorkerJob
import com.red.sovereign.jobs.ArchiveBackupIdReservationJob
import com.red.sovereign.jobs.AvatarGroupsV2DownloadJob
import com.red.sovereign.jobs.CreateReleaseChannelJob
import com.red.sovereign.jobs.DirectoryRefreshJob
import com.red.sovereign.jobs.DownloadLatestEmojiDataJob
import com.red.sovereign.jobs.EmojiSearchIndexDownloadJob
import com.red.sovereign.jobs.FontDownloaderJob
import com.red.sovereign.jobs.GroupRingCleanupJob
import com.red.sovereign.jobs.GroupV2UpdateSelfProfileKeyJob
import com.red.sovereign.jobs.LinkedDeviceInactiveCheckJob
import com.red.sovereign.jobs.MultiDeviceProfileKeyUpdateJob
import com.red.sovereign.jobs.PostRegistrationBackupRedemptionJob
import com.red.sovereign.jobs.PreKeysSyncJob
import com.red.sovereign.jobs.ProfileUploadJob
import com.red.sovereign.jobs.RefreshAttributesJob
import com.red.sovereign.jobs.RefreshSvrCredentialsJob
import com.red.sovereign.jobs.RequestGroupV2InfoJob
import com.red.sovereign.jobs.ResetSvrGuessCountJob
import com.red.sovereign.jobs.RestoreOptimizedMediaJob
import com.red.sovereign.jobs.RetrieveProfileAvatarJob
import com.red.sovereign.jobs.RetrieveProfileJob
import com.red.sovereign.jobs.RetrieveRemoteAnnouncementsJob
import com.red.sovereign.jobs.RotateCertificateJob
import com.red.sovereign.jobs.StickerPackDownloadJob
import com.red.sovereign.jobs.StorageSyncJob
import com.red.sovereign.jobs.StoryOnboardingDownloadJob

/**
 * A [Job] that does nothing and always succeeds. Test setups substitute this for jobs whose
 * real implementations would hit the network at startup (and so would either generate noise
 * against the [DeviceTransferBlockingInterceptor][com.red.sovereign.net.DeviceTransferBlockingInterceptor]
 * or fail against unstubbed mocks). Use [replaceFactories] to apply the swap.
 */
class NoOpJob(parameters: Parameters) : Job(parameters) {
  override fun serialize(): ByteArray? = null
  override fun getFactoryKey(): String = KEY
  override fun run(): Result = Result.success()
  override fun onFailure() = Unit

  class Factory : Job.Factory<NoOpJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): NoOpJob = NoOpJob(parameters)
  }

  companion object {
    const val KEY = "NoOpJob"

    private val STARTUP_NETWORK_JOB_KEYS: Set<String> = setOf(
      AccountConsistencyWorkerJob.KEY,
      ArchiveBackupIdReservationJob.KEY,
      AvatarGroupsV2DownloadJob.KEY,
      CreateReleaseChannelJob.KEY,
      DirectoryRefreshJob.KEY,
      DownloadLatestEmojiDataJob.KEY,
      EmojiSearchIndexDownloadJob.KEY,
      FontDownloaderJob.KEY,
      GroupRingCleanupJob.KEY,
      GroupV2UpdateSelfProfileKeyJob.KEY,
      LinkedDeviceInactiveCheckJob.KEY,
      MultiDeviceProfileKeyUpdateJob.KEY,
      PostRegistrationBackupRedemptionJob.KEY,
      PreKeysSyncJob.KEY,
      ProfileUploadJob.KEY,
      RefreshAttributesJob.KEY,
      RefreshSvrCredentialsJob.KEY,
      RequestGroupV2InfoJob.KEY,
      ResetSvrGuessCountJob.KEY,
      RestoreOptimizedMediaJob.KEY,
      RetrieveProfileAvatarJob.KEY,
      RetrieveProfileJob.KEY,
      RetrieveRemoteAnnouncementsJob.KEY,
      RotateCertificateJob.KEY,
      StickerPackDownloadJob.KEY,
      StorageSyncJob.KEY,
      StoryOnboardingDownloadJob.KEY
    )

    fun replaceFactories(factories: Map<String, Job.Factory<*>>): Map<String, Job.Factory<*>> = factories.mapValues { if (it.key in STARTUP_NETWORK_JOB_KEYS) Factory() else it.value }
  }
}
