package com.red.sovereign.storage

import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.StickerPackId
import org.whispersystems.signalservice.api.storage.REDStickerPackRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.OptionalUtil.asOptional
import java.util.Optional

/**
 * Record processor for [REDStickerPackRecord].
 * Handles merging and updating our local store when processing remote sticker pack storage records.
 */
class StickerPackRecordProcessor : DefaultStorageRecordProcessor<REDStickerPackRecord>() {

  companion object {
    private val TAG = Log.tag(StickerPackRecordProcessor::class)

    private const val PACK_ID_LENGTH = 16
    private const val PACK_KEY_LENGTH = 32
  }

  override fun compare(o1: REDStickerPackRecord, o2: REDStickerPackRecord): Int {
    return if (o1.proto.packId == o2.proto.packId) {
      0
    } else {
      1
    }
  }

  /**
   * Sticker packs must have a 16-byte pack id.
   * Packs that are not deleted must have a 32-byte pack key.
   */
  override fun isInvalid(remote: REDStickerPackRecord): Boolean {
    return remote.proto.packId.size != PACK_ID_LENGTH ||
      (remote.proto.deletedAtTimestamp == 0L && remote.proto.packKey.size != PACK_KEY_LENGTH)
  }

  override fun getMatching(remote: REDStickerPackRecord, keyGenerator: StorageKeyGenerator): Optional<REDStickerPackRecord> {
    val packId = StickerPackId(Hex.toStringCondensed(remote.proto.packId.toByteArray()))
    val local = REDDatabase.stickers.getPackForStorageSync(packId)

    return if (local == null || (!local.installed && local.deletedTimestampMs == 0L)) {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      Optional.empty<REDStickerPackRecord>()
    } else if (local.storageServiceId != null) {
      StorageSyncModels.localToRemoteStickerPack(local, local.storageServiceId.raw).asOptional()
    } else {
      Log.d(TAG, "Pack was missing a storage service id, generating one")
      val storageId = StorageId.forStickerPack(keyGenerator.generate())
      REDDatabase.stickers.applyStorageIdUpdate(packId, storageId)
      StorageSyncModels.localToRemoteStickerPack(local, storageId.raw).asOptional()
    }
  }

  /**
   * Note that we don't allow deleted packs to "always win" like we do in other records, because sticker packs can be reinstalled.
   */
  override fun merge(remote: REDStickerPackRecord, local: REDStickerPackRecord, keyGenerator: StorageKeyGenerator): REDStickerPackRecord {
    val isRemoteDeleted = remote.proto.deletedAtTimestamp > 0
    val isLocalDeleted = local.proto.deletedAtTimestamp > 0

    return if (isRemoteDeleted && isLocalDeleted && local.proto.deletedAtTimestamp < remote.proto.deletedAtTimestamp) {
      local
    } else {
      remote
    }
  }

  override fun insertLocal(record: REDStickerPackRecord) {
    REDDatabase.stickers.insertStickerPackFromStorageSync(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<REDStickerPackRecord>) {
    REDDatabase.stickers.updateStickerPackFromStorageSync(update.new)
  }
}
