package com.red.sovereign.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RichMessage(
    val version: Int = 1,
    val action: String = "MESSAGE",
    val text: String = "",
    val replyTo: String? = null,
    val editOf: String? = null,
    val deleteOf: String? = null,
    val forwardOf: String? = null,
    val expiresAt: Long? = null
) {
    init {
        require(action in setOf("MESSAGE", "EDIT", "DELETE", "STORY_REPLY"))
        require(text.length <= 65_536)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        fun encode(value: RichMessage) = json.encodeToString(serializer(), value).toByteArray(Charsets.UTF_8)
        fun decode(value: ByteArray) = runCatching { json.decodeFromString(serializer(), value.toString(Charsets.UTF_8)) }.getOrNull()
    }
}
