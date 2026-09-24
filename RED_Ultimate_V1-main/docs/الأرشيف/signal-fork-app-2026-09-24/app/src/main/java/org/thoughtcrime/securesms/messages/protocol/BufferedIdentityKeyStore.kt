package com.red.sovereign.messages.protocol

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.REDProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.IdentityKeyStore.IdentityChange
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.REDStore
import org.whispersystems.signalservice.api.REDServiceAccountDataStore

/**
 * An in-memory identity key store that is intended to be used temporarily while decrypting messages.
 */
class BufferedIdentityKeyStore(
  private val selfIdentityKeyPair: IdentityKeyPair,
  private val selfRegistrationId: Int
) : IdentityKeyStore {

  private val store: MutableMap<REDProtocolAddress, IdentityKey> = HashMap()

  /** All of the keys that have been created or updated during operation. */
  private val updatedKeys: MutableMap<REDProtocolAddress, IdentityKey> = mutableMapOf()

  override fun getIdentityKeyPair(): IdentityKeyPair {
    return selfIdentityKeyPair
  }

  override fun getLocalRegistrationId(): Int {
    return selfRegistrationId
  }

  override fun saveIdentity(address: REDProtocolAddress, identityKey: IdentityKey): IdentityChange {
    val existing: IdentityKey? = getIdentity(address)

    store[address] = identityKey

    return if (identityKey != existing) {
      updatedKeys[address] = identityKey
      IdentityChange.REPLACED_EXISTING
    } else {
      IdentityChange.NEW_OR_UNCHANGED
    }
  }

  override fun isTrustedIdentity(address: REDProtocolAddress, identityKey: IdentityKey, direction: IdentityKeyStore.Direction): Boolean {
    val isSelf = address.name == REDStore.account.aci?.toString() ||
      address.name == REDStore.account.pni?.toString() ||
      address.name == REDStore.account.e164

    if (isSelf) {
      return identityKey == REDStore.account.aciIdentityKey.publicKey
    }

    return when (direction) {
      IdentityKeyStore.Direction.RECEIVING -> true
      IdentityKeyStore.Direction.SENDING -> error("Should not happen during the intended usage pattern of this class")
      else -> error("Unknown direction: $direction")
    }
  }

  override fun getIdentity(address: REDProtocolAddress): IdentityKey? {
    val cached = store[address]

    return if (cached != null) {
      cached
    } else {
      val fromDatabase = REDDatabase.identities.getIdentityStoreRecord(address.name)
      if (fromDatabase != null) {
        store[address] = fromDatabase.identityKey
      }

      fromDatabase?.identityKey
    }
  }

  fun flushToDisk(persistentStore: REDServiceAccountDataStore) {
    for ((address, identityKey) in updatedKeys) {
      persistentStore.saveIdentity(address, identityKey)
    }

    updatedKeys.clear()
  }
}
