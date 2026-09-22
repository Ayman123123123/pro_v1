/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2

import android.app.PendingIntent
import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.annotation.Discouraged
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.greenrobot.eventbus.EventBus
import org.signal.archive.proto.BackupDebugInfo
import org.signal.archive.proto.BackupInfo
import org.signal.archive.proto.Frame
import org.signal.archive.stream.BackupExportWriter
import org.signal.archive.stream.BackupImportReader
import org.signal.archive.stream.EncryptedBackupReader
import org.signal.archive.stream.EncryptedBackupWriter
import org.signal.archive.stream.PlainTextBackupReader
import org.signal.archive.stream.PlainTextBackupWriter
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.models.backup.BackupId
import org.signal.core.models.backup.MediaName
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64
import org.signal.core.util.Base64.decodeBase64OrThrow
import org.signal.core.util.CursorUtil
import org.signal.core.util.DiskUtil
import org.signal.core.util.EventTimer
import org.signal.core.util.PendingIntentFlags.cancelCurrent
import org.signal.core.util.ServiceUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.bytes
import org.signal.core.util.concurrent.LimitedWorker
import org.signal.core.util.concurrent.REDDispatchers
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.crypto.AttachmentSecretProvider
import org.signal.core.util.decodeOrNull
import org.signal.core.util.forceForeignKeyConstraintsEnabled
import org.signal.core.util.fullWalCheckpoint
import org.signal.core.util.getAllIndexDefinitions
import org.signal.core.util.getAllTableDefinitions
import org.signal.core.util.getAllTriggerDefinitions
import org.signal.core.util.getForeignKeyViolations
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.logW
import org.signal.core.util.money.FiatMoney
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireIntOrNull
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireString
import org.signal.core.util.stream.NonClosingOutputStream
import org.signal.core.util.urlEncode
import org.signal.core.util.withinTransaction
import org.signal.libsignal.messagebackup.BackupForwardSecrecyToken
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.backups.BackupLevel
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.network.ApplicationErrorAction
import org.signal.network.NetworkResult
import org.signal.network.StatusCodeErrorAction
import org.signal.network.api.SvrBApi
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import com.red.sovereign.R
import com.red.sovereign.attachments.Cdn
import com.red.sovereign.attachments.DatabaseAttachment
import com.red.sovereign.backup.ArchiveUploadProgress
import com.red.sovereign.backup.DeletionState
import com.red.sovereign.backup.v2.BackupRepository.copyAttachmentToArchive
import com.red.sovereign.backup.v2.BackupRepository.exportForDebugging
import com.red.sovereign.backup.v2.importer.ChatItemArchiveImporter
import com.red.sovereign.backup.v2.processor.AccountDataArchiveProcessor
import com.red.sovereign.backup.v2.processor.AdHocCallArchiveProcessor
import com.red.sovereign.backup.v2.processor.ChatArchiveProcessor
import com.red.sovereign.backup.v2.processor.ChatFolderArchiveProcessor
import com.red.sovereign.backup.v2.processor.ChatItemArchiveProcessor
import com.red.sovereign.backup.v2.processor.NotificationProfileArchiveProcessor
import com.red.sovereign.backup.v2.processor.RecipientArchiveProcessor
import com.red.sovereign.backup.v2.processor.StickerArchiveProcessor
import com.red.sovereign.backup.v2.ui.BackupAlert
import com.red.sovereign.backup.v2.ui.subscription.MessageBackupsType
import com.red.sovereign.components.settings.app.AppSettingsActivity
import com.red.sovereign.components.settings.app.subscription.RecurringInAppPaymentRepository
import com.red.sovereign.crypto.AppAttachmentSecretStore
import com.red.sovereign.crypto.DatabaseSecretProvider
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.database.BackupMediaSnapshotTable.ArchiveMediaItem
import com.red.sovereign.database.KeyValueDatabase
import com.red.sovereign.database.KyberPreKeyTable
import com.red.sovereign.database.OneTimePreKeyTable
import com.red.sovereign.database.SearchTable
import com.red.sovereign.database.SessionTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.SignedPreKeyTable
import com.red.sovereign.database.StickerTables
import com.red.sovereign.database.ThreadTable
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.GroupId
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.DataRestoreConstraint
import com.red.sovereign.jobs.ArchiveAttachmentBackfillJob
import com.red.sovereign.jobs.ArchiveThumbnailBackfillJob
import com.red.sovereign.jobs.ArchiveThumbnailUploadJob
import com.red.sovereign.jobs.AvatarGroupsV2DownloadJob
import com.red.sovereign.jobs.BackfillCollapsedMessageJob
import com.red.sovereign.jobs.BackupDeleteJob
import com.red.sovereign.jobs.BackupMessagesJob
import com.red.sovereign.jobs.BackupRestoreMediaJob
import com.red.sovereign.jobs.CancelRestoreMediaJob
import com.red.sovereign.jobs.CreateReleaseChannelJob
import com.red.sovereign.jobs.LocalBackupJob
import com.red.sovereign.jobs.MultiDeviceKeysUpdateJob
import com.red.sovereign.jobs.RequestGroupV2InfoJob
import com.red.sovereign.jobs.ResetSvrGuessCountJob
import com.red.sovereign.jobs.RestoreOptimizedMediaJob
import com.red.sovereign.jobs.RetrieveProfileJob
import com.red.sovereign.jobs.StickerPackDownloadJob
import com.red.sovereign.jobs.StorageForcePushJob
import com.red.sovereign.jobs.Svr2MirrorJob
import com.red.sovereign.jobs.UploadAttachmentToArchiveJob
import com.red.sovereign.keyvalue.BackupValues
import com.red.sovereign.keyvalue.BackupValues.ArchiveServiceCredentials
import com.red.sovereign.keyvalue.KeyValueStore
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.isDecisionPending
import com.red.sovereign.keyvalue.protos.ArchiveUploadProgressState
import com.red.sovereign.logsubmit.SubmitDebugLogRepository
import com.red.sovereign.net.REDNetwork
import com.red.sovereign.notifications.NotificationChannels
import com.red.sovereign.notifications.NotificationIds
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.service.BackupMediaRestoreService
import com.red.sovereign.service.BackupProgressService
import com.red.sovereign.storage.StorageSyncHelper
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.util.TextSecurePreferences
import com.red.sovereign.util.toMillis
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse
import org.whispersystems.signalservice.api.archive.ArchiveKeyRotationLimitResponse
import org.whispersystems.signalservice.api.archive.ArchiveMediaRequest
import org.whispersystems.signalservice.api.archive.ArchiveMediaResponse
import org.whispersystems.signalservice.api.archive.ArchiveServiceAccess
import org.whispersystems.signalservice.api.archive.ArchiveServiceAccessPair
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.archive.DeleteArchivedMediaRequest
import org.whispersystems.signalservice.api.archive.GetArchiveCdnCredentialsResponse
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.link.TransferArchiveResponse
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.REDServiceAttachment.ProgressListener
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.Currency
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.signal.registration.R as RegistrationR

object BackupRepository {

  private val TAG = Log.tag(BackupRepository::class.java)
  const val VERSION = 1L
  private const val REMOTE_MAIN_DB_SNAPSHOT_NAME = "remote-signal-snapshot"
  private const val REMOTE_KEYVALUE_DB_SNAPSHOT_NAME = "remote-signal-key-value-snapshot"
  private const val LOCAL_MAIN_DB_SNAPSHOT_NAME = "local-signal-snapshot"
  private const val LOCAL_KEYVALUE_DB_SNAPSHOT_NAME = "local-signal-key-value-snapshot"
  private const val RECENT_RECIPIENTS_MAX = 50
  private val MANUAL_BACKUP_NOTIFICATION_THRESHOLD = 30.days

  private val resetInitializedStateErrorAction: StatusCodeErrorAction = { error ->
    when (error.code) {
      401 -> {
        Log.w(TAG, "Received status 401. Resetting initialized state + auth credentials.", error.exception)
        resetInitializedStateAndAuthCredentials()
      }

      403 -> {
        if (REDStore.backup.backupTierInternalOverride != null) {
          Log.w(TAG, "Received status 403, but the internal override is set, so not doing anything.", error.exception)
        } else {
          Log.w(TAG, "Received status 403. The user is not in the media tier. Updating local state.", error.exception)
          if (REDStore.backup.backupTier == MessageBackupTier.PAID) {
            Log.w(TAG, "Local device thought it was on PAID tier. Downgrading to FREE tier.")
            REDStore.backup.backupTier = MessageBackupTier.FREE
            REDStore.backup.backupExpiredAndDowngraded = true
            scheduleSyncForAccountChange()
          }

          REDStore.uiHints.markHasEverEnabledRemoteBackups()
        }
      }
    }
  }

  private val clearAuthCredentials: ApplicationErrorAction = { error ->
    if (error.getCause() is VerificationFailedException) {
      Log.w(TAG, "Unable to verify/receive credentials, clearing cache to fetch new.", error.getCause())
      REDStore.backup.messageCredentials.clearAll()
      REDStore.backup.mediaCredentials.clearAll()
    }
  }

  /**
   * Generates a new AEP that the user can choose to confirm.
   */
  @CheckResult
  fun stageBackupKeyRotations(): StagedBackupKeyRotations {
    return StagedBackupKeyRotations(
      aep = AccountEntropyPool.generate(),
      mediaRootBackupKey = MediaRootBackupKey.generate()
    )
  }

  /**
   * Saves the AEP to the local storage and kicks off a backup upload.
   */
  suspend fun commitAEPKeyRotation(stagedKeyRotations: StagedBackupKeyRotations) {
    haltAllJobs()
    resetInitializedStateAndAuthCredentials()
    REDStore.account.rotateAccountEntropyPool(stagedKeyRotations.aep)
    REDStore.backup.mediaRootBackupKey = stagedKeyRotations.mediaRootBackupKey
    refreshMasterKeyDependents()
    BackupMessagesJob.enqueue()
  }

  private fun refreshMasterKeyDependents() {
    val jobs = buildList {
      add(Svr2MirrorJob())
      if (REDStore.account.isMultiDevice) {
        add(MultiDeviceKeysUpdateJob())
      }
      add(StorageForcePushJob())
    }

    AppDependencies.jobManager.addAll(jobs)
  }

  fun resetInitializedStateAndAuthCredentials() {
    REDStore.backup.backupsInitialized = false
    REDStore.backup.messageCredentials.clearAll()
    REDStore.backup.mediaCredentials.clearAll()
    REDStore.backup.cachedMediaCdnPath = null
  }

  private suspend fun haltAllJobs() {
    ArchiveUploadProgress.cancelAndBlock()
    AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)

