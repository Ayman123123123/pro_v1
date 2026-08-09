package com.red.sovereign.dependencies

import org.signal.core.util.logging.Log
import org.signal.libsignal.keytrans.KeyTransparencyException
import org.signal.libsignal.net.KeyTransparency
import org.signal.libsignal.net.KeyTransparency.CheckMode
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ServiceId
import com.red.sovereign.database.model.KeyTransparencyStore
import org.whispersystems.signalservice.api.websocket.REDWebSocket

/**
 * Operations used when interacting with [org.signal.libsignal.net.KeyTransparencyClient]
 */
class KeyTransparencyApi(private val unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket) {

  companion object {
    val TAG = Log.tag(KeyTransparencyApi::class.java)

    fun reset(aci: ServiceId.Aci, field: KeyTransparency.AccountDataField, keyTransparencyStore: KeyTransparencyStore) {
      try {
        KeyTransparency.resetField(aci, field, keyTransparencyStore)
      } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Unexpected result when trying to reset KT", e)
      }
    }
  }

  suspend fun check(checkMode: CheckMode, aci: ServiceId.Aci, aciIdentityKey: IdentityKey, e164: String?, unidentifiedAccessKey: ByteArray?, usernameHash: ByteArray?, keyTransparencyStore: KeyTransparencyStore): RequestResult<Unit, KeyTransparencyException> {
    return unauthWebSocket.runCatchingWithChatConnection { chatConnection ->
      chatConnection.keyTransparencyClient().check(checkMode, aci, aciIdentityKey, e164, unidentifiedAccessKey, usernameHash, keyTransparencyStore)
    }
  }
}
