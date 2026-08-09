package com.red.sovereign.storage

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.models.ServiceId
import org.signal.core.util.Hex
import org.signal.core.util.UuidUtil
import org.signal.core.util.isNotEmpty
import org.signal.core.util.isNullOrEmpty
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import com.red.sovereign.components.settings.app.chats.folders.ChatFolderRecord
import com.red.sovereign.components.settings.app.usernamelinks.UsernameQrCodeColorScheme
import com.red.sovereign.conversation.colors.AvatarColor
import com.red.sovereign.database.GroupTable.ShowAsStoryState
import com.red.sovereign.database.IdentityTable.VerifiedStatus
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.RecipientTable.RecipientType
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.REDDatabase.Companion.callLinks
import com.red.sovereign.database.REDDatabase.Companion.distributionLists
import com.red.sovereign.database.REDDatabase.Companion.groups
import com.red.sovereign.database.REDDatabase.Companion.inAppPaymentSubscribers
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord
import com.red.sovereign.database.model.RecipientRecord
import com.red.sovereign.database.model.StickerPackSyncRecord
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData
import com.red.sovereign.groups.BadGroupIdException
import com.red.sovereign.groups.GroupId
import com.red.sovereign.keyvalue.PhoneNumberPrivacyValues
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.notifications.profiles.NotificationProfile
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import org.whispersystems.signalservice.api.push.REDServiceAddress
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.api.storage.REDCallLinkRecord
import org.whispersystems.signalservice.api.storage.REDChatFolderRecord
import org.whispersystems.signalservice.api.storage.REDContactRecord
import org.whispersystems.signalservice.api.storage.REDGroupV1Record
import org.whispersystems.signalservice.api.storage.REDGroupV2Record
import org.whispersystems.signalservice.api.storage.REDNotificationProfileRecord
import org.whispersystems.signalservice.api.storage.REDStickerPackRecord
import org.whispersystems.signalservice.api.storage.REDStorageRecord
import org.whispersystems.signalservice.api.storage.REDStoryDistributionListRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.toREDCallLinkRecord
import org.whispersystems.signalservice.api.storage.toREDChatFolderRecord
import org.whispersystems.signalservice.api.storage.toREDContactRecord
import org.whispersystems.signalservice.api.storage.toREDGroupV1Record
import org.whispersystems.signalservice.api.storage.toREDGroupV2Record
import org.whispersystems.signalservice.api.storage.toREDNotificationProfileRecord
import org.whispersystems.signalservice.api.storage.toREDStickerPackRecord
import org.whispersystems.signalservice.api.storage.toREDStorageRecord
import org.whispersystems.signalservice.api.storage.toREDStoryDistributionListRecord
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record
import java.time.DayOfWeek
import java.util.Currency
import kotlin.math.max
import org.whispersystems.signalservice.internal.storage.protos.AvatarColor as RemoteAvatarColor
import org.whispersystems.signalservice.internal.storage.protos.ChatFolderRecord as RemoteChatFolder
import org.whispersystems.signalservice.internal.storage.protos.NotificationProfile.DayOfWeek as RemoteDayOfWeek
import org.whispersystems.signalservice.internal.storage.protos.Recipient as RemoteRecipient

object StorageSyncModels {

  private val TAG = Log.tag(StorageSyncModels::class.java)

  fun localToRemoteRecord(settings: RecipientRecord): REDStorageRecord {
    if (settings.storageId == null) {
      throw AssertionError("Must have a storage key!")
    }

    return localToRemoteRecord(settings, settings.storageId)
  }

  fun localToRemoteRecord(settings: RecipientRecord, groupMasterKey: GroupMasterKey): REDStorageRecord {
    if (settings.storageId == null) {
      throw AssertionError("Must have a storage key!")
    }

    return localToRemoteGroupV2(settings, settings.storageId, groupMasterKey).toREDStorageRecord()
  }

