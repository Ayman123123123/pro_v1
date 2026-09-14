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
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val name: String = "",
    val address: String = ""
)

@Serializable
data class ContactData(
    val name: String,
    val phoneNumber: String = "",
    val redId: String = "",
    val avatarUrl: String = ""
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
    val forwardCount: Int = 0, // عدد مرات التحويل (>5 = كثيرة التحويل)
    val expiresAt: Long? = null,
    val mentions: List<String> = emptyList(), // RED IDs mentioned via @
    val hashtags: List<String> = emptyList(), // # tags
    val disappearingMs: Long? = null, // 0=off, 3600000=1h, 86400000=24h, 604800000=7d, 7776000000=90d
    val poll: InlinePoll? = null, // استطلاع مضمّن داخل الرسالة (مشفر)
    // تفاعلات الإيموجي على رسالة (E2EE ضمن الحوار/المجموعة — لا يرى الخادم الإيموجي)
    val reactionOf: String? = null, // id الرسالة المُتفاعل معها
    val emoji: String? = null, // الإيموجي المُتفاعل به (للإضافة)؛ null مع REACTION_REMOVE = إزالة تفاعل
    // تصويت استطلاع المجموعة (E2EE — كل تصويت رسالة غنية تصل للأعضاء)
    val pollVoteOf: String? = null, // pollId
    val pollVoteOption: Int? = null, // فهرس الخيار المُصوَّت عليه (أو null لإلغاء التصويت)
    // عرض مرة واحدة — وسائط/نص يُحذف بعد أول فتح (واتساب View-Once)
    val viewOnce: Boolean = false,
    // تم استهلاك رسالة العرض-مرة-واحدة (يُضبط محلياً عند الفتح؛ لا يُرسل)
    val viewOnceConsumed: Boolean = false,
    // موقع جغرافي & جهة اتصال
    val location: LocationData? = null,
    val contact: ContactData? = null,
    // رسالة مثبتة
    val isPinned: Boolean = false,
    // P1-A: موضوع المحادثة (Thread topic). اختياري للتوافق الخلفي:
    // ignoreUnknownKeys=true فيسمح للعملاء القدامى بتجاهله، وexplicitNulls=false
    // فيمنع إرسال null على السلك. البديل المدعوم دائمًا هو hashtag #topic
    // داخل النص (يُستخرج محليًا عبر TOPIC_HASHTAG_REGEX).
    val topicId: String? = null,
    // مكالمة جماعية (CALL_STARTED): الانضمام المتأخر من رسالة النظام — null = لا مكالمة.
    val callId: String? = null,
    val callIsVideo: Boolean = false
) {
    /** الموضوع الفعّال: topicId أولًا، ثم أول hashtag كبديل (بدون #). */
    fun effectiveTopic(): String? {
        topicId?.trim()?.removePrefix("#")?.takeIf { it.isNotBlank() }?.let { return it }
        hashtags.firstOrNull()?.trim()?.removePrefix("#")?.takeIf { it.isNotBlank() }?.let { return it }
        // بديل أخير: استخراج #topic من النص نفسه
        return TOPIC_HASHTAG_REGEX.find(text)?.value?.removePrefix("#")
    }
    init {
        require(action in setOf("MESSAGE", "EDIT", "DELETE", "STORY_REPLY", "REACTION", "REACTION_REMOVE", "POLL_VOTE", "CALL_STARTED", "LOCATION", "CONTACT", "PIN")) { "Unknown action: $action" }
        require(text.length <= 65_536)
        require(mentions.size <= 20) { "Too many mentions" }
        require(hashtags.size <= 10) { "Too many hashtags" }
        require(topicId == null || topicId.length in 1..64) { "Invalid topicId length" }
        require(disappearingMs == null || disappearingMs in setOf(0L, 3600000L, 86400000L, 604800000L, 7776000000L))
        // التحقق من صحة حمولة تفاعل الإيموجي
        require(emoji == null || emoji.length in 1..16) { "Invalid emoji length" }
        require(
            (action == "REACTION" && reactionOf != null && emoji != null) ||
            (action == "REACTION_REMOVE" && reactionOf != null) ||
            action !in setOf("REACTION", "REACTION_REMOVE")
        ) { "Invalid reaction payload" }
        require(
            (action == "POLL_VOTE" && pollVoteOf != null && (pollVoteOption == null || pollVoteOption in 0..50)) ||
            action != "POLL_VOTE"
        ) { "Invalid poll vote payload" }
    }

    companion object {
        /** P1-A: نفس سياسة الهاشتاغ المعتمدة في التطبيق (عربي + لاتيني، 2..30). */
        val TOPIC_HASHTAG_REGEX = Regex("#[\\w\u0600-\u06FF\\-]{2,30}")
        /** تطبيع اسم موضوع جديد إلى slug صالح: مسافات←'-'، إزالة #، قص 30. */
        fun normalizeTopicName(raw: String): String =
            raw.trim().removePrefix("#").trim()
                .replace(Regex("\\s+"), "-")
                .replace(Regex("[^\\w\u0600-\u06FF\\-]"), "")
                .take(30)
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        fun encode(value: RichMessage) = json.encodeToString(serializer(), value).toByteArray(Charsets.UTF_8)
        fun decode(value: ByteArray) = runCatching { json.decodeFromString(serializer(), value.toString(Charsets.UTF_8)) }.getOrNull()
    }
}
