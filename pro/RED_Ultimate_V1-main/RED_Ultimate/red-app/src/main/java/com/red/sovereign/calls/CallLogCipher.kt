package com.red.sovereign.calls

import com.red.sovereign.crypto.ProtocolRecordCipher

/**
 * Encrypts/decrypts the peerId and peerLabel in call log entries at rest.
 * Call logs are sensitive: they reveal who the user talks to, when, and for how long.
 * Without encryption, a stolen device with the DB extract would expose the entire social graph.
 */
class CallLogCipher {
    private val cipher = ProtocolRecordCipher()

    fun encryptPeerId(redId: String): String {
        if (redId.isBlank()) return ""
        return cipher.encrypt(redId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun decryptPeerId(hex: String): String {
        if (hex.isBlank()) return ""
        return runCatching {
            val bytes = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            String(cipher.decrypt(bytes), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun encryptLabel(label: String): String = encryptPeerId(label)
    fun decryptLabel(hex: String): String = decryptPeerId(hex)
}