    Log.d(TAG, "Waiting for local backup job cancelations to occur...")
    while (!AppDependencies.jobManager.areQueuesEmpty(setOf(LocalBackupJob.QUEUE))) {
      delay(1.seconds)
    }
  }

  /**
   * Triggers backup id reservation. As documented, this is safe to perform multiple times.
   */
  @WorkerThread
  fun triggerBackupIdReservation(): NetworkResult<Unit> {
    val messageBackupKey = REDStore.backup.messageBackupKey
    val mediaRootBackupKey = REDStore.backup.mediaRootBackupKey
    return REDNetwork.archive.triggerBackupIdReservation(messageBackupKey, mediaRootBackupKey, REDStore.account.requireAci())
      .runIfSuccessful {
        REDStore.backup.messageCredentials.clearAll()
        REDStore.backup.mediaCredentials.clearAll()
      }
  }

  @WorkerThread
  fun triggerBackupIdReservationForRestore(): NetworkResult<Unit> {
    val messageBackupKey = REDStore.backup.messageBackupKey
    return REDNetwork.archive.triggerBackupIdReservation(messageBackupKey, null, REDStore.account.requireAci())
      .runIfSuccessful {
        REDStore.backup.messageCredentials.clearAll()
      }
  }

  /**
   * Refreshes backup via server
   */
  fun refreshBackup(): NetworkResult<Unit> {
    Log.d(TAG, "Refreshing backup...")

    Log.d(TAG, "Fetching backup auth credential.")
    val credentialResult = initBackupAndFetchAuth()
    if (credentialResult.getCause() != null) {
      Log.w(TAG, "Failed to access backup auth.", credentialResult.getCause())
      return credentialResult.map { Unit }
    }

    val credential = credentialResult.successOrThrow()

    Log.d(TAG, "Fetched backup auth credential. Fetching backup tier.")

    val backupTierResult = getBackupTier()
    if (backupTierResult.getCause() != null) {
      Log.w(TAG, "Failed to access backup tier.", backupTierResult.getCause())
      return backupTierResult.map { Unit }
    }

    val backupTier = backupTierResult.successOrThrow()

    Log.d(TAG, "Fetched backup tier. Refreshing message backup access.")
    val messageBackupAccessResult = AppDependencies.archiveApi.refreshBackup(
      aci = REDStore.account.requireAci(),
      archiveServiceAccess = credential.messageBackupAccess
    )

    if (messageBackupAccessResult.getCause() != null) {
      Log.d(TAG, "Failed to refresh message backup access.", messageBackupAccessResult.getCause())
      return messageBackupAccessResult
    }

    Log.d(TAG, "Refreshed message backup access.")
    if (backupTier == MessageBackupTier.PAID) {
      Log.d(TAG, "Refreshing media backup access.")

      val mediaBackupAccessResult = AppDependencies.archiveApi.refreshBackup(
        aci = REDStore.account.requireAci(),
        archiveServiceAccess = credential.mediaBackupAccess
      )

      if (mediaBackupAccessResult.getCause() != null) {
        Log.d(TAG, "Failed to refresh media backup access.", mediaBackupAccessResult.getCause())
      }

      Log.d(TAG, "Refreshed media backup access.")

      return mediaBackupAccessResult
    } else {
      return messageBackupAccessResult
    }
  }

  /**
   * Checks whether or not we do not have enough storage space for our remaining attachments to be downloaded.
   * Caller from the attachment / thumbnail download jobs.
   */
  fun checkForOutOfStorageError(tag: String): Boolean {
    val availableSpace = DiskUtil.getAvailableSpace(AppDependencies.application)
    val remainingAttachmentSize = REDDatabase.attachments.getRemainingRestorableAttachmentSize().bytes

    return if (availableSpace < remainingAttachmentSize) {
      Log.w(tag, "Possibly out of space. ${availableSpace.toUnitString()} available.", true)
      REDStore.backup.spaceAvailableOnDiskBytes = availableSpace.bytes
      true
    } else {
      false
    }
  }

  @JvmStatic
  fun resumeMediaRestore() {
    REDStore.backup.userManuallySkippedMediaRestore = false
    RestoreOptimizedMediaJob.enqueue()
  }

  /**
   * Cancels any relevant jobs for media restore
   */
  @JvmStatic
  fun skipMediaRestore() {
    CancelRestoreMediaJob.enqueue()
  }

  fun markBackupCreationFailed(error: BackupValues.BackupCreationError) {
    REDStore.backup.markBackupCreationFailed(error)
    ArchiveUploadProgress.onMainBackupFileUploadFailure()

    if (!REDStore.backup.hasBackupBeenUploaded) {
      Log.w(TAG, "Failure of initial backup. Displaying notification.")
      displayInitialBackupFailureNotification()
    }
  }

  @Discouraged("This is only public to allow internal settings to call it directly.")
  fun displayInitialBackupFailureNotification() {
    val context = AppDependencies.application

    val pendingIntent = PendingIntent.getActivity(context, 0, AppSettingsActivity.remoteBackups(context), cancelCurrent())
    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_ALERTS)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.Notification_backup_failed))
      .setContentText(context.getString(R.string.Notification_an_error_occurred_and_your_backup))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    ServiceUtil.getNotificationManager(context).notify(NotificationIds.INITIAL_BACKUP_FAILED, notification)
  }

  fun clearBackupFailure() {
    REDStore.backup.backupCreationError = null
    ServiceUtil.getNotificationManager(AppDependencies.application).cancel(NotificationIds.INITIAL_BACKUP_FAILED)
  }

  fun markOutOfRemoteStorageSpaceError() {
    if (REDStore.backup.isNotEnoughRemoteStorageSpace) {
      return
    }

    REDStore.backup.markNotEnoughRemoteStorageSpace()

    val context = AppDependencies.application

    val pendingIntent = PendingIntent.getActivity(context, 0, AppSettingsActivity.remoteBackups(context), cancelCurrent())
    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().APP_ALERTS)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(R.string.Notification_backup_storage_full))
      .setContentText(context.getString(R.string.Notification_youve_reached_your_backup_storage_limit))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    ServiceUtil.getNotificationManager(context).notify(NotificationIds.OUT_OF_REMOTE_STORAGE, notification)
  }

  fun clearOutOfRemoteStorageSpaceError() {
    REDStore.backup.clearNotEnoughRemoteStorageSpace()
    ServiceUtil.getNotificationManager(AppDependencies.application).cancel(NotificationIds.OUT_OF_REMOTE_STORAGE)
  }

  fun shouldDisplayOutOfRemoteStorageSpaceUx(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return REDStore.backup.isNotEnoughRemoteStorageSpace
  }

  fun shouldDisplayOutOfRemoteStorageSpaceSheet(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return REDStore.backup.shouldDisplayNotEnoughRemoteStorageSpaceSheet
  }

  fun dismissOutOfRemoteStorageSpaceSheet() {
    REDStore.backup.dismissNotEnoughRemoteStorageSpaceSheet()
  }

  /**
   * Whether the yellow dot should be displayed on the conversation list avatar.
   */
  @JvmStatic
  fun shouldDisplayBackupFailedIndicator(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging() || !REDStore.backup.hasBackupCreationError) {
      return false
    }

    val now = System.currentTimeMillis().milliseconds
    val alertAfter = REDStore.backup.nextBackupFailureSnoozeTime

    return alertAfter <= now
  }

  @JvmStatic
  fun shouldDisplayBackupAlreadyRedeemedIndicator(): Boolean {
    return !(shouldNotDisplayBackupFailedMessaging() || !REDStore.backup.hasBackupAlreadyRedeemedError)
  }

  /**
   * Displayed when the user falls out of the grace period for backups after their subscription
   * expires.
   */
  fun shouldDisplayBackupExpiredAndDowngradedSheet(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return REDStore.backup.backupExpiredAndDowngraded
  }

  fun markBackupAlreadyRedeemedIndicatorClicked() {
    REDStore.backup.hasBackupAlreadyRedeemedError = false
  }

  /**
   * Updates the watermark for the indicator display.
   */
  @JvmStatic
  fun markBackupFailedIndicatorClicked() {
    REDStore.backup.updateMessageBackupFailureWatermark()
  }

  /**
   * Updates the watermark for the sheet display.
   */
  fun markBackupFailedSheetDismissed() {
    REDStore.backup.updateMessageBackupFailureSheetWatermark()
  }

  /**
   * User closed backup expiration alert sheet
   */
  fun markBackupExpiredAndDowngradedSheetDismissed() {
    REDStore.backup.backupExpiredAndDowngraded = false
  }

  /**
   * Whether or not the "Backup failed" sheet should be displayed.
   * Should only be displayed if this is the failure of the initial backup creation.
   */
  @JvmStatic
  fun shouldDisplayBackupFailedSheet(): Boolean {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    return REDStore.backup.hasBackupCreationError && REDStore.backup.backupCreationError != BackupValues.BackupCreationError.TRANSIENT && System.currentTimeMillis().milliseconds > REDStore.backup.nextBackupFailureSheetSnoozeTime
  }

  /**
   * Whether or not the "Could not complete backup" sheet should be displayed.
   */
  @JvmStatic
  fun shouldDisplayCouldNotCompleteBackupSheet(): Boolean {
    // Temporarily disabling. May re-enable in the future.
    if (true) {
      return false
    }

    if (shouldNotDisplayBackupFailedMessaging()) {
      return false
    }

    val isRegistered = REDStore.account.isRegistered && !TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application)
    if (!isRegistered) {
      Log.d(TAG, "[shouldDisplayCouldNotCompleteBackupSheet] Not displaying sheet for unregistered user.")
      return false
    }

    if (REDStore.backup.lastBackupTime <= 0) {
      Log.d(TAG, "[shouldDisplayCouldNotCompleteBackupSheet] Not displaying sheet as the last backup time is unset.")
      return false
    }

    if (!REDStore.backup.hasBackupBeenUploaded) {
      Log.d(TAG, "[shouldDisplayCouldNotCompleteBackupSheet] Not displaying sheet as a backup has never been uploaded.")
      return false
    }

    val now = System.currentTimeMillis().milliseconds
    val lastBackupTime = REDStore.backup.lastBackupTime.milliseconds
    val nextSnoozeTime = REDStore.backup.nextBackupFailureSnoozeTime

    val isLastBackupTimeAtLeastAWeekAgo = now - 7.days > lastBackupTime
    if (!isLastBackupTimeAtLeastAWeekAgo) {
      Log.d(TAG, "[shouldDisplayCouldNotCompleteBackupSheet] Not displaying sheet as the last backup time is less than a week ago.")
      return false
    }

    val isNextSnoozeTimeBeforeNow = nextSnoozeTime < now
    if (!isNextSnoozeTimeBeforeNow) {
      Log.d(TAG, "[shouldDisplayCouldNotCompleteBackupSheet] Not displaying sheet as the next snooze time is in the future.")
      return false
    }

    return true
  }

  fun snoozeDownloadYourBackupData() {
    REDStore.backup.snoozeDownloadNotifier()
  }

  @JvmStatic
  fun maybeFixAnyDanglingUploadProgress() {
    if (REDStore.account.isLinkedDevice) {
      return
    }

    if (REDStore.backup.archiveUploadState?.backupPhase == ArchiveUploadProgressState.BackupPhase.Message && AppDependencies.jobManager.find { it.factoryKey == BackupMessagesJob.KEY }.isEmpty()) {
      Log.w(TAG, "Found a situation where message backup was in progress, but there's no active BackupMessageJob! Re-enqueueing.")
      REDStore.backup.archiveUploadState = null
      BackupMessagesJob.enqueue()
      return
    }

    if (!REDStore.backup.backsUpMedia) {
      return
    }

    if (!AppDependencies.jobManager.areQueuesEmpty(UploadAttachmentToArchiveJob.QUEUES)) {
      if (REDStore.backup.archiveUploadState?.state == ArchiveUploadProgressState.State.None) {
        Log.w(TAG, "Found a situation where attachment uploads are in progress, but the progress state was None! Fixing.")
        ArchiveUploadProgress.onAttachmentSectionStarted(REDDatabase.attachments.getPendingArchiveUploadBytes())
      }
      return
    }

    if (AppDependencies.jobManager.areQueuesEmpty(ArchiveThumbnailUploadJob.QUEUES) && REDDatabase.attachments.areAnyThumbnailsPendingUpload()) {
      Log.w(TAG, "Found a situation where there's no thumbnail jobs in progress, but thumbnails are in the pending upload state! Clearing the pending state and re-enqueueing.")
      REDDatabase.attachments.clearArchiveThumbnailTransferStateForInProgressItems()
      AppDependencies.jobManager.add(ArchiveThumbnailBackfillJob())
    }

    val pendingBytes = REDDatabase.attachments.getPendingArchiveUploadBytes()
    if (pendingBytes == 0L) {
      return
    }

    Log.w(TAG, "There are ${pendingBytes.bytes.toUnitString(maxPlaces = 2)} of attachments that need to be uploaded to the archive, but no jobs for them! Attempting to fix.")
    val resetCount = REDDatabase.attachments.clearArchiveTransferStateForInProgressItems()
    Log.w(TAG, "Cleared the archive transfer state of $resetCount attachments.")
    AppDependencies.jobManager.add(ArchiveAttachmentBackfillJob())
  }

  /**
   * Whether or not the "Your media will be deleted today" sheet should be displayed.
   */
  suspend fun getDownloadYourBackupData(): BackupAlert.DownloadYourBackupData? {
    if (shouldNotDisplayBackupFailedMessaging()) {
      return null
    }

    val state = REDStore.backup.backupDownloadNotifierState ?: return null
    val nextSheetDisplayTime = state.lastSheetDisplaySeconds.seconds + state.intervalSeconds.seconds

    val remainingAttachmentSize = withContext(REDDispatchers.Default) {
      REDDatabase.attachments.getRemainingRestorableAttachmentSize()
    }

    if (remainingAttachmentSize <= 0L) {
      REDStore.backup.clearDownloadNotifierState()
      return null
    }

    val now = System.currentTimeMillis().milliseconds

    return if (nextSheetDisplayTime <= now) {
      val lastDay = state.entitlementExpirationSeconds.seconds - 1.days

      BackupAlert.DownloadYourBackupData(
        isLastDay = now >= lastDay,
        formattedSize = remainingAttachmentSize.bytes.toUnitString(),
        type = state.type
      )
    } else {
      null
    }
  }

  fun shouldNotDisplayBackupFailedMessaging(): Boolean {
    return !REDStore.account.isRegistered || !REDStore.backup.areBackupsEnabled
  }

  /**
   * Initiates backup disable via [BackupDeleteJob]
   */
  suspend fun turnOffAndDisableBackups() {
    ArchiveUploadProgress.cancelAndBlock()
    REDStore.backup.userManuallySkippedMediaRestore = false
    REDStore.backup.deletionState = DeletionState.CLEAR_LOCAL_STATE
    AppDependencies.jobManager.add(BackupDeleteJob())
  }

  /**
   * To be called if the user skips media restore during the deletion process.
   */
  fun continueTurningOffAndDisablingBackups() {
    AppDependencies.jobManager.add(BackupDeleteJob())
  }

  @WorkerThread
  private fun createREDDatabaseSnapshot(baseName: String): REDDatabase {
    // Need to do a WAL checkpoint to ensure that the database file we're copying has all pending writes
    if (!REDDatabase.rawDatabase.fullWalCheckpoint()) {
      Log.w(TAG, "Failed to checkpoint WAL for main database! Not guaranteed to be using the most recent data.")
    }

    // We make a copy of the database within a transaction to ensure that no writes occur while we're copying the file
    return REDDatabase.rawDatabase.withinTransaction {
      val context = AppDependencies.application

      val existingDbFile = context.getDatabasePath(REDDatabase.DATABASE_NAME)
      val targetFile = File(existingDbFile.parentFile, "$baseName.db")

      existingDbFile.parentFile?.deleteAllFilesWithPrefix(baseName)

      try {
        existingDbFile.copyTo(targetFile, overwrite = true)
      } catch (e: IOException) {
        // SIGNAL_INHERITED: TODO [backup] Gracefully handle this error
        throw IllegalStateException("Failed to copy database file!", e)
      }

      REDDatabase(
        context = context,
        databaseSecret = DatabaseSecretProvider.getOrCreateDatabaseSecret(context),
        attachmentSecret = AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore).getOrCreateAttachmentSecret(),
        name = "$baseName.db"
      )
    }
  }

  @WorkerThread
  private fun createREDStoreSnapshot(baseName: String): REDStore {
    val context = AppDependencies.application

    REDStore.blockUntilAllWritesFinished()

    // Need to do a WAL checkpoint to ensure that the database file we're copying has all pending writes
    if (!KeyValueDatabase.getInstance(context).writableDatabase.fullWalCheckpoint()) {
      Log.w(TAG, "Failed to checkpoint WAL for KeyValueDatabase! Not guaranteed to be using the most recent data.")
    }

    // We make a copy of the database within a transaction to ensure that no writes occur while we're copying the file
    return KeyValueDatabase.getInstance(context).writableDatabase.withinTransaction {
      val existingDbFile = context.getDatabasePath(KeyValueDatabase.DATABASE_NAME)
      val targetFile = File(existingDbFile.parentFile, "$baseName.db")

      existingDbFile.parentFile?.deleteAllFilesWithPrefix(baseName)

      try {
        existingDbFile.copyTo(targetFile, overwrite = true)
      } catch (e: IOException) {
        // SIGNAL_INHERITED: TODO [backup] Gracefully handle this error
        throw IllegalStateException("Failed to copy database file!", e)
      }

      val db = KeyValueDatabase.createWithName(context, "$baseName.db")
      REDStore(context, KeyValueStore(db))
    }
  }

  @WorkerThread
  private fun deleteDatabaseSnapshot(name: String) {
    AppDependencies.application.getDatabasePath("$name.db")
      .parentFile
      ?.deleteAllFilesWithPrefix(name)
  }

  @WorkerThread
  fun exportForLocalBackup(
    main: OutputStream,
    localBackupProgressEmitter: ExportProgressListener,
    cancellationRED: () -> Boolean = { false },
    archiveAttachment: (AttachmentTable.LocalArchivableAttachment, () -> InputStream?) -> Unit
  ) {
    val writer = EncryptedBackupWriter.createForLocalOrLinking(
      key = REDStore.backup.messageBackupKey,
      aci = REDStore.account.aci!!,
      outputStream = NonClosingOutputStream(main),
      append = { main.write(it) }
    )

    export(
      currentTime = System.currentTimeMillis(),
      isLocal = true,
      writer = writer,
      progressEmitter = localBackupProgressEmitter,
      cancellationRED = cancellationRED,
      backupMode = BackupMode.LOCAL,
      extraFrameOperation = null,
      messageInclusionCutoffTime = 0
    ) { dbSnapshot ->
      val localArchivableAttachments = dbSnapshot
        .attachmentTable
        .getLocalArchivableAttachments()
        .associateBy { MediaName.forLocalBackupFilename(it.plaintextHash, it.localBackupKey.key) }

      localBackupProgressEmitter.onAttachment(0, localArchivableAttachments.size.toLong())

      val progress = AtomicLong(0)

      LimitedWorker.execute(REDExecutors.BOUNDED_IO, 4, localArchivableAttachments.values) { attachment ->
        try {
          archiveAttachment(attachment) { dbSnapshot.attachmentTable.getAttachmentStream(attachment) }
        } catch (e: IOException) {
          Log.w(TAG, "Unable to open attachment, skipping", e)
        }

        val currentProgress = progress.incrementAndGet()
        localBackupProgressEmitter.onAttachment(currentProgress, localArchivableAttachments.size.toLong())
      }
    }
  }

  @WorkerThread
  fun exportForLocalPlaintextArchive(
    outputStream: OutputStream,
    progressEmitter: ExportProgressListener?,
    cancellationRED: () -> Boolean,
    includeMedia: Boolean
  ): List<AttachmentTable.LocalArchivableAttachment> {
    val writer = LibREDJsonBackupWriter(NonClosingOutputStream(outputStream))
    val collectedAttachments = mutableListOf<AttachmentTable.LocalArchivableAttachment>()

    export(
      currentTime = System.currentTimeMillis(),
      isLocal = true,
      writer = writer,
      backupMode = BackupMode.PLAINTEXT_EXPORT,
      progressEmitter = progressEmitter,
      cancellationRED = cancellationRED,
      extraFrameOperation = null,
      messageInclusionCutoffTime = 0
    ) { dbSnapshot ->
      if (includeMedia) {
        collectedAttachments.addAll(dbSnapshot.attachmentTable.getLocalArchivableAttachmentsForPlaintextExport())
      }
    }

    return collectedAttachments
  }

  /**
   * Export a backup that will be uploaded to the archive CDN.
   */
  fun exportForREDBackup(
    outputStream: OutputStream,
    append: (ByteArray) -> Unit,
    messageBackupKey: MessageBackupKey,
    forwardSecrecyToken: BackupForwardSecrecyToken,
    forwardSecrecyMetadata: ByteArray,
    currentTime: Long,
    messageInclusionCutoffTime: Long = 0,
    progressEmitter: ExportProgressListener? = null,
    cancellationRED: () -> Boolean = { false },
    extraFrameOperation: ((Frame) -> Unit)?
  ) {
    val writer = EncryptedBackupWriter.createForREDBackup(
      key = messageBackupKey,
      aci = REDStore.account.aci!!,
      outputStream = outputStream,
      forwardSecrecyToken = forwardSecrecyToken,
      forwardSecrecyMetadata = forwardSecrecyMetadata,
      append = append
    )

    return export(
      currentTime = currentTime,
      isLocal = false,
      writer = writer,
      backupMode = BackupMode.REMOTE,
      progressEmitter = progressEmitter,
      cancellationRED = cancellationRED,
      extraFrameOperation = extraFrameOperation,
      endingExportOperation = null,
      messageInclusionCutoffTime = messageInclusionCutoffTime
    )
  }

  /**
   * Export a backup that will be uploaded to the archive CDN.
   */
  fun exportForLinkAndSync(
    outputStream: OutputStream,
    append: (ByteArray) -> Unit,
    messageBackupKey: MessageBackupKey,
    currentTime: Long,
    progressEmitter: ExportProgressListener? = null,
    cancellationRED: () -> Boolean = { false }
  ) {
    val writer = EncryptedBackupWriter.createForLocalOrLinking(
      key = messageBackupKey,
      aci = REDStore.account.aci!!,
      outputStream = outputStream,
      append = append
    )

    return export(
      currentTime = currentTime,
      isLocal = false,
      writer = writer,
      backupMode = BackupMode.LINK_SYNC,
      progressEmitter = progressEmitter,
      cancellationRED = cancellationRED,
      extraFrameOperation = null,
      endingExportOperation = null,
      messageInclusionCutoffTime = 0
    )
  }

  @WorkerThread
  @JvmOverloads
  fun exportForDebugging(
    outputStream: OutputStream,
    append: (ByteArray) -> Unit,
    messageBackupKey: MessageBackupKey = REDStore.backup.messageBackupKey,
    plaintext: Boolean = false,
    currentTime: Long = System.currentTimeMillis(),
    progressEmitter: ExportProgressListener? = null,
    cancellationRED: () -> Boolean = { false }
  ) {
    val writer: BackupExportWriter = if (plaintext) {
      PlainTextBackupWriter(outputStream)
    } else {
      EncryptedBackupWriter.createForLocalOrLinking(
        key = messageBackupKey,
        aci = REDStore.account.aci!!,
        outputStream = outputStream,
        append = append
      )
    }

    export(
      currentTime = currentTime,
      isLocal = false,
      writer = writer,
      backupMode = BackupMode.REMOTE,
      progressEmitter = progressEmitter,
      cancellationRED = cancellationRED,
      extraFrameOperation = null,
      endingExportOperation = null,
      messageInclusionCutoffTime = 0
    )
  }

  /**
   * Exports to a blob in memory. Should only be used for testing.
   */
  @WorkerThread
  fun exportInMemoryForTests(plaintext: Boolean = false, currentTime: Long = System.currentTimeMillis()): ByteArray {
    val outputStream = ByteArrayOutputStream()
    exportForDebugging(outputStream = outputStream, append = { mac -> outputStream.write(mac) }, plaintext = plaintext, currentTime = currentTime)
    return outputStream.toByteArray()
  }

  @WorkerThread
  private fun export(
    currentTime: Long,
    isLocal: Boolean,
    writer: BackupExportWriter,
    backupMode: BackupMode,
    messageInclusionCutoffTime: Long,
    progressEmitter: ExportProgressListener?,
    cancellationRED: () -> Boolean,
    extraFrameOperation: ((Frame) -> Unit)?,
    endingExportOperation: ((REDDatabase) -> Unit)?
  ) {
    val eventTimer = EventTimer()
    val mainDbName = if (isLocal) LOCAL_MAIN_DB_SNAPSHOT_NAME else REMOTE_MAIN_DB_SNAPSHOT_NAME
    val keyValueDbName = if (isLocal) LOCAL_KEYVALUE_DB_SNAPSHOT_NAME else REMOTE_KEYVALUE_DB_SNAPSHOT_NAME

    try {
      val dbSnapshot: REDDatabase = createREDDatabaseSnapshot(mainDbName)
      eventTimer.emit("main-db-snapshot")

      val signalStoreSnapshot: REDStore = createREDStoreSnapshot(keyValueDbName)
      eventTimer.emit("store-db-snapshot")

      val selfAci = signalStoreSnapshot.accountValues.aci!!
      val selfRecipientId = dbSnapshot.recipientTable.getByAci(selfAci).get().toLong().let { RecipientId.from(it) }
      val exportState = ExportState(backupTime = currentTime, backupMode = backupMode, selfRecipientId = selfRecipientId)

      var frameCount = 0L

      writer.use {
        val debugInfo = buildDebugInfo()
        eventTimer.emit("debug-info")

        writer.write(
          BackupInfo(
            version = VERSION,
            backupTimeMs = exportState.backupTime,
            mediaRootBackupKey = REDStore.backup.mediaRootBackupKey.value.toByteString(),
            firstAppVersion = REDStore.backup.firstAppVersion,
            debugInfo = debugInfo
          )
        )
        frameCount++
        eventTimer.emit("header")

        // We're using a snapshot, so the transaction is more for perf than correctness
        dbSnapshot.rawWritableDatabase.withinTransaction {
          progressEmitter?.onAccount()
          AccountDataArchiveProcessor.export(dbSnapshot, signalStoreSnapshot, exportState) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("account")
            frameCount++
          }
          if (cancellationRED()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onRecipient()
          RecipientArchiveProcessor.export(dbSnapshot, signalStoreSnapshot, exportState, selfAci) {
            writer.write(it)
            extraFrameOperation?.invoke(it)
            eventTimer.emit("recipient")
            frameCount++
          }
          if (cancellationRED()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onThread()
          ChatArchiveProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("thread")
            frameCount++
          }
          if (cancellationRED()) {
            return@export
          }

          progressEmitter?.onCall()
          AdHocCallArchiveProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("call")
            frameCount++
          }
          if (cancellationRED()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onSticker()
          StickerArchiveProcessor.export(dbSnapshot) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("sticker-pack")
            frameCount++
          }
          if (cancellationRED()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onNotificationProfile()
          NotificationProfileArchiveProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("notification-profile")
            frameCount++
          }
          if (cancellationRED()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          progressEmitter?.onChatFolder()
          ChatFolderArchiveProcessor.export(dbSnapshot, exportState) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("chat-folder")
            frameCount++
          }
          if (cancellationRED()) {
            Log.w(TAG, "[export] Cancelled! Stopping")
            return@export
          }

          val approximateMessageCount = dbSnapshot.messageTable.getApproximateExportableMessageCount(exportState.threadIds)
          val frameCountStart = frameCount
          progressEmitter?.onMessage(0, approximateMessageCount)
          ChatItemArchiveProcessor.export(dbSnapshot, exportState, selfRecipientId, messageInclusionCutoffTime, cancellationRED) { frame ->
            writer.write(frame)
            extraFrameOperation?.invoke(frame)
            eventTimer.emit("message")
            frameCount++

            if (frameCount % 1000 == 0L) {
              Log.d(TAG, "[export] Exported $frameCount frames so far.")
              progressEmitter?.onMessage(frameCount - frameCountStart, approximateMessageCount)
              if (cancellationRED()) {
                Log.w(TAG, "[export] Cancelled! Stopping")
                return@export
              }
            }
          }
        }
      }

      endingExportOperation?.invoke(dbSnapshot)

      Log.d(TAG, "[export] totalFrames: $frameCount | ${eventTimer.stop().summary}")
    } finally {
      deleteDatabaseSnapshot(mainDbName)
      deleteDatabaseSnapshot(keyValueDbName)
    }
  }

  /**
   * Imports a local backup file that was exported to disk.
   */
  fun importLocal(mainStreamFactory: () -> InputStream, mainStreamLength: Long, selfData: SelfData, backupId: BackupId, messageBackupKey: MessageBackupKey): ImportResult {
    val backupKey = messageBackupKey

    val frameReader = try {
      EncryptedBackupReader.createForLocalOrLinking(
        key = backupKey,
        backupId = backupId,
        length = mainStreamLength,
        dataStream = mainStreamFactory
      )
    } catch (e: IOException) {
      Log.w(TAG, "Unable to import local archive", e)
      return ImportResult.Failure
    }

    return frameReader.use { reader ->
      import(reader, selfData, backupMode = BackupMode.LOCAL, cancellationRED = { false })
    }
  }

  /**
   * Imports a backup stored on the archive CDN.
   *
   * @param backupKey  The key used to encrypt the backup. If `null`, we assume that the file is plaintext.
   */
  fun importREDBackup(
    length: Long,
    inputStreamFactory: () -> InputStream,
    selfData: SelfData,
    backupKey: MessageBackupKey?,
    forwardSecrecyToken: BackupForwardSecrecyToken,
    cancellationRED: () -> Boolean = { false }
  ): ImportResult {
    try {
      val frameReader = if (backupKey == null) {
        PlainTextBackupReader(inputStreamFactory(), length)
      } else {
        EncryptedBackupReader.createForREDBackup(
          key = backupKey,
          aci = selfData.aci,
          forwardSecrecyToken = forwardSecrecyToken,
          length = length,
          dataStream = inputStreamFactory
        )
      }

      return frameReader.use { reader ->
        import(reader, selfData, backupMode = BackupMode.REMOTE, cancellationRED = cancellationRED)
      }
    } catch (e: IOException) {
      Log.w(TAG, "Unable to restore signal backup", e)
      return ImportResult.Failure
    }
  }

  /**
   * Imports a link and sync backup stored on the transit CDN.
   *
   * @param backupKey  The key used to encrypt the backup. If `null`, we assume that the file is plaintext.
   */
  fun importLinkAndSyncREDBackup(
    length: Long,
    inputStreamFactory: () -> InputStream,
    selfData: SelfData,
    backupKey: MessageBackupKey,
    cancellationRED: () -> Boolean = { false }
  ): ImportResult {
    val frameReader = EncryptedBackupReader.createForLocalOrLinking(
      key = backupKey,
      aci = selfData.aci,
      length = length,
      dataStream = inputStreamFactory
    )

    return frameReader.use { reader ->
      import(reader, selfData, backupMode = BackupMode.LINK_SYNC, cancellationRED = cancellationRED)
    }
  }

  /**
   * Imports a backup that was exported via [exportForDebugging].
   */
  fun importForDebugging(
    length: Long,
    inputStreamFactory: () -> InputStream,
    selfData: SelfData,
    backupKey: MessageBackupKey?,
    cancellationRED: () -> Boolean = { false }
  ): ImportResult {
    val frameReader = if (backupKey == null) {
      PlainTextBackupReader(inputStreamFactory(), length)
    } else {
      EncryptedBackupReader.createForLocalOrLinking(
        key = backupKey,
        aci = selfData.aci,
        length = length,
        dataStream = inputStreamFactory
      )
    }

    return frameReader.use { reader ->
      import(reader, selfData, backupMode = BackupMode.REMOTE, cancellationRED = cancellationRED)
    }
  }

  /**
   * Imports a plaintext backup only used for testing.
   */
  fun importPlaintextTest(
    length: Long,
    inputStreamFactory: () -> InputStream,
    selfData: SelfData,
    cancellationRED: () -> Boolean = { false }
  ): ImportResult {
    val frameReader = PlainTextBackupReader(inputStreamFactory(), length)

    return frameReader.use { reader ->
      import(reader, selfData, backupMode = BackupMode.PLAINTEXT_EXPORT, cancellationRED = cancellationRED)
    }
  }

  private fun import(
    frameReader: BackupImportReader,
    selfData: SelfData,
    backupMode: BackupMode,
    cancellationRED: () -> Boolean
  ): ImportResult {
    val stopwatch = Stopwatch("import")
    val eventTimer = EventTimer()

    val header = frameReader.getHeader()
    if (header == null) {
      Log.e(TAG, "[import] Backup is missing header!")
      REDStore.backup.hasInvalidBackupVersion = false
      return ImportResult.Failure
    } else if (header.version > VERSION) {
      Log.e(TAG, "[import] Backup version is newer than we understand: ${header.version}")
      REDStore.backup.hasInvalidBackupVersion = true
      return ImportResult.Failure
    }
    REDStore.backup.hasInvalidBackupVersion = false
    val selfId: RecipientId

    var transactionSuccessful = false
    try {
      // Removing all the data from the various tables is *very* expensive (i.e. can take *several* minutes) if we don't do some pre-work.
      // SQLite optimizes deletes if there's no foreign keys, triggers, or WHERE clause, so that's the environment we're gonna create.

      Log.d(TAG, "[import] Disabling foreign keys...")
      REDDatabase.rawDatabase.forceForeignKeyConstraintsEnabled(false)

      Log.d(TAG, "[import] Acquiring transaction...")
      REDDatabase.rawDatabase.beginTransaction()

      Log.d(TAG, "[import] Inside transaction.")
      stopwatch.split("get-transaction")

      Log.d(TAG, "[import] --- Dropping all indices ---")
      val indexMetadata = REDDatabase.rawDatabase.getAllIndexDefinitions()
      for (index in indexMetadata) {
        Log.d(TAG, "[import] Dropping index ${index.name}...")
        REDDatabase.rawDatabase.execSQL("DROP INDEX IF EXISTS ${index.name}")
      }
      stopwatch.split("drop-indices")

      if (cancellationRED()) {
        return ImportResult.Failure
      }

      Log.d(TAG, "[import] --- Dropping all triggers ---")
      val triggerMetadata = REDDatabase.rawDatabase.getAllTriggerDefinitions()
      for (trigger in triggerMetadata) {
        Log.d(TAG, "[import] Dropping trigger ${trigger.name}...")
        REDDatabase.rawDatabase.execSQL("DROP TRIGGER IF EXISTS ${trigger.name}")
      }
      stopwatch.split("drop-triggers")

      if (cancellationRED()) {
        return ImportResult.Failure
      }

      Log.d(TAG, "[import] --- Recreating all tables ---")
      val skipTables = buildSet {
        add(KyberPreKeyTable.TABLE_NAME)
        add(OneTimePreKeyTable.TABLE_NAME)
        add(SignedPreKeyTable.TABLE_NAME)

        // Preserve the session established with the primary during linking
        if (backupMode.isLinkAndSync) {
          add(SessionTable.TABLE_NAME)
        }
      }
      val tableMetadata = REDDatabase.rawDatabase.getAllTableDefinitions().filter { !it.name.startsWith(SearchTable.FTS_TABLE_NAME + "_") }
      for (table in tableMetadata) {
        if (skipTables.contains(table.name)) {
          Log.d(TAG, "[import] Skipping drop/create of table ${table.name}")
          continue
        }

        Log.d(TAG, "[import] Dropping table ${table.name}...")
        REDDatabase.rawDatabase.execSQL("DROP TABLE IF EXISTS ${table.name}")

        Log.d(TAG, "[import] Creating table ${table.name}...")
        REDDatabase.rawDatabase.execSQL(table.statement)
      }

      RecipientId.clearCache()
      REDDatabase.remappedRecords.clearCache()
      AppDependencies.recipientCache.clear()
      AppDependencies.recipientCache.clearSelf()
      REDDatabase.threads.clearCache()

      stopwatch.split("drop-data")

      if (cancellationRED()) {
        return ImportResult.Failure
      }

      val mediaRootBackupKey = MediaRootBackupKey(header.mediaRootBackupKey.toByteArray())
      REDStore.backup.mediaRootBackupKey = mediaRootBackupKey

      // Add back self after clearing data
      selfId = REDDatabase.recipients.getAndPossiblyMerge(selfData.aci, selfData.pni, selfData.e164, pniVerified = true, changeSelf = true)
      REDDatabase.recipients.setProfileKey(selfId, selfData.profileKey)
      REDDatabase.recipients.setProfileSharing(selfId, true)

      val importState = ImportState(mediaRootBackupKey, backupMode)
      val chatItemInserter: ChatItemArchiveImporter = ChatItemArchiveProcessor.beginImport(importState)

      Log.d(TAG, "[import] Beginning to read frames.")
      val totalLength = frameReader.getStreamLength()
      var frameCount = 0
      for (frame in frameReader) {
        val frameAccount = frame.account
        val frameRecipient = frame.recipient
        val frameChat = frame.chat
        val frameAdHocCall = frame.adHocCall
        val frameStickerPack = frame.stickerPack
        val frameNotificationProfile = frame.notificationProfile
        val frameChatFolder = frame.chatFolder
        val frameChatItem = frame.chatItem
        when {
          frameAccount != null -> {
            AccountDataArchiveProcessor.import(frameAccount, selfId, importState)
            eventTimer.emit("account")
            frameCount++
          }

          frameRecipient != null -> {
            RecipientArchiveProcessor.import(frameRecipient, importState)
            eventTimer.emit("recipient")
            frameCount++
          }

          frameChat != null -> {
            ChatArchiveProcessor.import(frameChat, importState)
            eventTimer.emit("chat")
            frameCount++
          }

          frameAdHocCall != null -> {
            AdHocCallArchiveProcessor.import(frameAdHocCall, importState)
            eventTimer.emit("call")
            frameCount++
          }

          frameStickerPack != null -> {
            StickerArchiveProcessor.import(frameStickerPack)
            eventTimer.emit("sticker-pack")
            frameCount++
          }

          frameNotificationProfile != null -> {
            NotificationProfileArchiveProcessor.import(frameNotificationProfile, importState)
            eventTimer.emit("notification-profile")
            frameCount++
          }

          frameChatFolder != null -> {
            ChatFolderArchiveProcessor.import(frameChatFolder, importState)
            eventTimer.emit("chat-folder")
            frameCount++
          }

          frameChatItem != null -> {
            chatItemInserter.import(frameChatItem)
            eventTimer.emit("chatItem")
            frameCount++

            if (frameCount % 1000 == 0) {
              if (cancellationRED()) {
                return ImportResult.Failure
              }
              Log.d(TAG, "Imported $frameCount frames so far.")
            }
            // SIGNAL_INHERITED: TODO if there's stuff in the stream after chatItems, we need to flush the inserter before going to the next phase
          }

          else -> Log.w(TAG, "Unrecognized frame")
        }
        EventBus.getDefault().post(RestoreV2Event(RestoreV2Event.Type.PROGRESS_RESTORE, frameReader.getBytesRead().bytes, totalLength.bytes))
      }

      if (chatItemInserter.flush()) {
        eventTimer.emit("chatItem")
      }

      EventBus.getDefault().post(RestoreV2Event(RestoreV2Event.Type.PROGRESS_FINALIZING, 0.bytes, 0.bytes))

      if (!importState.importedChatFolders) {
        // Add back default All Chats chat folder after clearing data if missing
        REDDatabase.chatFolders.insertAllChatFolder()
      }

      stopwatch.split("frames")

      Log.d(TAG, "[import] Remove duplicate messages...")
      REDDatabase.messages.removeDuplicatesPostBackupRestore()

      Log.d(TAG, "[import] Rebuilding FTS index...")
      REDDatabase.messageSearch.rebuildIndex()

      Log.d(TAG, "[import] --- Recreating indices ---")
      for (index in indexMetadata) {
        Log.d(TAG, "[import] Creating index ${index.name}...")
        REDDatabase.rawDatabase.execSQL(index.statement)
      }
      stopwatch.split("recreate-indices")

      Log.d(TAG, "[import] --- Recreating triggers ---")
      for (trigger in triggerMetadata) {
        Log.d(TAG, "[import] Creating trigger ${trigger.name}...")
        REDDatabase.rawDatabase.execSQL(trigger.statement)
      }
      stopwatch.split("recreate-triggers")

      Log.d(TAG, "[import] Updating threads...")
      importState.chatIdToLocalThreadId.values.forEach {
        REDDatabase.threads.update(it, unarchive = false, allowDeletion = false)
      }
      stopwatch.split("thread-updates")

      val foreignKeyViolations = REDDatabase.rawDatabase.getForeignKeyViolations()
      if (foreignKeyViolations.isNotEmpty()) {
        throw IllegalStateException("Foreign key check failed! Violations: $foreignKeyViolations")
      }
      stopwatch.split("fk-check")

      REDDatabase.rawDatabase.setTransactionSuccessful()
      transactionSuccessful = true
    } finally {
      if (REDDatabase.rawDatabase.inTransaction()) {
        REDDatabase.rawDatabase.endTransaction()
      }

      if (!transactionSuccessful) {
        Log.w(TAG, "[import] Transaction failed, clearing release channel recipient ID from key-value store.")
        REDStore.releaseChannel.clearReleaseChannelRecipientId()
      }

      Log.d(TAG, "[import] Re-enabling foreign keys...")
      REDDatabase.rawDatabase.forceForeignKeyConstraintsEnabled(true)
    }

    REDDatabase.remappedRecords.clearCache()
    REDDatabase.remappedRecords.trimStaleMappings()
    AppDependencies.recipientCache.clear()
    AppDependencies.recipientCache.warmUp()
    REDDatabase.threads.clearCache()

    if (REDStore.svr.pin?.isNotBlank() == true) {
      AppDependencies.jobManager.add(ResetSvrGuessCountJob())
    }

    val stickerJobs = REDDatabase.stickers.getAllStickerPacks().use { cursor ->
      val reader = StickerTables.StickerPackRecordReader(cursor)
      reader
        .filter { it.isInstalled }
        .map {
          StickerPackDownloadJob.forInstall(it.packId, it.packKey, false)
        }
    }
    AppDependencies.jobManager.addAll(stickerJobs)
    stopwatch.split("sticker-jobs")

    val recipientIds = REDDatabase.threads.getRecentConversationList(
      limit = RECENT_RECIPIENTS_MAX,
      includeInactiveGroups = false,
      individualsOnly = true,
      groupsOnly = false,
      hideV1Groups = true,
      hideSms = true,
      hideSelf = true
    ).use {
      val recipientSet = mutableSetOf<RecipientId>()
      while (it.moveToNext()) {
        recipientSet.add(RecipientId.from(CursorUtil.requireLong(it, ThreadTable.RECIPIENT_ID)))
      }
      recipientSet
    }

    RetrieveProfileJob.enqueue(recipientIds, skipDebounce = false)
    stopwatch.split("profile-jobs")

    AppDependencies.jobManager.add(CreateReleaseChannelJob.create())

    val groupJobs = REDDatabase.groups.getGroups().use { groups ->
      val jobs = mutableListOf<Job>()
      groups
        .asSequence()
        .filter { it.id.isV2 && it.hasV2GroupProperties }
        .forEach { group ->
          jobs.add(RequestGroupV2InfoJob(group.id as GroupId.V2))
          val avatarKey = group.requireV2GroupProperties().avatarKey
          if (avatarKey.isNotEmpty()) {
            jobs.add(AvatarGroupsV2DownloadJob(group.id.requireV2(), avatarKey))
          }
        }
      jobs
    }
    AppDependencies.jobManager.addAll(groupJobs)
    stopwatch.split("group-jobs")

    if (RemoteConfig.collapseEvents) {
      AppDependencies.jobManager.add(BackfillCollapsedMessageJob())
    }

    REDStore.backup.firstAppVersion = header.firstAppVersion
    REDStore.internal.importedBackupDebugInfo = header.debugInfo.let { BackupDebugInfo.ADAPTER.decodeOrNull(it.toByteArray()) }

    Log.d(TAG, "[import] Finished! ${eventTimer.stop().summary}")
    stopwatch.stop(TAG)

    return ImportResult.Success(backupTime = header.backupTimeMs, selfRecipientId = selfId)
  }

  fun listRemoteMediaObjects(limit: Int, cursor: String? = null): NetworkResult<ArchiveGetMediaItemsResponse> {
    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.getArchiveMediaItemsPage(REDStore.account.requireAci(), credential.mediaBackupAccess, limit, cursor)
      }.runOnStatusCodeError {
        REDStore.backup.mediaCredentials.clearAll()
      }
  }

  /**
   * Grabs the backup tier we think the user is on without performing any kind of authentication clearing
   * on a 403 error. Ensures we can check without rolling the user back during the BackupSubscriptionCheckJob.
   */
  fun getBackupTierWithoutDowngrade(): NetworkResult<MessageBackupTier> {
    return if (REDStore.backup.areBackupsEnabled) {
      getArchiveServiceAccessPair()
        .then { credential ->
          val zkCredential = REDNetwork.archive.getZkCredential(Recipient.self().requireAci(), credential.messageBackupAccess)
          val tier = if (zkCredential.backupLevel == BackupLevel.PAID) {
            MessageBackupTier.PAID
          } else {
            MessageBackupTier.FREE
          }

          NetworkResult.Success(tier)
        }
    } else {
      NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
    }
  }

  /**
   * If backups are enabled, sync with the network. Otherwise, return a 404.
   * Used in instrumentation tests.
   *
   * Note that this will set the user's backup tier to FREE if they are not on PAID, so avoid this method if you don't intend that to be the case.
   */
  fun getBackupTier(): NetworkResult<MessageBackupTier> {
    return if (REDStore.backup.areBackupsEnabled) {
      getBackupTier(Recipient.self().requireAci())
    } else {
      NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
    }
  }

  fun enablePaidBackupTier() {
    Log.i(TAG, "Setting backup tier to PAID", true)
    resetInitializedStateAndAuthCredentials()
    REDStore.backup.backupTier = MessageBackupTier.PAID
    REDStore.backup.lastCheckInMillis = System.currentTimeMillis()
    REDStore.backup.lastCheckInSnoozeMillis = 0
    REDStore.backup.clearDownloadNotifierState()
    scheduleSyncForAccountChange()
  }

  /**
   * Grabs the backup tier for the given ACI. Note that this will set the user's backup
   * tier to FREE if they are not on PAID, so avoid this method if you don't intend that
   * to be the case.
   */
  private fun getBackupTier(aci: ACI): NetworkResult<MessageBackupTier> {
    return initBackupAndFetchAuth()
      .map { credential ->
        val zkCredential = REDNetwork.archive.getZkCredential(aci, credential.messageBackupAccess)
        if (zkCredential.backupLevel == BackupLevel.PAID) {
          MessageBackupTier.PAID
        } else {
          MessageBackupTier.FREE
        }
      }
  }

  /**
   * Returns an object with details about the remote backup state.
   */
  fun debugGetRemoteBackupState(): NetworkResult<DebugBackupMetadata> {
    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.getBackupInfo(REDStore.account.requireAci(), credential.mediaBackupAccess)
          .map { it to credential }
      }
      .then { pair ->
        val (mediaBackupInfo, credential) = pair
        REDNetwork.archive.debugGetUploadedMediaItemMetadata(REDStore.account.requireAci(), credential.mediaBackupAccess)
          .map { mediaObjects ->
            DebugBackupMetadata(
              usedSpace = mediaBackupInfo.usedSpace ?: 0,
              mediaCount = mediaObjects.size.toLong(),
              mediaSize = mediaObjects.sumOf { it.objectLength }
            )
          }
      }
  }

  fun getMessageBackupUploadForm(backupFileSize: Long): NetworkResult<AttachmentUploadForm> {
    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.getMessageBackupUploadForm(REDStore.account.requireAci(), credential.messageBackupAccess, backupFileSize)
      }
  }

  fun downloadBackupFile(destination: File, listener: ProgressListener? = null): NetworkResult<Unit> {
    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.getBackupInfo(REDStore.account.requireAci(), credential.messageBackupAccess)
      }
      .then { info -> getCdnReadCredentials(CredentialType.MESSAGE, info.cdn ?: Cdn.CDN_3.cdnNumber).map { it.headers to info } }
      .map { pair ->
        val (cdnCredentials, info) = pair
        val messageReceiver = AppDependencies.signalServiceMessageReceiver
        messageReceiver.retrieveBackup(info.cdn!!, cdnCredentials, "backups/${info.backupDir}/${info.backupName}", destination, listener)
      }
  }

  fun getBackupFileLastModified(): NetworkResult<ZonedDateTime> {
    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.getBackupInfo(REDStore.account.requireAci(), credential.messageBackupAccess)
      }
      .then { info -> getCdnReadCredentials(CredentialType.MESSAGE, info.cdn ?: RemoteConfig.backupFallbackArchiveCdn).map { it.headers to info } }
      .then { pair ->
        val (cdnCredentials, info) = pair
        NetworkResult.fromFetch {
          AppDependencies.signalServiceMessageReceiver.getCdnLastModifiedTime(info.cdn!!, cdnCredentials, "backups/${info.backupDir}/${info.backupName}")
        }
      }
  }

  /**
   * Stores the remote backup's last-modified time in [BackupValues.lastBackupTime], (404/401 clear it to 0).
   */
  fun refreshBackupFileTimestamp(): NetworkResult<ZonedDateTime> {
    return getBackupFileLastModified().also { result ->
      when (result) {
        is NetworkResult.Success -> REDStore.backup.lastBackupTime = result.result.toMillis()
        is NetworkResult.StatusCodeError if (result.code == 404 || result.code == 401) -> REDStore.backup.lastBackupTime = 0L
        else -> Log.w(TAG, "Failed to refresh last backup time from remote: ${result::class.simpleName}")
      }
    }
  }

  /**
   * Returns an object with details about the remote backup state.
   */
  fun debugGetArchivedMediaState(): NetworkResult<List<ArchiveGetMediaItemsResponse.StoredMediaObject>> {
    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.debugGetUploadedMediaItemMetadata(REDStore.account.requireAci(), credential.mediaBackupAccess)
      }
  }

  /**
   * Retrieves an [AttachmentUploadForm] that can be used to upload an attachment to the transit cdn.
   *
   * It's important to note that in order to get this to the archive cdn, you still need to use [copyAttachmentToArchive].
   */
  fun getAttachmentUploadForm(uploadLength: Long): NetworkResult<AttachmentUploadForm> {
    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.getMediaUploadForm(REDStore.account.requireAci(), credential.mediaBackupAccess, uploadLength)
      }
  }

  /**
   * Returns if an attachment should be copied to the archive if it meets certain requirements eg
   * not a story, not already uploaded to the archive cdn, not a preuploaded attachment, etc.
   */
  @JvmStatic
  fun shouldCopyAttachmentToArchive(attachmentId: AttachmentId, messageId: Long): Boolean {
    if (!REDStore.backup.backsUpMedia) {
      return false
    }

    val attachment = REDDatabase.attachments.getAttachment(attachmentId)

    return when {
      attachment == null -> false
      attachment.archiveTransferState == AttachmentTable.ArchiveTransferState.FINISHED -> false
      !DatabaseAttachmentArchiveUtil.hadIntegrityCheckPerformed(attachment) -> false
      messageId == AttachmentTable.PREUPLOAD_MESSAGE_ID -> false
      REDDatabase.messages.isStory(messageId) -> false
      REDDatabase.messages.isViewOnce(messageId) -> false
      REDDatabase.messages.willMessageExpireBeforeCutoff(messageId) -> false
      else -> true
    }
  }

  /**
   * Copies a thumbnail that has been uploaded to the transit cdn to the archive cdn.
   */
  fun copyThumbnailToArchive(thumbnail: UploadedThumbnailInfo, parentAttachment: DatabaseAttachment): NetworkResult<ArchiveMediaResponse> {
    return initBackupAndFetchAuth()
      .then { credential ->
        val request = buildArchiveMediaRequest(thumbnail.cdnNumber, thumbnail.remoteLocation, thumbnail.size, parentAttachment.requireThumbnailMediaName(), credential.mediaBackupAccess.backupKey)

        REDNetwork.archive.copyAttachmentToArchive(
          aci = REDStore.account.requireAci(),
          archiveServiceAccess = credential.mediaBackupAccess,
          item = request
        )
      }
  }

  /**
   * Copies an attachment that has been uploaded to the transit cdn to the archive cdn.
   */
  fun copyAttachmentToArchive(attachment: DatabaseAttachment): NetworkResult<Unit> {
    return initBackupAndFetchAuth()
      .then { credential ->
        val mediaName = attachment.requireMediaName()
        val request = buildArchiveMediaRequest(attachment.cdn.cdnNumber, attachment.remoteLocation!!, attachment.size, mediaName, credential.mediaBackupAccess.backupKey)
        REDNetwork.archive
          .copyAttachmentToArchive(
            aci = REDStore.account.requireAci(),
            archiveServiceAccess = credential.mediaBackupAccess,
            item = request
          )
      }
      .map { response ->
        REDDatabase.attachments.setArchiveCdn(attachmentId = attachment.attachmentId, archiveCdn = response.cdn)
      }
      .also { Log.i(TAG, "archiveMediaResult: ${it::class.simpleName}") }
  }

  fun deleteAbandonedMediaObjects(mediaObjects: Collection<ArchivedMediaObject>): NetworkResult<Unit> {
    val mediaToDelete = mediaObjects
      .map {
        DeleteArchivedMediaRequest.ArchivedMediaObject(
          cdn = it.cdn,
          mediaId = it.mediaId
        )
      }
      .filter { it.cdn == Cdn.CDN_3.cdnNumber }

    if (mediaToDelete.isEmpty()) {
      Log.i(TAG, "No media to delete, quick success")
      return NetworkResult.Success(Unit)
    }

    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.deleteArchivedMedia(
          aci = REDStore.account.requireAci(),
          archiveServiceAccess = credential.mediaBackupAccess,
          mediaToDelete = mediaToDelete
        )
      }
      .also { Log.i(TAG, "deleteAbandonedMediaObjectsResult: ${it::class.simpleName}") }
  }

  fun deleteBackup(): NetworkResult<Unit> {
    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.deleteBackup(REDStore.account.requireAci(), credential.messageBackupAccess)
      }
  }

  fun deleteMediaBackup(): NetworkResult<Unit> {
    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.deleteBackup(REDStore.account.requireAci(), credential.mediaBackupAccess)
      }
  }

  fun debugDeleteAllArchivedMedia(): NetworkResult<Unit> {
    val itemLimit = 1000
    return debugGetArchivedMediaState()
      .then { archivedMedia ->
        val mediaChunksToDelete = archivedMedia
          .map {
            DeleteArchivedMediaRequest.ArchivedMediaObject(
              cdn = it.cdn,
              mediaId = it.mediaId
            )
          }
          .filter { it.cdn == Cdn.CDN_3.cdnNumber }
          .chunked(itemLimit)

        if (mediaChunksToDelete.isEmpty()) {
          Log.i(TAG, "No media to delete, quick success")
          return@then NetworkResult.Success(Unit)
        }

        getArchiveServiceAccessPair()
          .then processChunks@{ credential ->
            mediaChunksToDelete.forEachIndexed { index, chunk ->
              val result = REDNetwork.archive.deleteArchivedMedia(
                aci = REDStore.account.requireAci(),
                archiveServiceAccess = credential.mediaBackupAccess,
                mediaToDelete = chunk
              )

              if (result !is NetworkResult.Success) {
                Log.w(TAG, "Error occurred while deleting archived media chunk #$index: $result")
                return@processChunks result
              }
            }
            NetworkResult.Success(Unit)
          }
      }
      .map {
        REDDatabase.attachments.clearAllArchiveData()
      }
      .also { Log.i(TAG, "debugDeleteAllArchivedMediaResult: ${it::class.simpleName}") }
  }

  /**
   * Retrieve credentials for reading from the backup cdn.
   */
  fun getCdnReadCredentials(credentialType: CredentialType, cdnNumber: Int): NetworkResult<GetArchiveCdnCredentialsResponse> {
    val credentialStore = when (credentialType) {
      CredentialType.MESSAGE -> REDStore.backup.messageCredentials
      CredentialType.MEDIA -> REDStore.backup.mediaCredentials
    }

    val cached = credentialStore.cdnReadCredentials
    if (cached != null) {
      return NetworkResult.Success(cached)
    }

    return initBackupAndFetchAuth()
      .then { credential ->
        val archiveServiceAccess = when (credentialType) {
          CredentialType.MESSAGE -> credential.messageBackupAccess
          CredentialType.MEDIA -> credential.mediaBackupAccess
        }

        REDNetwork.archive.getCdnReadCredentials(
          cdnNumber = cdnNumber,
          aci = REDStore.account.requireAci(),
          archiveServiceAccess = archiveServiceAccess
        )
      }
      .also {
        if (it is NetworkResult.Success) {
          credentialStore.cdnReadCredentials = it.result
        }
      }
      .also { Log.i(TAG, "getCdnReadCredentialsResult: ${it::class.simpleName}") }
  }

  fun restoreBackupFileTimestamp(): RestoreTimestampResult {
    val timestampResult: NetworkResult<ZonedDateTime> = getBackupFileLastModified()

    when {
      timestampResult is NetworkResult.Success -> {
        REDStore.backup.lastBackupTime = timestampResult.result.toMillis()
        REDStore.backup.isBackupTimestampRestored = true
        REDStore.uiHints.markHasEverEnabledRemoteBackups()
        return RestoreTimestampResult.Success(REDStore.backup.lastBackupTime)
      }

      timestampResult is NetworkResult.StatusCodeError && timestampResult.code == 404 -> {
        Log.i(TAG, "No backup file exists")
        REDStore.backup.lastBackupTime = 0L
        REDStore.backup.isBackupTimestampRestored = true
        return RestoreTimestampResult.NotFound
      }

      timestampResult is NetworkResult.StatusCodeError && timestampResult.code == 401 -> {
        Log.i(TAG, "Backups not enabled")
        REDStore.backup.lastBackupTime = 0L
        REDStore.backup.isBackupTimestampRestored = true
        return RestoreTimestampResult.BackupsNotEnabled
      }

      timestampResult is NetworkResult.ApplicationError && timestampResult.getCause() is VerificationFailedException -> {
        Log.w(TAG, "Entered AEP fails zk verification", timestampResult.getCause())
        return RestoreTimestampResult.VerificationFailure
      }

      else -> {
        Log.w(TAG, "Could not check for backup file.", timestampResult.getCause())
        return RestoreTimestampResult.Failure
      }
    }
  }

  fun verifyBackupKeyAssociatedWithAccount(aci: ACI, aep: AccountEntropyPool): RestoreTimestampResult {
    Log.i(TAG, "Verifying enter aep is associated with account")
    var result: RestoreTimestampResult = getBackupTimestampToVerifyAepAssociatedWithAccountAndHasBackup(aci, aep)

    if (result is RestoreTimestampResult.VerificationFailure) {
      Log.w(TAG, "Resetting backup id reservation due to zk verification failure")
      val triggerResult = REDNetwork.archive.triggerBackupIdReservation(aep.deriveMessageBackupKey(), null, aci)
      result = when {
        triggerResult is NetworkResult.Success -> {
          Log.i(TAG, "Reset successful, retrying aep verification")
          REDStore.backup.messageCredentials.clearAll()
          getBackupTimestampToVerifyAepAssociatedWithAccountAndHasBackup(aci, aep)
        }

        triggerResult is NetworkResult.StatusCodeError && triggerResult.code == 429 -> {
          Log.w(TAG, "Rate limited when resetting backup id, failing operation $triggerResult")
          RestoreTimestampResult.RateLimited(triggerResult.retryAfter())
        }

        else -> {
          Log.w(TAG, "Reset backup id failed, failing operation", triggerResult.getCause())
          result
        }
      }
    }

    return result
  }

  private fun getBackupTimestampToVerifyAepAssociatedWithAccountAndHasBackup(aci: ACI, aep: AccountEntropyPool): RestoreTimestampResult {
    val currentTime = System.currentTimeMillis()
    val messageBackupKey = aep.deriveMessageBackupKey()

    val result: NetworkResult<ZonedDateTime> = REDNetwork.archive.getServiceCredentials(currentTime)
      .then { result ->
        val credential: ArchiveServiceCredential? = ArchiveServiceCredentials(result.messageCredentials.associateBy { it.redemptionTime }).getForCurrentTime(currentTime.milliseconds)

        if (credential == null) {
          NetworkResult.ApplicationError(NullPointerException("No credential available for current time."))
        } else {
          NetworkResult.Success(
            ArchiveServiceAccess(
              credential = credential,
              backupKey = messageBackupKey
            )
          )
        }
      }
      .then { messageAccess ->
        REDNetwork.archive.getBackupInfo(REDStore.account.requireAci(), messageAccess)
          .then { info -> REDNetwork.archive.getCdnReadCredentials(info.cdn ?: RemoteConfig.backupFallbackArchiveCdn, aci, messageAccess).map { it.headers to info } }
          .then { pair ->
            val (cdnCredentials, info) = pair
            NetworkResult.fromFetch {
              AppDependencies.signalServiceMessageReceiver.getCdnLastModifiedTime(info.cdn!!, cdnCredentials, "backups/${info.backupDir}/${info.backupName}")
            }
          }
      }

    return when {
      result is NetworkResult.Success -> {
        RestoreTimestampResult.Success(result.result.toMillis())
      }

      result is NetworkResult.StatusCodeError && result.code == 404 -> {
        Log.i(TAG, "No backup file exists")
        RestoreTimestampResult.NotFound
      }

      result is NetworkResult.StatusCodeError && result.code == 401 -> {
        Log.i(TAG, "Backups not enabled")
        RestoreTimestampResult.BackupsNotEnabled
      }

      result is NetworkResult.ApplicationError && result.getCause() is VerificationFailedException -> {
        Log.w(TAG, "Entered AEP fails zk verification", result.getCause())
        RestoreTimestampResult.VerificationFailure
      }

      else -> {
        Log.w(TAG, "Could not check for backup file.", result.getCause())
        RestoreTimestampResult.Failure
      }
    }
  }

  /**
   * Retrieves media-specific cdn path, preferring cached value if available.
   *
   * This will change if the backup expires, a new backup-id is set, or the delete all endpoint is called.
   */
  fun getArchivedMediaCdnPath(): NetworkResult<String> {
    val cachedMediaPath = REDStore.backup.cachedMediaCdnPath

    if (cachedMediaPath != null) {
      return NetworkResult.Success(cachedMediaPath)
    }

    return initBackupAndFetchAuth()
      .then { credential ->
        REDNetwork.archive.getBackupInfo(REDStore.account.requireAci(), credential.mediaBackupAccess).map {
          "${it.backupDir!!.urlEncode()}/${it.mediaDir!!.urlEncode()}"
        }
      }
      .also {
        if (it is NetworkResult.Success) {
          REDStore.backup.cachedMediaCdnPath = it.result
        }
      }
  }

  suspend fun getBackupTypes(availableBackupTiers: List<MessageBackupTier>): List<MessageBackupsType> {
    return availableBackupTiers.mapNotNull {
      val type = getBackupsType(it)

      if (type is NetworkResult.Success) type.result else null
    }
  }

  private suspend fun getBackupsType(tier: MessageBackupTier): NetworkResult<out MessageBackupsType> {
    return when (tier) {
      MessageBackupTier.FREE -> getFreeType()
      MessageBackupTier.PAID -> getPaidType()
    }
  }

  @WorkerThread
  fun getBackupLevelConfiguration(): NetworkResult<SubscriptionsConfiguration.BackupLevelConfiguration> {
    return AppDependencies.donationsService
      .getDonationsConfiguration(Locale.getDefault())
      .toNetworkResult()
      .then {
        val config = it.backupConfiguration.backupLevelConfigurationMap[SubscriptionsConfiguration.BACKUPS_LEVEL]
        if (config != null) {
          NetworkResult.Success(config)
        } else {
          NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
        }
      }
  }

  @WorkerThread
  fun getFreeType(): NetworkResult<MessageBackupsType.Free> {
    return AppDependencies.donationsService
      .getDonationsConfiguration(Locale.getDefault())
      .toNetworkResult()
      .map {
        MessageBackupsType.Free(
          mediaRetentionDays = it.backupConfiguration.freeTierMediaDays
        )
      }
  }

  suspend fun getPaidType(): NetworkResult<MessageBackupsType.Paid> {
    val productPrice: FiatMoney? = if (REDStore.backup.backupTierInternalOverride == MessageBackupTier.PAID) {
      Log.d(TAG, "Accessing price via mock subscription.")
      RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP).successOrNull()?.activeSubscription?.let {
        FiatMoney.fromREDNetworkAmount(it.amount, Currency.getInstance(it.currency))
      }
    } else if (AppDependencies.billingApi.getApiAvailability().isSuccess) {
      Log.d(TAG, "Accessing price via billing api.")
      AppDependencies.billingApi.queryProduct()?.price
    } else {
      FiatMoney(BigDecimal.ZERO, REDStore.inAppPayments.getRecurringDonationCurrency())
    }

    if (productPrice == null) {
      Log.w(TAG, "No pricing available. Exiting.")
      return NetworkResult.StatusCodeError(NonSuccessfulResponseCodeException(404))
    }

    return getBackupLevelConfiguration()
      .map {
        MessageBackupsType.Paid(
          pricePerMonth = productPrice,
          storageAllowanceBytes = it.storageAllowanceBytes,
          mediaTtl = it.mediaTtlDays.days
        )
      }
  }

  /**
   * See [org.signal.network.api.ArchiveApi.getSvrBAuthorization].
   */
  fun getSvrBAuth(): NetworkResult<AuthCredentials> {
    return initBackupAndFetchAuth()
      .then { REDNetwork.archive.getSvrBAuthorization(REDStore.account.requireAci(), it.messageBackupAccess) }
  }

  fun getKeyRotationLimit(): NetworkResult<ArchiveKeyRotationLimitResponse> {
    return REDNetwork.archive.getKeyRotationLimit()
  }

  /**
   * During normal operation, ensures that the backupId has been reserved and that your public key has been set,
   * while also returning an archive access data. Should be the basis of all backup operations.
   *
   * When called during registration before backups are initialized, will only fetch access data and not initialize backups. This
   * prevents early initialization with incorrect keys before we have restored them.
   */
  private fun initBackupAndFetchAuth(): NetworkResult<ArchiveServiceAccessPair> {
    return if (REDStore.backup.backupsInitialized || REDStore.account.isLinkedDevice) {
      getArchiveServiceAccessPair()
        .runOnStatusCodeError(resetInitializedStateErrorAction)
        .runOnApplicationError(clearAuthCredentials)
    } else if (isPreRestoreDuringRegistration()) {
      Log.w(TAG, "Requesting/using auth credentials in pre-restore state", Throwable())
      getArchiveServiceAccessPair()
        .runOnApplicationError(clearAuthCredentials)
    } else {
      val messageBackupKey = REDStore.backup.messageBackupKey
      val mediaRootBackupKey = REDStore.backup.mediaRootBackupKey

      return REDNetwork.archive
        .triggerBackupIdReservation(messageBackupKey, mediaRootBackupKey, REDStore.account.requireAci())
        .then {
          REDStore.backup.messageCredentials.clearAll()
          REDStore.backup.mediaCredentials.clearAll()
          getArchiveServiceAccessPair()
        }
        .then { credential -> REDNetwork.archive.setPublicKey(REDStore.account.requireAci(), credential.messageBackupAccess).map { credential } }
        .then { credential -> REDNetwork.archive.setPublicKey(REDStore.account.requireAci(), credential.mediaBackupAccess).map { credential } }
        .runIfSuccessful { REDStore.backup.backupsInitialized = true }
        .runOnStatusCodeError(resetInitializedStateErrorAction)
        .runOnApplicationError(clearAuthCredentials)
    }
  }

  /**
   * Retrieves an auth credential, preferring a cached value if available.
   */
  private fun getArchiveServiceAccessPair(): NetworkResult<ArchiveServiceAccessPair> {
    val currentTime = System.currentTimeMillis()

    val messageCredential = REDStore.backup.messageCredentials.byDay.getForCurrentTime(currentTime.milliseconds)
    val mediaCredential = REDStore.backup.mediaCredentials.byDay.getForCurrentTime(currentTime.milliseconds)

    if (messageCredential != null && mediaCredential != null) {
      return NetworkResult.Success(
        ArchiveServiceAccessPair(
          messageBackupAccess = ArchiveServiceAccess(messageCredential, REDStore.backup.messageBackupKey),
          mediaBackupAccess = ArchiveServiceAccess(mediaCredential, REDStore.backup.mediaRootBackupKey)
        )
      )
    }

    Log.w(TAG, "No credentials found for today, need to fetch new ones! This shouldn't happen under normal circumstances. We should ensure the routine fetch is running properly.")

    return REDNetwork.archive.getServiceCredentials(currentTime).map { result ->
      REDStore.backup.messageCredentials.add(result.messageCredentials)
      REDStore.backup.messageCredentials.clearOlderThan(currentTime)

      REDStore.backup.mediaCredentials.add(result.mediaCredentials)
      REDStore.backup.mediaCredentials.clearOlderThan(currentTime)

      ArchiveServiceAccessPair(
        messageBackupAccess = ArchiveServiceAccess(REDStore.backup.messageCredentials.byDay.getForCurrentTime(currentTime.milliseconds)!!, REDStore.backup.messageBackupKey),
        mediaBackupAccess = ArchiveServiceAccess(REDStore.backup.mediaCredentials.byDay.getForCurrentTime(currentTime.milliseconds)!!, REDStore.backup.mediaRootBackupKey)
      )
    }
  }

  private fun isPreRestoreDuringRegistration(): Boolean {
    return !REDStore.registration.isRegistrationComplete &&
      REDStore.registration.restoreDecisionState.isDecisionPending
  }

  private fun scheduleSyncForAccountChange() {
    REDDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  private fun File.deleteAllFilesWithPrefix(prefix: String) {
    this.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
  }

  data class SelfData(
    val aci: ACI,
    val pni: PNI,
    val e164: String,
    val profileKey: ProfileKey
  )

  private fun buildArchiveMediaRequest(cdnNumber: Int, remoteLocation: String, plaintextSize: Long, mediaName: MediaName, mediaRootBackupKey: MediaRootBackupKey): ArchiveMediaRequest {
    val mediaSecrets = mediaRootBackupKey.deriveMediaSecrets(mediaName)

    return ArchiveMediaRequest(
      sourceAttachment = ArchiveMediaRequest.SourceAttachment(
        cdn = cdnNumber,
        key = remoteLocation
      ),
      objectLength = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(plaintextSize)).toInt(),
      mediaId = mediaSecrets.id.encode(),
      hmacKey = Base64.encodeWithPadding(mediaSecrets.macKey),
      encryptionKey = Base64.encodeWithPadding(mediaSecrets.aesKey)
    )
  }

  suspend fun restoreRemoteBackup(): RemoteRestoreResult {
    val context = AppDependencies.application
    ArchiveRestoreProgress.onRestorePending()

    try {
      DataRestoreConstraint.isRestoringData = true
      return withContext(Dispatchers.IO) {
        val result = BackupProgressService.start(context, context.getString(R.string.BackupProgressService_title)).use {
          restoreRemoteBackup(controller = it, cancellationRED = { !isActive })
        }
        if (result !is RemoteRestoreResult.Success) {
          ArchiveRestoreProgress.onRestoreFailed()
        }
        return@withContext result
      }
    } finally {
      DataRestoreConstraint.isRestoringData = false
    }
  }

  private fun restoreRemoteBackup(controller: BackupProgressService.Controller, cancellationRED: () -> Boolean): RemoteRestoreResult {
    ArchiveRestoreProgress.onRestoringDb()

    val progressListener = object : ProgressListener {
      override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
        controller.update(
          title = AppDependencies.application.getString(R.string.BackupProgressService_title_downloading),
          progress = progress.value,
          indeterminate = false
        )
        EventBus.getDefault().post(RestoreV2Event(RestoreV2Event.Type.PROGRESS_DOWNLOAD, progress.transmitted, progress.total))
      }

      override fun shouldCancel() = cancellationRED()
    }

    Log.i(TAG, "[remoteRestore] Downloading backup")
    val tempBackupFile = AppDependencies.blobs.forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)
    when (val result = downloadBackupFile(tempBackupFile, progressListener)) {
      is NetworkResult.Success -> Log.i(TAG, "[remoteRestore] Download successful")
      else -> {
        Log.w(TAG, "[remoteRestore] Failed to download backup file", result.getCause())
        return RemoteRestoreResult.NetworkError
      }
    }

    if (cancellationRED()) {
      return RemoteRestoreResult.Canceled
    }

    controller.update(
      title = AppDependencies.application.getString(R.string.BackupProgressService_title),
      progress = 0f,
      indeterminate = true
    )

    val forwardSecrecyMetadata = EncryptedBackupReader.readForwardSecrecyMetadata(tempBackupFile.inputStream())
    if (forwardSecrecyMetadata == null) {
      Log.w(TAG, "Failed to read forward secrecy metadata!")
      return RemoteRestoreResult.Failure
    }

    val messageBackupKey = REDStore.backup.messageBackupKey

    Log.i(TAG, "[remoteRestore] Fetching SVRB data")
    val svrBAuth = when (val result = getSvrBAuth()) {
      is NetworkResult.Success -> result.result
      is NetworkResult.NetworkError -> return RemoteRestoreResult.NetworkError.logW(TAG, "[remoteRestore] Network error when getting SVRB auth.", result.getCause())
      is NetworkResult.StatusCodeError -> return RemoteRestoreResult.NetworkError.logW(TAG, "[remoteRestore] Status code error when getting SVRB auth.", result.getCause())
      is NetworkResult.ApplicationError -> throw result.throwable
    }

    val forwardSecrecyToken = when (val result = REDNetwork.svrB.restore(svrBAuth, messageBackupKey, forwardSecrecyMetadata)) {
      is SvrBApi.RestoreResult.Success -> {
        REDStore.backup.nextBackupSecretData = result.data.nextBackupSecretData
        result.data.forwardSecrecyToken
      }

      is SvrBApi.RestoreResult.NetworkError -> {
        Log.w(TAG, "[remoteRestore] Network error during SVRB.", result.exception)
        return RemoteRestoreResult.NetworkError
      }

      is SvrBApi.RestoreResult.RestoreFailedError,
      SvrBApi.RestoreResult.InvalidDataError -> {
        Log.w(TAG, "[remoteRestore] Permanent SVRB error! $result")
        return RemoteRestoreResult.PermanentSvrBFailure
      }

      SvrBApi.RestoreResult.DataMissingError,
      is SvrBApi.RestoreResult.SvrError -> {
        Log.w(TAG, "[remoteRestore] Failed to fetch SVRB data: $result")
        return RemoteRestoreResult.Failure
      }

      is SvrBApi.RestoreResult.UnknownError -> {
        Log.e(TAG, "[remoteRestore] Unknown SVRB result! Crashing.", result.throwable)
        throw result.throwable
      }
    }

    val self = Recipient.self()
    val selfData = SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))
    Log.i(TAG, "[remoteRestore] Importing backup")
    val result = importREDBackup(
      length = tempBackupFile.length(),
      inputStreamFactory = tempBackupFile::inputStream,
      selfData = selfData,
      backupKey = REDStore.backup.messageBackupKey,
      forwardSecrecyToken = forwardSecrecyToken,
      cancellationRED = cancellationRED
    )

    return when (result) {
      is ImportResult.Failure -> {
        Log.w(TAG, "[remoteRestore] Failed to import backup")
        RemoteRestoreResult.Failure
      }

      is ImportResult.Success -> {
        Log.i(TAG, "[remoteRestore] Restore successful")
        BackupMediaRestoreService.resetTimeout()
        AppDependencies.jobManager.add(BackupRestoreMediaJob())
        RemoteRestoreResult.Success(result.selfRecipientId)
      }
    }
  }

  suspend fun restoreLinkAndSyncBackup(response: TransferArchiveResponse, ephemeralBackupKey: MessageBackupKey): RemoteRestoreResult {
    val context = AppDependencies.application
    ArchiveRestoreProgress.onRestorePending()

    try {
      DataRestoreConstraint.isRestoringData = true
      return withContext(Dispatchers.IO) {
        return@withContext BackupProgressService.start(context, context.getString(RegistrationR.string.MessageSyncScreen__syncing_messages)).use {
          restoreLinkAndSyncBackup(response, ephemeralBackupKey, controller = it, cancellationRED = { !isActive })
        }
      }
    } finally {
      DataRestoreConstraint.isRestoringData = false
    }
  }

  private fun restoreLinkAndSyncBackup(response: TransferArchiveResponse, ephemeralBackupKey: MessageBackupKey, controller: BackupProgressService.Controller, cancellationRED: () -> Boolean): RemoteRestoreResult {
    ArchiveRestoreProgress.onRestoringDb()

    val progressListener = object : ProgressListener {
      override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
        controller.update(
          title = AppDependencies.application.getString(RegistrationR.string.MessageSyncScreen__syncing_messages),
          progress = progress.value,
          indeterminate = false
        )
        EventBus.getDefault().post(RestoreV2Event(RestoreV2Event.Type.PROGRESS_DOWNLOAD, progress.transmitted, progress.total))
      }

      override fun shouldCancel() = cancellationRED()
    }

    val cdn = response.cdn
    val key = response.key
    if (cdn == null || key == null) {
      Log.w(TAG, "[restoreLinkAndSyncBackup] Response has no archive location (error=${response.error}); nothing to download.")
      return RemoteRestoreResult.Failure
    }

    Log.i(TAG, "[restoreLinkAndSyncBackup] Downloading backup")
    val tempBackupFile = AppDependencies.blobs.forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)
    when (val result = AppDependencies.signalServiceMessageReceiver.retrieveLinkAndSyncBackup(cdn, key, tempBackupFile, progressListener)) {
      is NetworkResult.Success -> Log.i(TAG, "[restoreLinkAndSyncBackup] Download successful")
      else -> {
        Log.w(TAG, "[restoreLinkAndSyncBackup] Failed to download backup file", result.getCause())
        return RemoteRestoreResult.NetworkError
      }
    }

    if (cancellationRED()) {
      return RemoteRestoreResult.Canceled
    }

    controller.update(
      title = AppDependencies.application.getString(RegistrationR.string.MessageSyncScreen__syncing_messages),
      progress = 0f,
      indeterminate = true
    )

    val self = Recipient.self()
    val selfData = SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))
    Log.i(TAG, "[restoreLinkAndSyncBackup] Importing backup")
    val result = importLinkAndSyncREDBackup(
      length = tempBackupFile.length(),
      inputStreamFactory = tempBackupFile::inputStream,
      selfData = selfData,
      backupKey = ephemeralBackupKey,
      cancellationRED = cancellationRED
    )

    return when (result) {
      is ImportResult.Failure -> {
        Log.w(TAG, "[restoreLinkAndSyncBackup] Failed to import backup")
        RemoteRestoreResult.Failure
      }

      is ImportResult.Success -> {
        Log.i(TAG, "[restoreLinkAndSyncBackup] Restore successful")
        BackupMediaRestoreService.resetTimeout()
        AppDependencies.jobManager.add(BackupRestoreMediaJob())
        RemoteRestoreResult.Success(result.selfRecipientId)
      }
    }
  }

  private fun buildDebugInfo(): ByteString {
    if (!RemoteConfig.internalUser) {
      return ByteString.EMPTY
    }

    var debuglogUrl: String? = null

    if (REDStore.internal.includeDebuglogInBackup) {
      Log.i(TAG, "User has debuglog inclusion enabled. Generating a debuglog.")
      val latch = CountDownLatch(1)
      SubmitDebugLogRepository().buildAndSubmitLog { url ->
        debuglogUrl = url.getOrNull()
        latch.countDown()
      }

      try {
        val success = latch.await(10, TimeUnit.SECONDS)
        if (!success) {
          Log.w(TAG, "Timed out waiting for debuglog!")
        }
      } catch (e: Exception) {
        Log.w(TAG, "Hit an error while generating the debuglog!")
      }
    }

    return BackupDebugInfo(
      debuglogUrl = debuglogUrl ?: "",
      attachmentDetails = REDDatabase.attachments.debugAttachmentStatsForBackupProto(),
      usingPaidTier = REDStore.backup.backupTier == MessageBackupTier.PAID
    ).encodeByteString()
  }

  fun getRemoteBackupForwardSecrecyMetadata(): NetworkResult<ByteArray?> {
    return initBackupAndFetchAuth()
      .then { credential -> REDNetwork.archive.getBackupInfo(REDStore.account.requireAci(), credential.messageBackupAccess) }
      .then { info -> getCdnReadCredentials(CredentialType.MESSAGE, info.cdn ?: Cdn.CDN_3.cdnNumber).map { it.headers to info } }
      .then { pair ->
        val (cdnCredentials, info) = pair
        val headers = cdnCredentials.toMutableMap().apply {
          this["range"] = "bytes=0-${EncryptedBackupReader.BACKUP_SECRET_METADATA_UPPERBOUND - 1}"
        }

        AppDependencies.signalServiceMessageReceiver.retrieveBackupForwardSecretMetadataBytes(
          info.cdn!!,
          headers,
          "backups/${info.backupDir}/${info.backupName}",
          EncryptedBackupReader.BACKUP_SECRET_METADATA_UPPERBOUND
        )
      }
      .map { bytes -> EncryptedBackupReader.readForwardSecrecyMetadata(ByteArrayInputStream(bytes)) }
  }

  interface ExportProgressListener {
    fun onAccount()
    fun onRecipient()
    fun onThread()
    fun onCall()
    fun onSticker()
    fun onNotificationProfile()
    fun onChatFolder()
    fun onMessage(currentProgress: Long, approximateCount: Long)
    fun onAttachment(currentProgress: Long, totalCount: Long)
  }

  enum class CredentialType {
    MESSAGE, MEDIA
  }
}

