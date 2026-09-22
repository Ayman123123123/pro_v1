package com.red.sovereign.messages.protocol

import org.signal.core.models.ServiceId
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.REDProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.util.RemoteConfig
import org.whispersystems.signalservice.api.REDServiceAccountDataStore
import org.whispersystems.signalservice.api.REDServiceSessionStore
import kotlin.jvm.Throws

/**
 * An in-memory session store that is intended to be used temporarily while decrypting messages.
 */
class BufferedSessionStore(private val selfServiceId: ServiceId) : REDServiceSessionStore {

  private val store: MutableMap<REDProtocolAddress, SessionRecord> = HashMap()

  /** All of the sessions that have been created or updated during operation. */
  private val updatedSessions: MutableMap<REDProtocolAddress, SessionRecord> = mutableMapOf()

  /** All of the sessions that have deleted during operation. */
  private val deletedSessions: MutableSet<REDProtocolAddress> = mutableSetOf()

  override fun loadSession(address: REDProtocolAddress): SessionRecord {
    val session: SessionRecord = store[address]
      ?: REDDatabase.sessions.load(selfServiceId, address)
      ?: SessionRecord()

    store[address] = session

    return session
  }

  @Throws(NoSessionException::class)
  override fun loadExistingSessions(addresses: MutableList<REDProtocolAddress>): List<SessionRecord> {
    val found: MutableList<SessionRecord?> = ArrayList(addresses.size)
    val needsDatabaseLookup: MutableList<Pair<Int, REDProtocolAddress>> = mutableListOf()

    addresses.forEachIndexed { index, address ->
      val cached: SessionRecord? = store[address]

      if (cached != null) {
        found[index] = cached
      } else {
        needsDatabaseLookup += (index to address)
      }
    }

    if (needsDatabaseLookup.isNotEmpty()) {
      val databaseRecords: List<SessionRecord?> = REDDatabase.sessions.load(selfServiceId, needsDatabaseLookup.map { (_, address) -> address })
      needsDatabaseLookup.forEachIndexed { databaseLookupIndex, (addressIndex, _) ->
        found[addressIndex] = databaseRecords[databaseLookupIndex]
      }
    }

    val cachedAndLoaded = found.filterNotNull()

    if (cachedAndLoaded.size != addresses.size) {
      throw NoSessionException("Failed to find one or more sessions.")
    }

    return cachedAndLoaded
  }

  override fun storeSession(address: REDProtocolAddress, record: SessionRecord) {
    store[address] = record
    updatedSessions[address] = record
  }

  override fun containsSession(address: REDProtocolAddress): Boolean {
    return if (store.containsKey(address)) {
      true
    } else {
      val fromDatabase: SessionRecord? = REDDatabase.sessions.load(selfServiceId, address)

      if (fromDatabase != null) {
        store[address] = fromDatabase
        return fromDatabase.hasSenderChain(RemoteConfig.requirePqRatio)
      } else {
        false
      }
    }
  }

  override fun deleteSession(address: REDProtocolAddress) {
    store.remove(address)
    deletedSessions += address
  }

  override fun getSubDeviceSessions(name: String): MutableList<Int> {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun deleteAllSessions(name: String) {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun archiveSession(address: REDProtocolAddress?) {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun getAllAddressesWithActiveSessions(addressNames: MutableList<String>): Map<REDProtocolAddress, SessionRecord> {
    error("Should not happen during the intended usage pattern of this class")
  }

  fun flushToDisk(persistentStore: REDServiceAccountDataStore) {
    for ((address, record) in updatedSessions) {
      persistentStore.storeSession(address, record)
    }

    for (address in deletedSessions) {
      persistentStore.deleteSession(address)
    }

    updatedSessions.clear()
    deletedSessions.clear()
  }
}
