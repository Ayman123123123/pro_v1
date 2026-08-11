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
    val expiresAt: Long? = null,
    val mentions: List<String> = emptyList(), // RED IDs mentioned via @
    val hashtags: List<String> = emptyList(), // # tags
    val disappearingMs: Long? = null // 0=off, 3600000=1h, 86400000=24h, 604800000=7d
) {
    init {
        require(action in setOf("MESSAGE", "EDIT", "DELETE", "STORY_REPLY"))
        require(text.length <= 65_536)
        require(mentions.size <= 20) { "Too many mentions" }
        require(hashtags.size <= 10) { "Too many hashtags" }
        require(disappearingMs == null || disappearingMs in setOf(0L, 3600000L, 86400000L, 604800000L))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        fun encode(value: RichMessage) = json.encodeToString(serializer(), value).toByteArray(Charsets.UTF_8)
        fun decode(value: ByteArray) = runCatching { json.decodeFromString(serializer(), value.toString(Charsets.UTF_8)) }.getOrNull()
    }
}