data class ResumableMessagesBackupUploadSpec(
  val attachmentUploadForm: AttachmentUploadForm,
  val resumableUri: String
)

data class ArchivedMediaObject(val mediaId: String, val cdn: Int)

class ExportState(
  val backupTime: Long,
  val backupMode: BackupMode,
  val selfRecipientId: RecipientId
) {
  val recipientIds: MutableSet<Long> = hashSetOf()
  val threadIds: MutableSet<Long> = hashSetOf()
  val contactRecipientIds: MutableSet<Long> = hashSetOf()
  val groupRecipientIds: MutableSet<Long> = hashSetOf()
  val threadIdToRecipientId: MutableMap<Long, Long> = hashMapOf()
  val recipientIdToAci: MutableMap<Long, ByteString> = hashMapOf()
  val aciToRecipientId: MutableMap<String, Long> = hashMapOf()
  val recipientIdToE164: MutableMap<Long, Long> = hashMapOf()
  val customChatColorIds: MutableSet<Long> = hashSetOf()
  var releaseNoteRecipientId: Long? = null
}

class ImportState(val mediaRootBackupKey: MediaRootBackupKey, val backupMode: BackupMode) {
  val remoteToLocalRecipientId: MutableMap<Long, RecipientId> = hashMapOf()
  val chatIdToLocalThreadId: MutableMap<Long, Long> = hashMapOf()
  val chatIdToLocalRecipientId: MutableMap<Long, RecipientId> = hashMapOf()
  val chatIdToBackupRecipientId: MutableMap<Long, Long> = hashMapOf()
  val remoteToLocalColorId: MutableMap<Long, Long> = hashMapOf()
  val recipientIdToLocalThreadId: MutableMap<RecipientId, Long> = hashMapOf()
  val recipientIdToIsGroup: MutableMap<RecipientId, Boolean> = hashMapOf()

