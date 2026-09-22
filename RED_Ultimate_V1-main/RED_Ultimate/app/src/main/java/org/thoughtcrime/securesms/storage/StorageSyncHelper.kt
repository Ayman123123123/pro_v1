package com.red.sovereign.storage

import android.content.Context
import androidx.annotation.VisibleForTesting
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64.encodeWithPadding
import org.signal.core.util.SqlUtil
import org.signal.core.util.Util
import org.signal.core.util.UuidUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.toByteArray
import org.signal.libsignal.net.KeyTransparency
import com.red.sovereign.backup.v2.MessageBackupTier
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository.getSubscriber
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository.isUserManuallyCancelled
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository.setSubscriber
import com.red.sovereign.database.NotificationProfileTables
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord
import com.red.sovereign.database.model.KeyTransparencyStore
import com.red.sovereign.database.model.RecipientRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.dependencies.KeyTransparencyApi
import com.red.sovereign.jobs.RefreshAttributesJob
import com.red.sovereign.jobs.RetrieveProfileAvatarJob
import com.red.sovereign.jobs.StorageSyncJob
import com.red.sovereign.keyvalue.AccountValues
import com.red.sovereign.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.notifications.profiles.NotificationProfileId
import com.red.sovereign.payments.Entropy
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.Recipient.Companion.self
import com.red.sovereign.util.TextSecurePreferences
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.storage.REDAccountRecord
import org.whispersystems.signalservice.api.storage.REDContactRecord
import org.whispersystems.signalservice.api.storage.REDStorageManifest
import org.whispersystems.signalservice.api.storage.REDStorageRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.safeSetBackupsSubscriber
import org.whispersystems.signalservice.api.storage.safeSetPayments
import org.whispersystems.signalservice.api.storage.safeSetSubscriber
import org.whispersystems.signalservice.api.storage.toREDAccountRecord
import org.whispersystems.signalservice.api.storage.toREDStorageRecord
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import org.whispersystems.signalservice.internal.storage.protos.OptionalBool
import java.util.Optional
import java.util.concurrent.TimeUnit

object StorageSyncHelper {
  private val TAG = Log.tag(StorageSyncHelper::class.java)

  val KEY_GENERATOR: StorageKeyGenerator = StorageKeyGenerator { Util.getSecretBytes(16) }

  private var keyGenerator = KEY_GENERATOR

  private val REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(2)

  /**
   * Given a list of all the local and remote keys you know about, this will return a result telling
   * you which keys are exclusively remote and which are exclusively local.
   *
   * @param remoteIds All remote keys available.
   * @param localIds  All local keys available.
   * @return An object describing which keys are exclusive to the remote data set and which keys are
   * exclusive to the local data set.
   */
  @JvmStatic
  fun findIdDifference(
    remoteIds: Collection<StorageId>,
    localIds: Collection<StorageId>
  ): IdDifferenceResult {
    val remoteByRawId: Map<String, StorageId> = remoteIds.associateBy { encodeWithPadding(it.raw) }
    val localByRawId: Map<String, StorageId> = localIds.associateBy { encodeWithPadding(it.raw) }

    var hasTypeMismatch = remoteByRawId.size != remoteIds.size || localByRawId.size != localIds.size

    val remoteOnlyRawIds: MutableSet<String> = (remoteByRawId.keys - localByRawId.keys).toMutableSet()
    val localOnlyRawIds: MutableSet<String> = (localByRawId.keys - remoteByRawId.keys).toMutableSet()
    val sharedRawIds: Set<String> = localByRawId.keys.intersect(remoteByRawId.keys)

    for (rawId in sharedRawIds) {
      val remote = remoteByRawId[rawId]!!
      val local = localByRawId[rawId]!!

      if (remote.type != local.type) {
        remoteOnlyRawIds.remove(rawId)
        localOnlyRawIds.remove(rawId)
        hasTypeMismatch = true
        Log.w(TAG, "Remote type ${remote.type} did not match local type ${local.type}!")
      }
    }

    val remoteOnlyKeys = remoteOnlyRawIds.mapNotNull { remoteByRawId[it] }
    val localOnlyKeys = localOnlyRawIds.mapNotNull { localByRawId[it] }

    return IdDifferenceResult(remoteOnlyKeys, localOnlyKeys, hasTypeMismatch)
  }