  fun localToRemoteRecord(settings: RecipientRecord, rawStorageId: ByteArray): REDStorageRecord {
    return when (settings.recipientType) {
      RecipientType.INDIVIDUAL -> localToRemoteContact(settings, rawStorageId).toREDStorageRecord()
      RecipientType.GV1 -> localToRemoteGroupV1(settings, rawStorageId).toREDStorageRecord()
      RecipientType.GV2 -> localToRemoteGroupV2(settings, rawStorageId, settings.syncExtras.groupMasterKey!!).toREDStorageRecord()
      RecipientType.DISTRIBUTION_LIST -> localToRemoteStoryDistributionList(settings, rawStorageId).toREDStorageRecord()
      RecipientType.CALL_LINK -> localToRemoteCallLink(settings, rawStorageId).toREDStorageRecord()
      else -> throw AssertionError("Unsupported type!")
    }
  }

  fun localToRemoteRecord(folder: ChatFolderRecord, rawStorageId: ByteArray): REDStorageRecord {
    return localToRemoteChatFolder(folder, rawStorageId).toREDStorageRecord()
  }

  fun localToRemoteRecord(profile: NotificationProfile, rawStorageId: ByteArray): REDStorageRecord {
    return localToRemoteNotificationProfile(profile, rawStorageId).toREDStorageRecord()
  }

  fun localToRemoteRecord(pack: StickerPackSyncRecord, rawStorageId: ByteArray): REDStorageRecord {
    return localToRemoteStickerPack(pack, rawStorageId).toREDStorageRecord()
  }

  @JvmStatic
  fun localToRemotePhoneNumberSharingMode(phoneNumberPhoneNumberSharingMode: PhoneNumberPrivacyValues.PhoneNumberSharingMode): AccountRecord.PhoneNumberSharingMode {
    return when (phoneNumberPhoneNumberSharingMode) {
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.DEFAULT -> AccountRecord.PhoneNumberSharingMode.NOBODY
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYBODY -> AccountRecord.PhoneNumberSharingMode.EVERYBODY
      PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY -> AccountRecord.PhoneNumberSharingMode.NOBODY
    }
  }

  @JvmStatic
  fun remoteToLocalPhoneNumberSharingMode(phoneNumberPhoneNumberSharingMode: AccountRecord.PhoneNumberSharingMode): PhoneNumberPrivacyValues.PhoneNumberSharingMode {
    return when (phoneNumberPhoneNumberSharingMode) {
      AccountRecord.PhoneNumberSharingMode.EVERYBODY -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.EVERYBODY
      AccountRecord.PhoneNumberSharingMode.NOBODY -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.NOBODY
      else -> PhoneNumberPrivacyValues.PhoneNumberSharingMode.DEFAULT
    }
  }

  @JvmStatic
  fun localToRemotePinnedConversations(records: List<RecipientRecord>): List<AccountRecord.PinnedConversation> {
    val releaseChannelId = REDStore.releaseChannel.releaseChannelRecipientId
    return records
      .filter { it.recipientType == RecipientType.GV1 || it.recipientType == RecipientType.GV2 || it.registered == RecipientTable.RegisteredState.REGISTERED || it.id == releaseChannelId }
      .map { localToRemotePinnedConversation(it, releaseChannelId) }
  }

  @JvmStatic
  private fun localToRemotePinnedConversation(settings: RecipientRecord, releaseChannelId: RecipientId?): AccountRecord.PinnedConversation {
    if (settings.id == releaseChannelId) {
      return AccountRecord.PinnedConversation(releaseNotes = AccountRecord.PinnedConversation.ReleaseNotes())
    }
    return when (settings.recipientType) {
      RecipientType.INDIVIDUAL -> {
        AccountRecord.PinnedConversation(
          contact = AccountRecord.PinnedConversation.Contact(
            serviceId = "",
            e164 = settings.e164 ?: "",
            serviceIdBinary = settings.serviceId?.toByteString() ?: ByteString.EMPTY
          )
        )
      }
      RecipientType.GV1 -> {
        AccountRecord.PinnedConversation(
          legacyGroupId = settings.groupId!!.requireV1().decodedId.toByteString()
        )
      }
      RecipientType.GV2 -> {
        AccountRecord.PinnedConversation(
          groupMasterKey = settings.syncExtras.groupMasterKey!!.serialize().toByteString()
        )
      }
      else -> throw AssertionError("Unexpected group type!")
    }
  }