  private var chatFolderPosition: Int = 0
  val importedChatFolders: Boolean
    get() = chatFolderPosition > 0

  fun requireLocalRecipientId(remoteId: Long): RecipientId {
    return remoteToLocalRecipientId[remoteId] ?: throw IllegalArgumentException("There is no local recipientId for remote recipientId $remoteId!")
  }

  fun getNextChatFolderPosition(): Int {
    return chatFolderPosition++
  }
}

class DebugBackupMetadata(
  val usedSpace: Long,
  val mediaCount: Long,
  val mediaSize: Long
)

data class StagedBackupKeyRotations(
  val aep: AccountEntropyPool,
  val mediaRootBackupKey: MediaRootBackupKey
)

sealed class ImportResult {
  data class Success(val backupTime: Long, val selfRecipientId: RecipientId) : ImportResult()
  data object Failure : ImportResult()
}

sealed interface RemoteRestoreResult {
  data class Success(val selfRecipientId: RecipientId) : RemoteRestoreResult
  data object NetworkError : RemoteRestoreResult
  data object Canceled : RemoteRestoreResult
  data object Failure : RemoteRestoreResult

  /** SVRB has failed in such a way that recovering a backup is impossible. */
  data object PermanentSvrBFailure : RemoteRestoreResult
}

