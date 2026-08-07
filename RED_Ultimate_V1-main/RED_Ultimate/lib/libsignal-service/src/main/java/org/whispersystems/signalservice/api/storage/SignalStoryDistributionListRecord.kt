package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.StoryDistributionListRecord
import java.io.IOException

data class REDStoryDistributionListRecord(
  override val id: StorageId,
  override val proto: StoryDistributionListRecord
) : REDRecord<StoryDistributionListRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): StoryDistributionListRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: StoryDistributionListRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): StoryDistributionListRecord.Builder {
      return try {
        StoryDistributionListRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        StoryDistributionListRecord.Builder()
      }
    }
  }
}
