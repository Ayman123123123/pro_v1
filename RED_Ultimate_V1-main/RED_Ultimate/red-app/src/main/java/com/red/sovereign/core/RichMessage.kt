package com.red.sovereign.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class InlinePoll(
    val question: String,
    val options: List<String>,
    val pollId: String = "",
    val isClosed: Boolean = false,
    val votes: List<Int> = emptyList()
)

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
    val disappearingMs: Long? = null, // 0=off, 3600000=1h, 86400000=24h, 604800000=7d
    val poll: InlinePoll? = null, // استطلاع مضمّن داخل الرسالة (مشفر)
    // تفاعلات الإيموجي على رسالة (E2EE ضمن الحوار/المجموعة — لا يرى الخادم الإيموجي)
    val reactionOf: String? = null, // id الرسالة المُتفاعل معها
    val emoji: String? = null // الإيموجي المُتفاعل به (للإضافة)؛ null مع REACTION_REMOVE = إزالة تفاعل
) {
    init {
        require(action in setOf("MESSAGE", "EDIT", "DELETE", "STORY_REPLY", "REACTION", "REACTION_REMOVE")) { "Unknown action: $action" }
        require(text.length <= 65_536)
        require(mentions.size <= 20) { "Too many mentions" }
        require(hashtags.size <= 10) { "Too many hashtags" }
        require(disappearingMs == null || disappearingMs in setOf(0L, 3600000L, 86400000L, 604800000L))
        // التحقق من صحة حمولة تفاعل الإيموجي
        require(emoji == null || emoji.length in 1..16) { "Invalid emoji length" }
        require(
            (action == "REACTION" && reactionOf != null && emoji != null) ||
            (action == "REACTION_REMOVE" && reactionOf != null) ||
            action !in setOf("REACTION", "REACTION_REMOVE")
        ) { "Invalid reaction payload" }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        fun encode(value: RichMessage) = json.encodeToString(serializer(), value).toByteArray(Charsets.UTF_8)
        fun decode(value: ByteArray) = runCatching { json.decodeFromString(serializer(), value.toString(Charsets.UTF_8)) }.getOrNull()
    }
}
