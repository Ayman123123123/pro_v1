package com.red.sovereign.storage

import org.whispersystems.signalservice.api.storage.REDRecord

/**
 * Represents a pair of records: one old, and one new. The new record should replace the old.
 */
class StorageRecordUpdate<E : REDRecord<*>>(val old: E, val new: E) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as StorageRecordUpdate<*>

    if (old != other.old) return false
    if (new != other.new) return false

    return true
  }

  override fun hashCode(): Int {
    var result = old.hashCode()
    result = 31 * result + new.hashCode()
    return result
  }
}
