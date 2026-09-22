package com.red.sovereign.migrations

import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobs.StickerPackDownloadJob
import com.red.sovereign.stickers.BlessedPacks

/**
 * Installs the second wave of blessed sticker packs. Some are installed by default, others as references.
 */
internal class StickerPackAddition2MigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {
    const val KEY = "StickerPackAddition2MigrationJob"
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    val jobManager = AppDependencies.jobManager

    jobManager.add(StickerPackDownloadJob.forInstall(BlessedPacks.ROCKY_TALK.packId, BlessedPacks.ROCKY_TALK.packKey, false))
    jobManager.add(StickerPackDownloadJob.forInstall(BlessedPacks.CROCOS_FEELINGS.packId, BlessedPacks.CROCOS_FEELINGS.packKey, false))

    jobManager.add(StickerPackDownloadJob.forReference(BlessedPacks.MY_DAILY_LIFE_2.packId, BlessedPacks.MY_DAILY_LIFE_2.packKey))
    jobManager.add(StickerPackDownloadJob.forReference(BlessedPacks.COZY_SEASON.packId, BlessedPacks.COZY_SEASON.packKey))
    jobManager.add(StickerPackDownloadJob.forReference(BlessedPacks.CHUG_THE_MOUSE.packId, BlessedPacks.CHUG_THE_MOUSE.packKey))
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<StickerPackAddition2MigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): StickerPackAddition2MigrationJob {
      return StickerPackAddition2MigrationJob(parameters)
    }
  }
}