  @JvmStatic
  fun localToRemoteUsernameColor(local: UsernameQrCodeColorScheme): AccountRecord.UsernameLink.Color {
    return when (local) {
      UsernameQrCodeColorScheme.Blue -> AccountRecord.UsernameLink.Color.BLUE
      UsernameQrCodeColorScheme.White -> AccountRecord.UsernameLink.Color.WHITE
      UsernameQrCodeColorScheme.Grey -> AccountRecord.UsernameLink.Color.GREY
      UsernameQrCodeColorScheme.Tan -> AccountRecord.UsernameLink.Color.OLIVE
      UsernameQrCodeColorScheme.Green -> AccountRecord.UsernameLink.Color.GREEN
      UsernameQrCodeColorScheme.Orange -> AccountRecord.UsernameLink.Color.ORANGE
      UsernameQrCodeColorScheme.Pink -> AccountRecord.UsernameLink.Color.PINK
      UsernameQrCodeColorScheme.Purple -> AccountRecord.UsernameLink.Color.PURPLE
    }
  }

  @JvmStatic
  fun remoteToLocalUsernameColor(remote: AccountRecord.UsernameLink.Color): UsernameQrCodeColorScheme {
    return when (remote) {
      AccountRecord.UsernameLink.Color.BLUE -> UsernameQrCodeColorScheme.Blue
      AccountRecord.UsernameLink.Color.WHITE -> UsernameQrCodeColorScheme.White
      AccountRecord.UsernameLink.Color.GREY -> UsernameQrCodeColorScheme.Grey
      AccountRecord.UsernameLink.Color.OLIVE -> UsernameQrCodeColorScheme.Tan
      AccountRecord.UsernameLink.Color.GREEN -> UsernameQrCodeColorScheme.Green
      AccountRecord.UsernameLink.Color.ORANGE -> UsernameQrCodeColorScheme.Orange
      AccountRecord.UsernameLink.Color.PINK -> UsernameQrCodeColorScheme.Pink
      AccountRecord.UsernameLink.Color.PURPLE -> UsernameQrCodeColorScheme.Purple
      else -> UsernameQrCodeColorScheme.Blue
    }
  }

  private fun localToRemoteContact(recipient: RecipientRecord, rawStorageId: ByteArray): REDContactRecord {
    if (recipient.aci == null && recipient.pni == null && recipient.e164 == null) {
      throw AssertionError("Must have either a UUID or a phone number!")
    }

    return REDContactRecord.newBuilder(recipient.syncExtras.storageProto).apply {
      aciBinary = recipient.aci?.toByteString() ?: ByteString.EMPTY
      aci = ""
      e164 = recipient.e164 ?: ""
      pniBinary = recipient.pni?.toByteStringWithoutPrefix() ?: ByteString.EMPTY
      pni = ""
      profileKey = recipient.profileKey?.toByteString() ?: ByteString.EMPTY
      givenName = recipient.signalProfileName.givenName
      familyName = recipient.signalProfileName.familyName
      systemGivenName = recipient.systemProfileName.givenName
      systemFamilyName = recipient.systemProfileName.familyName
      systemNickname = recipient.syncExtras.systemNickname ?: ""
      blocked = recipient.isBlocked
      whitelisted = recipient.profileSharing || recipient.systemContactUri != null
      identityKey = recipient.syncExtras.identityKey?.toByteString() ?: ByteString.EMPTY
      identityState = localToRemoteIdentityState(recipient.syncExtras.identityStatus)
      archived = recipient.syncExtras.isArchived
      markedUnread = recipient.syncExtras.isForcedUnread
      mutedUntilTimestamp = recipient.muteUntil
      hideStory = recipient.extras != null && recipient.extras.hideStory()
      unregisteredAtTimestamp = recipient.syncExtras.unregisteredTimestamp
      hidden = recipient.hiddenState != Recipient.HiddenState.NOT_HIDDEN
      username = recipient.username ?: ""
      pniSignatureVerified = recipient.syncExtras.pniSignatureVerified
      nickname = recipient.nickname.takeUnless { it.isEmpty }?.let { ContactRecord.Name(given = it.givenName, family = it.familyName) }
      note = recipient.note ?: ""
      avatarColor = localToRemoteAvatarColor(recipient.avatarColor)
    }.build().toREDContactRecord(StorageId.forContact(rawStorageId))
  }