  @JvmStatic
  fun generateKey(): ByteArray {
    return keyGenerator.generate()
  }

  @JvmStatic
  @VisibleForTesting
  fun setTestKeyGenerator(testKeyGenerator: StorageKeyGenerator?) {
    keyGenerator = testKeyGenerator ?: KEY_GENERATOR
  }

  @JvmStatic
  fun profileKeyChanged(update: StorageRecordUpdate<REDContactRecord>): Boolean {
    return update.old.proto.profileKey != update.new.proto.profileKey
  }

  @JvmStatic
  fun buildAccountRecord(context: Context, self: Recipient): REDStorageRecord {
    var self = self
    var selfRecord: RecipientRecord? = REDDatabase.recipients.getRecordForSync(self.id)
    val pinned: List<RecipientRecord> = REDDatabase.threads.getPinnedRecipientIds()
      .mapNotNull { REDDatabase.recipients.getRecordForSync(it) }

    val storyViewReceiptsState = if (REDStore.story.viewedReceiptsEnabled) {
      OptionalBool.ENABLED
    } else {
      OptionalBool.DISABLED
    }

    if (self.storageId == null || (selfRecord != null && selfRecord.storageId == null)) {
      Log.w(TAG, "[buildAccountRecord] No storageId for self or record! Generating. (Self: ${self.storageId != null}, Record: ${selfRecord?.storageId != null})")
      REDDatabase.recipients.updateStorageId(self.id, generateKey())
      self = self().fresh()
      selfRecord = REDDatabase.recipients.getRecordForSync(self.id)
    }

    if (selfRecord == null) {
      Log.w(TAG, "[buildAccountRecord] Could not find a RecipientRecord for ourselves! ID: ${self.id}")
    } else if (!selfRecord.storageId.contentEquals(self.storageId)) {
      Log.w(TAG, "[buildAccountRecord] StorageId on RecipientRecord did not match self! ID: ${self.id}")
    }

    val storageId = selfRecord?.storageId ?: self.storageId

    val releaseChannelRecord: RecipientRecord? = REDStore.releaseChannel.releaseChannelRecipientId?.let { REDDatabase.recipients.getRecordForSync(it) }

    val accountRecord = REDAccountRecord.newBuilder(selfRecord?.syncExtras?.storageProto).apply {
      profileKey = self.profileKey?.toByteString() ?: ByteString.EMPTY
      givenName = self.profileName.givenName
      familyName = self.profileName.familyName
      avatarUrlPath = self.profileAvatar ?: ""
      noteToSelfArchived = selfRecord != null && selfRecord.syncExtras.isArchived
      noteToSelfMarkedUnread = selfRecord != null && selfRecord.syncExtras.isForcedUnread
      typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(context)
      readReceipts = TextSecurePreferences.isReadReceiptsEnabled(context)
      sealedSenderIndicators = TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context)
      linkPreviews = REDStore.settings.isLinkPreviewsEnabled
      unlistedPhoneNumber = REDStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE
      phoneNumberSharingMode = StorageSyncModels.localToRemotePhoneNumberSharingMode(REDStore.phoneNumberPrivacy.phoneNumberSharingMode)
      pinnedConversations = StorageSyncModels.localToRemotePinnedConversations(pinned)
      preferContactAvatars = REDStore.settings.isPreferSystemContactPhotos
      primarySendsSms = false
      universalExpireTimer = REDStore.settings.universalExpireTimer
      preferredReactionEmoji = REDStore.emoji.reactions
      displayBadgesOnProfile = REDStore.inAppPayments.getDisplayBadgesOnProfile()
      subscriptionManuallyCancelled = isUserManuallyCancelled(InAppPaymentSubscriberRecord.Type.DONATION)
      keepMutedChatsArchived = REDStore.settings.shouldKeepMutedChatsArchived()
      hasSetMyStoriesPrivacy = REDStore.story.userHasBeenNotifiedAboutStories
      hasViewedOnboardingStory = REDStore.story.userHasViewedOnboardingStory
      storiesDisabled = REDStore.story.isFeatureDisabled
      storyViewReceiptsEnabled = storyViewReceiptsState
      hasSeenGroupStoryEducationSheet = REDStore.story.userHasSeenGroupStoryEducationSheet
      hasCompletedUsernameOnboarding = REDStore.uiHints.hasCompletedUsernameOnboarding()
      avatarColor = StorageSyncModels.localToRemoteAvatarColor(self.avatarColor)
      username = REDStore.account.username ?: ""
      usernameLink = REDStore.account.usernameLink?.let { linkComponents ->
        AccountRecord.UsernameLink(
          entropy = linkComponents.entropy.toByteString(),
          serverId = linkComponents.serverId.toByteArray().toByteString(),
          color = StorageSyncModels.localToRemoteUsernameColor(REDStore.misc.usernameQrCodeColorScheme)
        )
      }

      backupTier = when {
        REDStore.account.isLinkedDevice -> null
        REDStore.backup.areBackupsEnabled && REDStore.backup.backupTier != null -> REDStore.backup.backupTier!!.toBackupLevel()
        REDStore.backup.backupTierInternalOverride != null -> REDStore.backup.backupTierInternalOverride!!.toBackupLevel()
        else -> null
      }

      notificationProfileManualOverride = getNotificationProfileManualOverride()

      getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)?.let {
        safeSetSubscriber(it.subscriberId.bytes.toByteString(), it.currency?.currencyCode ?: "")
      }

