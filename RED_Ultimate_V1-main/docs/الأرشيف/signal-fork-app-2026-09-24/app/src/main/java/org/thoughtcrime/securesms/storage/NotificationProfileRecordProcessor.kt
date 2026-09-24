package com.red.sovereign.storage

import org.signal.core.models.ServiceId
import org.signal.core.util.SqlUtil
import org.signal.core.util.UuidUtil
import org.signal.core.util.logging.Log
import com.red.sovereign.database.NotificationProfileTables
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.notifications.profiles.NotificationProfileId
import org.whispersystems.signalservice.api.storage.REDNotificationProfileRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.OptionalUtil.asOptional
import org.whispersystems.signalservice.internal.storage.protos.Recipient
import java.util.Optional
import java.util.UUID

/**
 * Record processor for [REDNotificationProfileRecord].
 * Handles merging and updating our local store when processing remote notification profile storage records.
 */
class NotificationProfileRecordProcessor : DefaultStorageRecordProcessor<REDNotificationProfileRecord>() {

  companion object {
    private val TAG = Log.tag(NotificationProfileRecordProcessor::class)
  }

  override fun compare(o1: REDNotificationProfileRecord, o2: REDNotificationProfileRecord): Int {
    return if (o1.proto.id == o2.proto.id) {
      0
    } else {
      1
    }
  }

  /**
   * Notification profiles must have a valid identifier
   * Notification profiles must have a name
   * All allowed members must have a valid serviceId
   */
  override fun isInvalid(remote: REDNotificationProfileRecord): Boolean {
    return UuidUtil.parseOrNull(remote.proto.id) == null ||
      remote.proto.name.isEmpty() ||
      containsInvalidServiceId(remote.proto.allowedMembers)
  }

  override fun getMatching(remote: REDNotificationProfileRecord, keyGenerator: StorageKeyGenerator): Optional<REDNotificationProfileRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val uuid: UUID = UuidUtil.parseOrThrow(remote.proto.id)
    val query = SqlUtil.buildQuery("${NotificationProfileTables.NotificationProfileTable.NOTIFICATION_PROFILE_ID} = ?", NotificationProfileId(uuid))

    val notificationProfile = REDDatabase.notificationProfiles.getProfile(query)

    return if (notificationProfile?.storageServiceId != null) {
      StorageSyncModels.localToRemoteNotificationProfile(notificationProfile, notificationProfile.storageServiceId.raw).asOptional()
    } else if (notificationProfile != null) {
      Log.d(TAG, "Notification profile was missing a storage service id, generating one")
      val storageId = StorageId.forNotificationProfile(keyGenerator.generate())
      REDDatabase.notificationProfiles.applyStorageIdUpdate(notificationProfile.notificationProfileId, storageId)
      StorageSyncModels.localToRemoteNotificationProfile(notificationProfile, storageId.raw).asOptional()
    } else {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      Optional.empty<REDNotificationProfileRecord>()
    }
  }

  /**
   * A deleted record takes precedence over a non-deleted record
   * while an earlier deletion takes precedence over a later deletion
   */
  override fun merge(remote: REDNotificationProfileRecord, local: REDNotificationProfileRecord, keyGenerator: StorageKeyGenerator): REDNotificationProfileRecord {
    val isRemoteDeleted = remote.proto.deletedAtTimestampMs > 0
    val isLocalDeleted = local.proto.deletedAtTimestampMs > 0

    return when {
      isRemoteDeleted && isLocalDeleted -> if (remote.proto.deletedAtTimestampMs <= local.proto.deletedAtTimestampMs) remote else local
      isRemoteDeleted -> remote
      isLocalDeleted -> local
      else -> remote
    }
  }

  override fun insertLocal(record: REDNotificationProfileRecord) {
    REDDatabase.notificationProfiles.insertNotificationProfileFromStorageSync(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<REDNotificationProfileRecord>) {
    REDDatabase.notificationProfiles.updateNotificationProfileFromStorageSync(update.new)
  }

  private fun containsInvalidServiceId(recipients: List<Recipient>): Boolean {
    return recipients.any { recipient ->
      recipient.contact != null && ServiceId.parseOrNull(recipient.contact!!.serviceId, recipient.contact!!.serviceIdBinary) == null
    }
  }
}