  private fun localToRemoteGroupV1(recipient: RecipientRecord, rawStorageId: ByteArray): REDGroupV1Record {
    val groupId = recipient.groupId ?: throw AssertionError("Must have a groupId!")

    if (!groupId.isV1) {
      throw AssertionError("Group is not V1")
    }

    return REDGroupV1Record.newBuilder(recipient.syncExtras.storageProto).apply {
      id = recipient.groupId.requireV1().decodedId.toByteString()
      blocked = recipient.isBlocked
      whitelisted = recipient.profileSharing
      archived = recipient.syncExtras.isArchived
      markedUnread = recipient.syncExtras.isForcedUnread
      mutedUntilTimestamp = recipient.muteUntil
    }.build().toREDGroupV1Record(StorageId.forGroupV1(rawStorageId))
  }

  private fun localToRemoteGroupV2(recipient: RecipientRecord, rawStorageId: ByteArray?, groupMasterKey: GroupMasterKey): REDGroupV2Record {
    val groupId = recipient.groupId ?: throw AssertionError("Must have a groupId!")

    if (!groupId.isV2) {
      throw AssertionError("Group is not V2")
    }

    val localVerifiedNameHash: ByteArray? = groups.getGroup(groupId).orElse(null)?.verifiedNameHash

    return REDGroupV2Record.newBuilder(recipient.syncExtras.storageProto).apply {
      masterKey = groupMasterKey.serialize().toByteString()
      blocked = recipient.isBlocked
      whitelisted = recipient.profileSharing
      archived = recipient.syncExtras.isArchived
      markedUnread = recipient.syncExtras.isForcedUnread
      mutedUntilTimestamp = recipient.muteUntil
      dontNotifyForMentionsIfMuted = recipient.mentionSetting == RecipientTable.NotificationSetting.DO_NOT_NOTIFY
      hideStory = recipient.extras != null && recipient.extras.hideStory()
      avatarColor = localToRemoteAvatarColor(recipient.avatarColor)
      storySendMode = when (groups.getShowAsStoryState(groupId)) {
        ShowAsStoryState.ALWAYS -> GroupV2Record.StorySendMode.ENABLED
        ShowAsStoryState.NEVER -> GroupV2Record.StorySendMode.DISABLED
        else -> GroupV2Record.StorySendMode.DEFAULT
      }
      if (localVerifiedNameHash != null) {
        verifiedNameHash = localVerifiedNameHash.toByteString()
      }
    }.build().toREDGroupV2Record(StorageId.forGroupV2(rawStorageId))
  }

  private fun localToRemoteCallLink(recipient: RecipientRecord, rawStorageId: ByteArray): REDCallLinkRecord {
    val callLinkRoomId = recipient.callLinkRoomId ?: throw AssertionError("Must have a callLinkRoomId!")

    val callLink = callLinks.getCallLinkByRoomId(callLinkRoomId) ?: throw AssertionError("Must have a call link record!")

    if (callLink.credentials == null) {
      throw AssertionError("Must have call link credentials!")
    }

    val deletedTimestamp = max(0.0, callLinks.getDeletedTimestampByRoomId(callLinkRoomId).toDouble()).toLong()
    val adminPassword = if (deletedTimestamp > 0) byteArrayOf() else callLink.credentials.adminPassBytes!!

    return REDCallLinkRecord.newBuilder(null).apply {
      rootKey = callLink.credentials.linkKeyBytes.toByteString()
      adminPasskey = adminPassword.toByteString()
      deletedAtTimestampMs = deletedTimestamp
    }.build().toREDCallLinkRecord(StorageId.forCallLink(rawStorageId))
  }

  private fun localToRemoteStoryDistributionList(recipient: RecipientRecord, rawStorageId: ByteArray): REDStoryDistributionListRecord {
    val distributionListId = recipient.distributionListId ?: throw AssertionError("Must have a distributionListId!")

    val record = distributionLists.getListForStorageSync(distributionListId) ?: throw AssertionError("Must have a distribution list record!")

    if (record.deletedAtTimestamp > 0L) {
      return REDStoryDistributionListRecord.newBuilder(recipient.syncExtras.storageProto).apply {
        identifier = UuidUtil.toByteArray(record.distributionId.asUuid()).toByteString()
        deletedAtTimestamp = record.deletedAtTimestamp
      }.build().toREDStoryDistributionListRecord(StorageId.forStoryDistributionList(rawStorageId))
    }

    return REDStoryDistributionListRecord.newBuilder(recipient.syncExtras.storageProto).apply {
      identifier = UuidUtil.toByteArray(record.distributionId.asUuid()).toByteString()
      name = record.name
      recipientServiceIds = emptyList()
      recipientServiceIdsBinary = record.getMembersToSync()
        .map { Recipient.resolved(it) }
        .filter { it.hasServiceId }
        .map { it.requireServiceId().toByteString() }
      allowsReplies = record.allowsReplies
      isBlockList = record.privacyMode.isBlockList
    }.build().toREDStoryDistributionListRecord(StorageId.forStoryDistributionList(rawStorageId))
  }

