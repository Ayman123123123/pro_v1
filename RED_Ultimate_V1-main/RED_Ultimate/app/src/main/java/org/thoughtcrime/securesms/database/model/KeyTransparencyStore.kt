package com.red.sovereign.database.model

import org.signal.core.util.logging.Log.tag
import org.signal.libsignal.keytrans.Store
import org.signal.libsignal.protocol.ServiceId
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.REDStore
import java.util.Optional

/**
 * Store used by [org.signal.libsignal.net.KeyTransparencyClient] during key transparency
 */
data object KeyTransparencyStore : Store {

  private val TAG: String = tag(KeyTransparencyStore::class.java)

  override fun getLastDistinguishedTreeHead(): Optional<ByteArray> {
    return Optional.ofNullable(REDStore.account.distinguishedHead)
  }

  override fun setLastDistinguishedTreeHead(lastDistinguishedTreeHead: ByteArray) {
    REDStore.account.distinguishedHead = lastDistinguishedTreeHead
  }

  override fun getAccountData(libsignalAci: ServiceId.Aci): Optional<ByteArray> {
    val aci = org.signal.core.models.ServiceId.ACI.fromLibRED(libsignalAci)
    return Optional.ofNullable(REDDatabase.recipients.getKeyTransparencyData(aci))
  }

  override fun setAccountData(libsignalAci: ServiceId.Aci, data: ByteArray) {
    val aci = org.signal.core.models.ServiceId.ACI.fromLibRED(libsignalAci)
    REDDatabase.recipients.setKeyTransparencyData(aci, data)
  }
}
