package com.red.sovereign.core.database

import androidx.room.TypeConverter

/**
 * Enums مخزّنة كنص (TEXT) — نفس القيم المستعملة حاليًا في الأعمدة
 * السلسلية، فلا تغيير في السكيما ولا Migration.
 *
 * مسجّلة عبر ‎@TypeConverters(RedTypeConverters::class)‎ على مستوى
 * الـ DAO (RedDao/OutboxDao/MediaUploadDao) حتى لا نلمس RedDatabase.kt.
 */
enum class MessageStatus(val value: String) {
    SENDING("SENDING"),
    SENT("SENT"),
    DELIVERED("DELIVERED"),
    READ("READ"),
    FAILED("FAILED");

    companion object {
        fun from(v: String?): MessageStatus =
            entries.firstOrNull { it.value == v } ?: SENT
    }
}

enum class OutboxStatus(val value: String) {
    PENDING("PENDING"),
    SENDING("SENDING"),
    SENT("SENT"),
    FAILED("FAILED"),
    DEAD_LETTER("DEAD_LETTER");

    companion object {
        fun from(v: String?): OutboxStatus =
            entries.firstOrNull { it.value == v } ?: PENDING
    }
}

enum class CallDirection(val value: String) {
    INCOMING("INCOMING"),
    OUTGOING("OUTGOING");

    companion object {
        fun from(v: String?): CallDirection =
            entries.firstOrNull { it.value == v } ?: INCOMING
    }
}

enum class CallStatus(val value: String) {
    COMPLETED("COMPLETED"),
    MISSED("MISSED"),
    REJECTED("REJECTED"),
    ACTIVE("ACTIVE"),
    ENDED("ENDED"),
    FAILED("FAILED");

    companion object {
        fun from(v: String?): CallStatus =
            entries.firstOrNull { it.value == v } ?: COMPLETED
    }
}

enum class ChatMessageType(val value: String) {
    TEXT("TEXT"),
    RICH_TEXT("RICH_TEXT"),
    IMAGE("IMAGE"),
    VIDEO("VIDEO"),
    FILE("FILE"),
    AUDIO("AUDIO"),
    VOICE("VOICE"),
    STICKER("STICKER"),
    GROUP_MESSAGE("GROUP_MESSAGE");

    companion object {
        fun from(v: String?): ChatMessageType =
            entries.firstOrNull { it.value == v } ?: TEXT
    }
}

class RedTypeConverters {
    @TypeConverter fun fromMessageStatus(s: MessageStatus): String = s.value
    @TypeConverter fun toMessageStatus(v: String?): MessageStatus = MessageStatus.from(v)

    @TypeConverter fun fromOutboxStatus(s: OutboxStatus): String = s.value
    @TypeConverter fun toOutboxStatus(v: String?): OutboxStatus = OutboxStatus.from(v)

    @TypeConverter fun fromCallDirection(s: CallDirection): String = s.value
    @TypeConverter fun toCallDirection(v: String?): CallDirection = CallDirection.from(v)

    @TypeConverter fun fromCallStatus(s: CallStatus): String = s.value
    @TypeConverter fun toCallStatus(v: String?): CallStatus = CallStatus.from(v)

    @TypeConverter fun fromChatMessageType(s: ChatMessageType): String = s.value
    @TypeConverter fun toChatMessageType(v: String?): ChatMessageType = ChatMessageType.from(v)
}

/**
 * توحيد تهريب LIKE + حدود البحث في مكان واحد.
 *
 * القاعدة: الشرطة المائلة أولًا ثم ‎%‎ ثم ‎_‎ — والترتيب مقصود،
 * وإلا ضوعف تهريب ما بعدها. كل DAO بحث يستعمل ‎ESCAPE '\'‎.
 */
object SearchGuards {
    const val MIN_QUERY_LENGTH = 2
    const val MAX_QUERY_LENGTH = 100
    const val SEARCH_LIMIT_DEFAULT = 50
    const val SEARCH_LIMIT_MAX = 100

    /** يهرّب ‎\ % _‎ ويقص الطول — يعيد نصًا آمنًا لوضعه داخل ‎%…%‎. */
    fun escapeLike(raw: String): String {
        val trimmed = raw.trim().take(MAX_QUERY_LENGTH)
        return trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    /** نمط ‎%…%‎ جاهز للـ DAO (يفترض ‎ESCAPE '\'‎ في SQL). */
    fun likePattern(raw: String): String = "%${escapeLike(raw)}%"

    /** صالح للبحث؟ (بعد القصّ والتهريب) */
    fun isSearchable(raw: String): Boolean {
        val t = raw.trim()
        return t.length >= MIN_QUERY_LENGTH
    }

    fun coerceLimit(limit: Int, default: Int = SEARCH_LIMIT_DEFAULT): Int =
        if (limit <= 0) default else limit.coerceIn(1, SEARCH_LIMIT_MAX)

    fun coercePage(limit: Int, offset: Int): Pair<Int, Int> =
        (limit.coerceIn(1, 100)) to (offset.coerceAtLeast(0))
}