  fun remoteToLocalIdentityStatus(identityState: IdentityState): VerifiedStatus {
    return when (identityState) {
      IdentityState.VERIFIED -> VerifiedStatus.VERIFIED
      IdentityState.UNVERIFIED -> VerifiedStatus.UNVERIFIED
      else -> VerifiedStatus.DEFAULT
    }
  }

  private fun localToRemoteIdentityState(local: VerifiedStatus): IdentityState {
    return when (local) {
      VerifiedStatus.VERIFIED -> IdentityState.VERIFIED
      VerifiedStatus.UNVERIFIED -> IdentityState.UNVERIFIED
      else -> IdentityState.DEFAULT
    }
  }

  fun remoteToLocalBackupSubscriber(
    iapData: AccountRecord.IAPSubscriberData?
  ): InAppPaymentSubscriberRecord? {
    if (iapData == null || iapData.subscriberId.isNullOrEmpty()) {
      return null
    }

    val subscriberId = SubscriberId.fromBytes(iapData.subscriberId.toByteArray())
    val localSubscriberRecord = inAppPaymentSubscribers.getBySubscriberId(subscriberId)
    val requiresCancel = localSubscriberRecord != null && localSubscriberRecord.requiresCancel
    val paymentMethodType = localSubscriberRecord?.paymentMethodType ?: InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING
    val iapSubscriptionId = IAPSubscriptionId.from(iapData) ?: return null

    return InAppPaymentSubscriberRecord(
      subscriberId = subscriberId,
      currency = null,
      type = InAppPaymentSubscriberRecord.Type.BACKUP,
      requiresCancel = requiresCancel,
      paymentMethodType = paymentMethodType,
      iapSubscriptionId = iapSubscriptionId
    )
  }

  fun remoteToLocalDonorSubscriber(
    subscriberId: ByteString,
    subscriberCurrencyCode: String
  ): InAppPaymentSubscriberRecord? {
    if (subscriberId.isNotEmpty()) {
      val subscriberId = SubscriberId.fromBytes(subscriberId.toByteArray())
      val localSubscriberRecord = inAppPaymentSubscribers.getBySubscriberId(subscriberId)
      val requiresCancel = localSubscriberRecord != null && localSubscriberRecord.requiresCancel
      val paymentMethodType = localSubscriberRecord?.paymentMethodType ?: InAppPaymentData.PaymentMethodType.UNKNOWN

      val currency: Currency
      if (subscriberCurrencyCode.isBlank()) {
        return null
      } else {
        try {
          currency = Currency.getInstance(subscriberCurrencyCode)
        } catch (e: IllegalArgumentException) {
          return null
        }
      }

      return InAppPaymentSubscriberRecord(
        subscriberId = subscriberId,
        currency = currency,
        type = InAppPaymentSubscriberRecord.Type.DONATION,
        requiresCancel = requiresCancel,
        paymentMethodType = paymentMethodType,
        iapSubscriptionId = null
      )
    } else {
      return null
    }
  }

  fun localToRemoteAvatarColor(avatarColor: AvatarColor): RemoteAvatarColor {
    return when (avatarColor) {
      AvatarColor.A100 -> RemoteAvatarColor.A100
      AvatarColor.A110 -> RemoteAvatarColor.A110
      AvatarColor.A120 -> RemoteAvatarColor.A120
      AvatarColor.A130 -> RemoteAvatarColor.A130
      AvatarColor.A140 -> RemoteAvatarColor.A140
      AvatarColor.A150 -> RemoteAvatarColor.A150
      AvatarColor.A160 -> RemoteAvatarColor.A160
      AvatarColor.A170 -> RemoteAvatarColor.A170
      AvatarColor.A180 -> RemoteAvatarColor.A180
      AvatarColor.A190 -> RemoteAvatarColor.A190
      AvatarColor.A200 -> RemoteAvatarColor.A200
      AvatarColor.A210 -> RemoteAvatarColor.A210
      AvatarColor.UNKNOWN -> RemoteAvatarColor.A100
      AvatarColor.ON_SURFACE_VARIANT -> RemoteAvatarColor.A100
    }
  }