sealed interface RestoreTimestampResult {
  data class Success(val timestamp: Long) : RestoreTimestampResult
  data object NotFound : RestoreTimestampResult
  data object BackupsNotEnabled : RestoreTimestampResult
  data object VerificationFailure : RestoreTimestampResult
  data class RateLimited(val retryAfter: Duration?) : RestoreTimestampResult
  data object Failure : RestoreTimestampResult
}

enum class BackupMode {
  REMOTE,
  LINK_SYNC,
  LOCAL,
  PLAINTEXT_EXPORT;

  val isLinkAndSync: Boolean
    get() = this == LINK_SYNC

  val isLocalBackup: Boolean
    get() = this == LOCAL

  val isPlaintextExport: Boolean
    get() = this == PLAINTEXT_EXPORT
}

/**
 * Iterator that reads values from the given cursor. Expects that REMOTE_DIGEST is present and non-null, and ARCHIVE_CDN is present.
 *
 * This class does not assume ownership of the cursor. Recommended usage is within a use statement:
 *
 * ```
 * databaseCall().use { cursor ->
 *   val iterator = ArchivedMediaObjectIterator(cursor)
 *   // Use the iterator...
 * }
 * // Cursor is closed after use block.
 * ```
 */
class ArchiveMediaItemIterator(private val cursor: Cursor) : Iterator<ArchiveMediaItem> {

