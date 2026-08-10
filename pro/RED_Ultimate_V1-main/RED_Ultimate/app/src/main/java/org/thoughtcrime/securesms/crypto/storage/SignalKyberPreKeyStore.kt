/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.crypto.storage

import org.signal.core.models.ServiceId
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import com.red.sovereign.crypto.ReentrantSessionLock
import com.red.sovereign.database.REDDatabase
import org.whispersystems.signalservice.api.REDServiceKyberPreKeyStore
import kotlin.jvm.Throws

/**
 * An implementation of the [KyberPreKeyStore] that stores entries in [com.red.sovereign.database.KyberPreKeyTable].
 */
class REDKyberPreKeyStore(private val selfServiceId: ServiceId) : REDServiceKyberPreKeyStore {

  @Throws(InvalidKeyIdException::class)
  override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return REDDatabase.kyberPreKeys.get(selfServiceId, kyberPreKeyId)?.record ?: throw InvalidKeyIdException("Missing kyber prekey with ID: $kyberPreKeyId")
    }
  }

  override fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return REDDatabase.kyberPreKeys.getAll(selfServiceId).map { it.record }
    }
  }

  override fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord> {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return REDDatabase.kyberPreKeys.getAllLastResort(selfServiceId).map { it.record }
    }
  }

  override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return REDDatabase.kyberPreKeys.insert(selfServiceId, kyberPreKeyId, record, false)
    }
  }

  override fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return REDDatabase.kyberPreKeys.insert(selfServiceId, kyberPreKeyId, kyberPreKeyRecord, true)
    }
  }

  override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
    ReentrantSessionLock.INSTANCE.acquire().use {
      return REDDatabase.kyberPreKeys.contains(selfServiceId, kyberPreKeyId)
    }
  }

  override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      REDDatabase.kyberPreKeys.handleMarkKyberPreKeyUsed(selfServiceId, kyberPreKeyId, signedPreKeyId, baseKey)
    }
  }

  override fun removeKyberPreKey(kyberPreKeyId: Int) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      REDDatabase.kyberPreKeys.delete(selfServiceId, kyberPreKeyId)
    }
  }

  override fun markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime: Long) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      REDDatabase.kyberPreKeys.markAllStaleIfNecessary(selfServiceId, staleTime)
    }
  }

  override fun deleteAllStaleOneTimeKyberPreKeys(threshold: Long, minCount: Int) {
    ReentrantSessionLock.INSTANCE.acquire().use {
      REDDatabase.kyberPreKeys.deleteAllStaleBefore(selfServiceId, threshold, minCount)
    }
  }
}