  fun remoteToLocalAvatarColor(avatarColor: RemoteAvatarColor?): AvatarColor? {
    return when (avatarColor) {
      RemoteAvatarColor.A100 -> AvatarColor.A100
      RemoteAvatarColor.A110 -> AvatarColor.A110
      RemoteAvatarColor.A120 -> AvatarColor.A120
      RemoteAvatarColor.A130 -> AvatarColor.A130
      RemoteAvatarColor.A140 -> AvatarColor.A140
      RemoteAvatarColor.A150 -> AvatarColor.A150
      RemoteAvatarColor.A160 -> AvatarColor.A160
      RemoteAvatarColor.A170 -> AvatarColor.A170
      RemoteAvatarColor.A180 -> AvatarColor.A180
      RemoteAvatarColor.A190 -> AvatarColor.A190
      RemoteAvatarColor.A200 -> AvatarColor.A200
      RemoteAvatarColor.A210 -> AvatarColor.A210
      null -> null
    }
  }

  fun localToRemoteChatFolder(folder: ChatFolderRecord, rawStorageId: ByteArray?): REDChatFolderRecord {
    if (folder.chatFolderId == null) {
      throw AssertionError("Chat folder must have a chat folder id.")
    }
    return REDChatFolderRecord.newBuilder(folder.storageServiceProto).apply {
      identifier = UuidUtil.toByteArray(folder.chatFolderId.uuid).toByteString()
      name = folder.name
      position = folder.position
      showOnlyUnread = folder.showUnread
      showMutedChats = folder.showMutedChats
      includeAllIndividualChats = folder.showIndividualChats
      includeAllGroupChats = folder.showGroupChats
      folderType = when (folder.folderType) {
        ChatFolderRecord.FolderType.ALL -> RemoteChatFolder.FolderType.ALL
        ChatFolderRecord.FolderType.INDIVIDUAL,
        ChatFolderRecord.FolderType.GROUP,
        ChatFolderRecord.FolderType.UNREAD,
        ChatFolderRecord.FolderType.CUSTOM -> RemoteChatFolder.FolderType.CUSTOM
      }
      includedRecipients = localToRemoteChatFolderRecipients(folder.includedChats)
      excludedRecipients = localToRemoteChatFolderRecipients(folder.excludedChats)
      deletedAtTimestampMs = folder.deletedTimestampMs
    }.build().toREDChatFolderRecord(StorageId.forChatFolder(rawStorageId))
  }

  fun localToRemoteNotificationProfile(profile: NotificationProfile, rawStorageId: ByteArray?): REDNotificationProfileRecord {
    return REDNotificationProfileRecord.newBuilder(profile.storageServiceProto).apply {
      id = UuidUtil.toByteArray(profile.notificationProfileId.uuid).toByteString()
      name = profile.name
      emoji = profile.emoji
      color = profile.color.colorInt()
      createdAtMs = profile.createdAt
      allowAllCalls = profile.allowAllCalls
      allowAllMentions = profile.allowAllMentions
      allowedMembers = localToRemoteRecipients(profile.allowedMembers.toList())
      scheduleEnabled = profile.schedule.enabled
      scheduleStartTime = profile.schedule.start
      scheduleEndTime = profile.schedule.end
      scheduleDaysEnabled = localToRemoteDayOfWeek(profile.schedule.daysEnabled)
      deletedAtTimestampMs = profile.deletedTimestampMs
    }.build().toREDNotificationProfileRecord(StorageId.forNotificationProfile(rawStorageId))
  }