      getSubscriber(InAppPaymentSubscriberRecord.Type.BACKUP)?.let {
        safeSetBackupsSubscriber(it.subscriberId.bytes.toByteString(), it.iapSubscriptionId)
      }

      safeSetPayments(REDStore.payments.mobileCoinPaymentsEnabled(), Optional.ofNullable(REDStore.payments.paymentsEntropy).map { obj: Entropy -> obj.bytes }.orElse(null))
      automaticKeyVerificationDisabled = !REDStore.settings.automaticVerificationEnabled
      hasSeenAdminDeleteEducationDialog = REDStore.uiHints.hasSeenAdminDeleteEducationDialog()

      if (releaseChannelRecord != null) {
        releaseNotesChatArchived = releaseChannelRecord.syncExtras.isArchived == true
        releaseNotesChatMutedUntilTimestamp = releaseChannelRecord.muteUntil
        releaseNotesChatBlocked = releaseChannelRecord.isBlocked == true
        releaseNotesChatMarkedUnread = releaseChannelRecord.syncExtras.isForcedUnread == true
      }
    }

    return accountRecord.toREDAccountRecord(StorageId.forAccount(storageId)).toREDStorageRecord()
  }

  private fun getNotificationProfileManualOverride(): AccountRecord.NotificationProfileManualOverride? {
    val profile = REDDatabase.notificationProfiles.getProfile(REDStore.notificationProfile.manuallyEnabledProfile)
    return if (profile != null && profile.deletedTimestampMs == 0L) {
      Log.i(TAG, "Setting a manually enabled profile ${profile.id}")
      // From [StorageService.proto], end timestamp should be unset if no timespan was chosen in the UI
      val endTimestamp = if (REDStore.notificationProfile.manuallyEnabledUntil == Long.MAX_VALUE) 0 else REDStore.notificationProfile.manuallyEnabledUntil
      AccountRecord.NotificationProfileManualOverride(
        enabled = AccountRecord.NotificationProfileManualOverride.ManuallyEnabled(
          id = UuidUtil.toByteArray(profile.notificationProfileId.uuid).toByteString(),
          endAtTimestampMs = endTimestamp
        )
      )
    } else if (REDStore.notificationProfile.manuallyDisabledAt != 0L) {
      Log.i(TAG, "Setting a manually disabled profile ${REDStore.notificationProfile.manuallyDisabledAt}")
      AccountRecord.NotificationProfileManualOverride(
        disabledAtTimestampMs = REDStore.notificationProfile.manuallyDisabledAt
      )
    } else {
      null
    }
  }

  @JvmStatic
  fun applyAccountStorageSyncUpdates(context: Context, self: Recipient, updatedRecord: REDAccountRecord, fetchProfile: Boolean) {
    val localRecord = buildAccountRecord(context, self).let { it.proto.account!!.toREDAccountRecord(it.id) }
    applyAccountStorageSyncUpdates(context, self, StorageRecordUpdate(localRecord, updatedRecord), fetchProfile)
  }

  @JvmStatic
  fun applyAccountStorageSyncUpdates(context: Context, self: Recipient, update: StorageRecordUpdate<REDAccountRecord>, fetchProfile: Boolean) {
    REDDatabase.recipients.applyStorageSyncAccountUpdate(update)

    TextSecurePreferences.setReadReceiptsEnabled(context, update.new.proto.readReceipts)
    TextSecurePreferences.setTypingIndicatorsEnabled(context, update.new.proto.typingIndicators)
    TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, update.new.proto.sealedSenderIndicators)
    REDStore.settings.isLinkPreviewsEnabled = update.new.proto.linkPreviews
    REDStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode = if (update.new.proto.unlistedPhoneNumber) PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE else PhoneNumberDiscoverabilityMode.DISCOVERABLE
    REDStore.phoneNumberPrivacy.phoneNumberSharingMode = StorageSyncModels.remoteToLocalPhoneNumberSharingMode(update.new.proto.phoneNumberSharingMode)
    REDStore.settings.isPreferSystemContactPhotos = update.new.proto.preferContactAvatars
    REDStore.payments.setEnabledAndEntropy(update.new.proto.payments?.enabled == true, Entropy.fromBytes(update.new.proto.payments?.entropy?.toByteArray()))
    REDStore.settings.universalExpireTimer = update.new.proto.universalExpireTimer
    REDStore.emoji.reactions = update.new.proto.preferredReactionEmoji
    REDStore.inAppPayments.setDisplayBadgesOnProfile(update.new.proto.displayBadgesOnProfile)
    REDStore.settings.setKeepMutedChatsArchived(update.new.proto.keepMutedChatsArchived)
    REDStore.story.userHasBeenNotifiedAboutStories = update.new.proto.hasSetMyStoriesPrivacy
    REDStore.story.userHasViewedOnboardingStory = update.new.proto.hasViewedOnboardingStory
    REDStore.story.isFeatureDisabled = update.new.proto.storiesDisabled
    REDStore.story.userHasSeenGroupStoryEducationSheet = update.new.proto.hasSeenGroupStoryEducationSheet
    REDStore.uiHints.setHasCompletedUsernameOnboarding(update.new.proto.hasCompletedUsernameOnboarding)

    if (update.new.proto.unlistedPhoneNumber != update.old.proto.unlistedPhoneNumber && REDStore.account.isPrimaryDevice) {
      Log.i(TAG, "Phone number discoverability changed via storage service. Refreshing attributes to push the change to the server.")
      AppDependencies.jobManager.add(RefreshAttributesJob())
    }

    if (REDStore.settings.automaticVerificationEnabled && update.new.proto.automaticKeyVerificationDisabled) {
      REDDatabase.recipients.clearAllKeyTransparencyData()
    }
    REDStore.settings.automaticVerificationEnabled = !update.new.proto.automaticKeyVerificationDisabled

    if (update.new.proto.hasSeenAdminDeleteEducationDialog) {
      REDStore.uiHints.setHasSeenAdminDeleteEducationDialog()
    }

    if (update.new.proto.storyViewReceiptsEnabled == OptionalBool.UNSET) {
      REDStore.story.viewedReceiptsEnabled = update.new.proto.readReceipts
    } else {
      REDStore.story.viewedReceiptsEnabled = update.new.proto.storyViewReceiptsEnabled == OptionalBool.ENABLED
    }

    val remoteSubscriber = StorageSyncModels.remoteToLocalDonorSubscriber(update.new.proto.subscriberId, update.new.proto.subscriberCurrencyCode)
    if (remoteSubscriber != null) {
      setSubscriber(remoteSubscriber)
    }

    val remoteBackupsSubscriber = StorageSyncModels.remoteToLocalBackupSubscriber(update.new.proto.backupSubscriberData)
    if (remoteBackupsSubscriber != null) {
      setSubscriber(remoteBackupsSubscriber)
    }

    if (REDStore.account.isLinkedDevice) {
      val remoteBackupTier = MessageBackupTier.fromBackupLevel(update.new.proto.backupTier)
      if (remoteBackupTier != REDStore.backup.backupTier) {
        REDStore.backup.backupTier = remoteBackupTier
      }
    }

    if (update.new.proto.subscriptionManuallyCancelled && !update.old.proto.subscriptionManuallyCancelled) {
      REDStore.inAppPayments.updateLocalStateForManualCancellation(InAppPaymentSubscriberRecord.Type.DONATION)
    }

    if (fetchProfile && update.new.proto.avatarUrlPath.isNotBlank()) {
      AppDependencies.jobManager.add(RetrieveProfileAvatarJob(self, update.new.proto.avatarUrlPath))
    }

    if (update.new.proto.username != update.old.proto.username) {
      REDStore.account.username = update.new.proto.username
      REDStore.account.usernameSyncState = AccountValues.UsernameSyncState.IN_SYNC
      REDStore.account.usernameSyncErrorCount = 0

      Log.i(TAG, "Resetting KT data due to username change in storage service.")
      KeyTransparencyApi.reset(aci = REDStore.account.requireAci().libREDAci, field = KeyTransparency.AccountDataField.USERNAME_HASH, keyTransparencyStore = KeyTransparencyStore)
    }

    if (update.new.proto.usernameLink != null) {
      val remoteServerId = UuidUtil.parseOrNull(update.new.proto.usernameLink!!.serverId.toByteArray())

      if (remoteServerId != null) {
        REDStore.account.usernameLink = UsernameLinkComponents(
          update.new.proto.usernameLink!!.entropy.toByteArray(),
          remoteServerId
        )

        REDStore.misc.usernameQrCodeColorScheme = StorageSyncModels.remoteToLocalUsernameColor(update.new.proto.usernameLink!!.color)
      } else {
        Log.w(TAG, "Remote username link had a malformed serverId. Ignoring the username link.")
      }
    }

    REDStore.releaseChannel.releaseChannelRecipientId?.let { releaseChannelId ->
      update.new.proto.releaseNotesChatBlocked?.let { REDDatabase.recipients.setBlocked(releaseChannelId, it) }
      update.new.proto.releaseNotesChatMutedUntilTimestamp?.let { REDDatabase.recipients.setMuted(releaseChannelId, it) }
      if (update.new.proto.releaseNotesChatArchived != null && update.new.proto.releaseNotesChatMarkedUnread != null) {
        REDDatabase.threads.applyStorageSyncReleaseChannelUpdate(releaseChannelId, update.new.proto.releaseNotesChatArchived!!, update.new.proto.releaseNotesChatMarkedUnread!!)
      }
      Recipient.live(releaseChannelId).refresh()
    }

    if (update.new.proto.notificationProfileManualOverride != null) {
      if (update.new.proto.notificationProfileManualOverride!!.enabled != null) {
        Log.i(TAG, "Found a remote enabled notification override")
        val remoteProfile = update.new.proto.notificationProfileManualOverride!!.enabled!!
        val remoteId = UuidUtil.parseOrNull(remoteProfile.id)
        val remoteEndTime = if (remoteProfile.endAtTimestampMs == 0L) Long.MAX_VALUE else remoteProfile.endAtTimestampMs

        if (remoteId == null) {
          Log.w(TAG, "Remote notification profile id is not valid")
        } else {
          val query = SqlUtil.buildQuery("${NotificationProfileTables.NotificationProfileTable.NOTIFICATION_PROFILE_ID} = ?", NotificationProfileId(remoteId))
          val localProfile = REDDatabase.notificationProfiles.getProfile(query)

          if (localProfile == null) {
            Log.w(TAG, "Unable to find local notification profile with given remote id $remoteId")
          } else {
            Log.i(TAG, "Setting manually enabled profile to ${localProfile.id} ending at $remoteEndTime.")
            REDStore.notificationProfile.manuallyEnabledProfile = localProfile.id
            REDStore.notificationProfile.manuallyEnabledUntil = remoteEndTime
            REDStore.notificationProfile.manuallyDisabledAt = 0L
          }
        }
      } else if (update.new.proto.notificationProfileManualOverride!!.disabledAtTimestampMs != null) {
        Log.i(TAG, "Found a remote disabled notification override for ${update.new.proto.notificationProfileManualOverride!!.disabledAtTimestampMs!!}")
        REDStore.notificationProfile.manuallyEnabledProfile = 0
        REDStore.notificationProfile.manuallyEnabledUntil = 0
        REDStore.notificationProfile.manuallyDisabledAt = update.new.proto.notificationProfileManualOverride!!.disabledAtTimestampMs!!
      }
    }
  }

  @JvmStatic
  fun scheduleSyncForDataChange() {
    if (!REDStore.registration.isRegistrationComplete) {
      Log.d(TAG, "Registration still ongoing. Ignore sync request.")
      return
    }
    AppDependencies.jobManager.add(StorageSyncJob.forLocalChange())
  }

  @JvmStatic
  fun scheduleRoutineSync() {
    val timeSinceLastSync = System.currentTimeMillis() - REDStore.storageService.lastSyncTime

    if (timeSinceLastSync > REFRESH_INTERVAL && REDStore.registration.isRegistrationComplete) {
      Log.d(TAG, "Scheduling a sync. Last sync was $timeSinceLastSync ms ago.")
      AppDependencies.jobManager.add(StorageSyncJob.forRemoteChange())
    } else {
      Log.d(TAG, "No need for sync. Last sync was $timeSinceLastSync ms ago.")
    }
  }

  class IdDifferenceResult(
    @JvmField val remoteOnlyIds: List<StorageId>,
    @JvmField val localOnlyIds: List<StorageId>,
    val hasTypeMismatches: Boolean
  ) {
    val isEmpty: Boolean
      get() = remoteOnlyIds.isEmpty() && localOnlyIds.isEmpty()

    override fun toString(): String {
      return "remoteOnly: ${remoteOnlyIds.size}, localOnly: ${localOnlyIds.size}, hasTypeMismatches: $hasTypeMismatches"
    }
  }

  class WriteOperationResult(
    @JvmField val manifest: REDStorageManifest,
    @JvmField val inserts: List<REDStorageRecord>,
    @JvmField val deletes: List<ByteArray>
  ) {
    val isEmpty: Boolean
      get() = inserts.isEmpty() && deletes.isEmpty()

    override fun toString(): String {
      return if (isEmpty) {
        "Empty"
      } else {
        "ManifestVersion: ${manifest.version}, Total Keys: ${manifest.storageIds.size}, Inserts: ${inserts.size}, Deletes: ${deletes.size}"
      }
    }
  }
}