  init {
    cursor.moveToFirst()
  }

  override fun hasNext(): Boolean = !cursor.isAfterLast

  override fun next(): ArchiveMediaItem {
    val plaintextHash = cursor.requireNonNullString(AttachmentTable.DATA_HASH_END).decodeBase64OrThrow()
    val remoteKey = cursor.requireNonNullString(AttachmentTable.REMOTE_KEY).decodeBase64OrThrow()
    val cdn = cursor.requireIntOrNull(AttachmentTable.ARCHIVE_CDN)
    val quote = cursor.requireBoolean(AttachmentTable.QUOTE)
    val contentType = cursor.requireString(AttachmentTable.CONTENT_TYPE)

    val mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(REDStore.backup.mediaRootBackupKey).encode()
    val thumbnailMediaId = MediaName.fromPlaintextHashAndRemoteKeyForThumbnail(plaintextHash, remoteKey).toMediaId(REDStore.backup.mediaRootBackupKey).encode()

    cursor.moveToNext()

    return ArchiveMediaItem(
      mediaId = mediaId,
      thumbnailMediaId = thumbnailMediaId,
      cdn = cdn,
      plaintextHash = plaintextHash,
      remoteKey = remoteKey,
      quote = quote,
      contentType = contentType
    )
  }
}

data class UploadedThumbnailInfo(
  val cdnNumber: Int,
  val remoteLocation: String,
  val size: Long
)