  fun localToRemoteStickerPack(pack: StickerPackSyncRecord, rawStorageId: ByteArray?): REDStickerPackRecord {
    return REDStickerPackRecord.newBuilder(pack.storageServiceProto).apply {
      packId = Hex.fromStringCondensed(pack.packId.value).toByteString()

      if (pack.deletedTimestampMs > 0) {
        packKey = ByteString.EMPTY
        position = 0
        deletedAtTimestamp = pack.deletedTimestampMs
      } else {
        packKey = Hex.fromStringCondensed(pack.packKey.value).toByteString()
        position = pack.position
        deletedAtTimestamp = 0
      }
    }.build().toREDStickerPackRecord(StorageId.forStickerPack(rawStorageId))
  }

  private fun localToRemoteDayOfWeek(daysEnabled: Set<DayOfWeek>): List<RemoteDayOfWeek> {
    return daysEnabled.map { day ->
      when (day) {
        DayOfWeek.MONDAY -> RemoteDayOfWeek.MONDAY
        DayOfWeek.TUESDAY -> RemoteDayOfWeek.TUESDAY
        DayOfWeek.WEDNESDAY -> RemoteDayOfWeek.WEDNESDAY
        DayOfWeek.THURSDAY -> RemoteDayOfWeek.THURSDAY
        DayOfWeek.FRIDAY -> RemoteDayOfWeek.FRIDAY
        DayOfWeek.SATURDAY -> RemoteDayOfWeek.SATURDAY
        DayOfWeek.SUNDAY -> RemoteDayOfWeek.SUNDAY
      }
    }
  }

  fun RemoteDayOfWeek.toLocal(): DayOfWeek {
    return when (this) {
      RemoteDayOfWeek.UNKNOWN -> DayOfWeek.MONDAY
      RemoteDayOfWeek.MONDAY -> DayOfWeek.MONDAY
      RemoteDayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
      RemoteDayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
      RemoteDayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
      RemoteDayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
      RemoteDayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
      RemoteDayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
    }
  }

  private fun localToRemoteChatFolderRecipients(threadIds: List<Long>): List<RemoteRecipient> {
    val recipientIds = REDDatabase.threads.getRecipientIdsForThreadIds(threadIds)
    return localToRemoteRecipients(recipientIds)
  }

  private fun localToRemoteRecipients(recipientIds: List<RecipientId>): List<RemoteRecipient> {
    return recipientIds.mapNotNull { id ->
      val recipient = REDDatabase.recipients.getRecordForSync(id)
      if (recipient == null) {
        Log.w(TAG, "Recipient $id from notification profile cannot be found")
        null
      } else {
        when (recipient.recipientType) {
          RecipientType.INDIVIDUAL -> {
            RemoteRecipient(
              contact = RemoteRecipient.Contact(
                serviceId = "",
                e164 = recipient.e164 ?: "",
                serviceIdBinary = recipient.serviceId?.toByteString() ?: ByteString.EMPTY
              )
            )
          }
          RecipientType.GV1 -> {
            RemoteRecipient(legacyGroupId = recipient.groupId!!.requireV1().decodedId.toByteString())
          }
          RecipientType.GV2 -> {
            RemoteRecipient(groupMasterKey = recipient.syncExtras.groupMasterKey!!.serialize().toByteString())
          }
          else -> null
        }
      }
    }
  }

  fun remoteToLocalRecipient(remoteRecipient: RemoteRecipient): Recipient? {
    return if (remoteRecipient.contact != null) {
      val serviceId = ServiceId.parseOrNull(remoteRecipient.contact!!.serviceId, remoteRecipient.contact!!.serviceIdBinary)
      val e164 = remoteRecipient.contact!!.e164
      Recipient.externalPush(REDServiceAddress(serviceId, e164))
    } else if (remoteRecipient.legacyGroupId != null) {
      try {
        Recipient.externalGroupExact(GroupId.v1(remoteRecipient.legacyGroupId!!.toByteArray()))
      } catch (e: BadGroupIdException) {
        Log.w(TAG, "Failed to parse groupV1 ID!", e)
        null
      }
    } else if (remoteRecipient.groupMasterKey != null) {
      try {
        Recipient.externalGroupExact(GroupId.v2(GroupMasterKey(remoteRecipient.groupMasterKey!!.toByteArray())))
      } catch (e: InvalidInputException) {
        Log.w(TAG, "Failed to parse groupV2 master key!", e)
        null
      }
    } else {
      Log.w(TAG, "Could not find recipient")
      null
    }
  }
}
