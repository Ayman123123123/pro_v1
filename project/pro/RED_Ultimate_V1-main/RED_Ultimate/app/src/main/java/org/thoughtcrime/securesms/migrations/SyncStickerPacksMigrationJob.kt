package com.red.sovereign.migrations

import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.StickerTables
import com.red.sovereign.database.model.StickerPackId
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.storage.StorageSyncHelper

/**
 * Marks all installed sticker packs as needing to be synced for storage service.
 */
internal class SyncStickerPacksMigrationJob(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {
  companion object {
    const val KEY = "SyncStickerPacksMigrationJob"

    private val TAG = Log.tag(SyncStickerPacksMigrationJob::class)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (REDStore.account.aci == null) {
      Log.w(TAG, "Self not available yet.")
      return
    }

    val packIds = REDDatabase.stickers.getInstalledPackIds()

    REDDatabase.stickers.markNeedsSync(packIds)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  private fun StickerTables.getInstalledPackIds(): List<StickerPackId> {
    return readableDatabase
      .select(StickerTables.Pack.PACK_ID)
      .from(StickerTables.Pack.TABLE_NAME)
      .where("${StickerTables.Pack.INSTALLED} = 1")
      .run()
      .readToList { cursor -> StickerPackId(cursor.requireNonNullString(StickerTables.Pack.PACK_ID)) }
  }

  class Factory : Job.Factory<SyncStickerPacksMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SyncStickerPacksMigrationJob {
      return SyncStickerPacksMigrationJob(parameters)
    }
  }
}
