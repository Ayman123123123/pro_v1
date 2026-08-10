package com.red.sovereign.storage

import org.signal.core.util.isNotEmpty
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import com.red.sovereign.database.GroupTable
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.RecipientRecord
import com.red.sovereign.groups.GroupId
import com.red.sovereign.keyvalue.REDStore
import org.whispersystems.signalservice.api.storage.REDGroupV2Record
import org.whispersystems.signalservice.api.storage.REDStorageRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.toREDGroupV2Record
import java.util.Optional

/**
 * Record processor for [REDGroupV2Record].
 * Handles merging and updating our local store when processing remote gv2 storage records.
 */
class GroupV2RecordProcessor(private val recipientTable: RecipientTable, private val groupDatabase: GroupTable) : DefaultStorageRecordProcessor<REDGroupV2Record>() {
  companion object {
    private val TAG = Log.tag(GroupV2RecordProcessor::class.java)
  }

  constructor() : this(REDDatabase.recipients, REDDatabase.groups)

  override fun isInvalid(remote: REDGroupV2Record): Boolean {
    return remote.proto.masterKey.size != GroupMasterKey.SIZE
  }

  override fun getMatching(remote: REDGroupV2Record, keyGenerator: StorageKeyGenerator): Optional<REDGroupV2Record> {
    val groupId = GroupId.v2(GroupMasterKey(remote.proto.masterKey.toByteArray()))

    val recipientId = recipientTable.getByGroupId(groupId)

    return recipientId
      .map { recipientTable.getRecordForSync(it)!! }
      .map { settings: RecipientRecord ->
        if (settings.syncExtras.groupMasterKey != null) {
          StorageSyncModels.localToRemoteRecord(settings)
        } else {
          Log.w(TAG, "No local master key. Assuming it matches remote since the groupIds match. Enqueuing a fetch to fix the bad state.")
          groupDatabase.fixMissingMasterKey(GroupMasterKey(remote.proto.masterKey.toByteArray()))
          StorageSyncModels.localToRemoteRecord(settings, GroupMasterKey(remote.proto.masterKey.toByteArray()))
        }
      }
      .map { record: REDStorageRecord -> record.proto.groupV2!!.toREDGroupV2Record(record.id) }
  }

  override fun merge(remote: REDGroupV2Record, local: REDGroupV2Record, keyGenerator: StorageKeyGenerator): REDGroupV2Record {
    val merged = REDGroupV2Record.newBuilder(remote.serializedUnknowns).apply {
      masterKey = remote.proto.masterKey
      blocked = remote.proto.blocked
      whitelisted = remote.proto.whitelisted
      archived = remote.proto.archived
      markedUnread = remote.proto.markedUnread
      mutedUntilTimestamp = remote.proto.mutedUntilTimestamp
      dontNotifyForMentionsIfMuted = remote.proto.dontNotifyForMentionsIfMuted
      hideStory = remote.proto.hideStory
      storySendMode = remote.proto.storySendMode
      avatarColor = if (REDStore.account.isPrimaryDevice) local.proto.avatarColor else remote.proto.avatarColor
      verifiedNameHash = if (remote.proto.verifiedNameHash.isNotEmpty()) remote.proto.verifiedNameHash else local.proto.verifiedNameHash
    }.build().toREDGroupV2Record(StorageId.forGroupV2(keyGenerator.generate()))

    val matchesRemote = doParamsMatch(remote, merged)
    val matchesLocal = doParamsMatch(local, merged)

    return if (matchesRemote) {
      remote
    } else if (matchesLocal) {
      local
    } else {
      merged
    }
  }

  override fun insertLocal(record: REDGroupV2Record) {
    recipientTable.applyStorageSyncGroupV2Insert(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<REDGroupV2Record>) {
    recipientTable.applyStorageSyncGroupV2Update(update)
  }

  override fun compare(lhs: REDGroupV2Record, rhs: REDGroupV2Record): Int {
    return if (lhs.proto.masterKey == rhs.proto.masterKey) {
      0
    } else {
      1
    }
  }
}
