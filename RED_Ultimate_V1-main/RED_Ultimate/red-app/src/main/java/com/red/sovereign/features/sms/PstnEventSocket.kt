package com.red.sovereign.features.sms

import com.red.sovereign.auth.TokenStore

class PstnEventSocket(
    private val tokens: TokenStore,
    private val onEnvelope: (PstnWsEnvelope) -> Unit = {},
    private val onState: (Boolean) -> Unit = {}
) {
    fun connect() {}
    fun disconnect() {}
    fun isActive(): Boolean = false
    fun sendControl(type: String, data: Map<String, String?>): Boolean = false
    fun shutdown() {}
}
