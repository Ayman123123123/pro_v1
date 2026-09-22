package com.red.sovereign.messages.protocol

import org.signal.core.models.ServiceId
import org.signal.core.models.ServiceId.PNI
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore

/**
 * The entry point for creating and retrieving buffered protocol stores.
 * These stores will read from disk, but never write, instead buffering the results in memory.
 * You can then call [flushToDisk] in order to write the buffered results to disk.
 *
 * This allows you to efficiently do batches of work and avoid unnecessary intermediate writes.
 */
class BufferedProtocolStore private constructor(
  private val aciStore: Pair<ServiceId, BufferedREDServiceAccountDataStore>,
  private val pniStore: Pair<PNI, BufferedREDServiceAccountDataStore>
) {

  /** The PNI captured when this batch's store was created. Does not refresh if [REDStore.account.pni] later changes mid-batch. */
  val pni: PNI get() = pniStore.first

  fun get(serviceId: ServiceId): BufferedREDServiceAccountDataStore {
    return when (serviceId) {
      aciStore.first -> aciStore.second
      pniStore.first -> pniStore.second
      else -> error("No store matching serviceId $serviceId")
    }
  }

  fun getAciStore(): BufferedREDServiceAccountDataStore {
    return aciStore.second
  }

  /**
   * Writes any buffered data to disk. You can continue to use the same buffered store afterwards.
   */
  fun flushToDisk() {
    aciStore.second.flushToDisk(AppDependencies.protocolStore.aci())
    pniStore.second.flushToDisk(AppDependencies.protocolStore.pni())
  }

  companion object {
    fun create(): BufferedProtocolStore {
      val aci = REDStore.account.requireAci()
      val pni = REDStore.account.requirePni()

      return BufferedProtocolStore(
        aciStore = aci to BufferedREDServiceAccountDataStore(aci),
        pniStore = pni to BufferedREDServiceAccountDataStore(pni)
      )
    }
  }
}
