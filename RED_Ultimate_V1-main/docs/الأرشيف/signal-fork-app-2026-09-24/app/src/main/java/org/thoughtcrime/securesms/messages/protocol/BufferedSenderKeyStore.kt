package com.red.sovereign.messages.protocol

import org.signal.libsignal.protocol.REDProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import com.red.sovereign.database.REDDatabase
import org.whispersystems.signalservice.api.REDServiceAccountDataStore
import org.whispersystems.signalservice.api.REDServiceSenderKeyStore
import org.whispersystems.signalservice.api.push.DistributionId
import java.util.UUID

/**
 * An in-memory sender key store that is intended to be used temporarily while decrypting messages.
 */
class BufferedSenderKeyStore : REDServiceSenderKeyStore {

  private val store: MutableMap<StoreKey, SenderKeyRecord> = HashMap()

  /** All of the keys that have been created or updated during operation. */
  private val updatedKeys: MutableMap<StoreKey, SenderKeyRecord> = mutableMapOf()

  /** All of the distributionId's whose sharing has been cleared during operation. */
  private val clearSharedWith: MutableSet<REDProtocolAddress> = mutableSetOf()

  override fun storeSenderKey(sender: REDProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
    val key = StoreKey(sender, distributionId)
    store[key] = record
    updatedKeys[key] = record
  }

  override fun loadSenderKey(sender: REDProtocolAddress, distributionId: UUID): SenderKeyRecord? {
    val cached: SenderKeyRecord? = store[StoreKey(sender, distributionId)]

    return if (cached != null) {
      cached
    } else {
      val fromDatabase: SenderKeyRecord? = REDDatabase.senderKeys.load(sender, distributionId.toDistributionId())

      if (fromDatabase != null) {
        store[StoreKey(sender, distributionId)] = fromDatabase
      }

      return fromDatabase
    }
  }

  override fun clearSenderKeySharedWith(addresses: MutableCollection<REDProtocolAddress>) {
    clearSharedWith.addAll(addresses)
  }

  override fun getSenderKeySharedWith(distributionId: DistributionId?): MutableSet<REDProtocolAddress> {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun markSenderKeySharedWith(distributionId: DistributionId?, addresses: MutableCollection<REDProtocolAddress>?) {
    error("Should not happen during the intended usage pattern of this class")
  }

  fun flushToDisk(persistentStore: REDServiceAccountDataStore) {
    for ((key, record) in updatedKeys) {
      persistentStore.storeSenderKey(key.address, key.distributionId, record)
    }

    if (clearSharedWith.isNotEmpty()) {
      persistentStore.clearSenderKeySharedWith(clearSharedWith)
      clearSharedWith.clear()
    }

    updatedKeys.clear()
  }

  private fun UUID.toDistributionId() = DistributionId.from(this)

  data class StoreKey(
    val address: REDProtocolAddress,
    val distributionId: UUID
  )
}
