package com.red.sovereign.core

/**
 * P1-D — MentionPolicy: سياسة @all والكتم والإشعارات (ملف جديد مستقل).
 *
 * ربط الواجهة: GroupMentionBar يضيف زر @all يستدعي
 * [MentionPolicy.canUseAtAll] لإظهار/إخفاء الزر قبل الإرسال
 * (المجموعات > 32 عضوًا: OWNER/ADMIN فقط — انظر [GroupMentions.canUseAll]).
 *
 * لم يُعدَّل GroupMentions.kt لتفادي التعارض — هذه السياسة طبقة خفيفة
 * فوقه ويمكن لـ RedConnectionService / الواجهة استدعاؤها مباشرة.
 */
object MentionPolicy {

    /** نفس عتبة GroupMentions.LARGE_GROUP_ALL_THRESHOLD (=32) مكررة محليًا لتفادي الاعتماد الدائري. */
    const val LARGE_GROUP_THRESHOLD = 32

    /** هل الدور مشرف؟ OWNER/ADMIN فقط (case-insensitive، مع trim). */
    fun isPrivilegedRole(myRole: String): Boolean =
        myRole.trim().uppercase() in setOf("OWNER", "ADMIN")

    /**
     * هل يحق لي استعمال @all؟
     * - memberCount > 32 → OWNER/ADMIN فقط.
     * - غير ذلك (مجموعات صغيرة) → أي عضو.
     * - memberCount <= 0 (غير معروف/متصل قديم) → نسمح حفاظًا على التوافق الخلفي.
     */
    fun canUseAtAll(myRole: String, memberCount: Int): Boolean {
        if (memberCount <= 0) return true
        if (memberCount <= LARGE_GROUP_THRESHOLD) return true
        return isPrivilegedRole(myRole)
    }

    /**
     * هل تتجاوز الرسالة الكتم؟ @all يتجاوز الكتم للطوارئ.
     * - isAtAll = true → تجاوز (بث طارئ).
     * - أو mentions تحتوي @ALL/@all → تجاوز.
     * - غير ذلك → لا تجاوز.
     */
    fun shouldBypassMute(mentions: List<String>, isAtAll: Boolean): Boolean {
        if (isAtAll) return true
        if (mentions.isEmpty()) return false
        return mentions.any {
            val id = it.removePrefix("@")
            id.equals("ALL", ignoreCase = true)
        }
    }

    /** أوضاع الإشعار للمحادثة. */
    enum class NotifyMode {
        /** كل الرسائل تُنبّه. */
        ALL,

        /** المنشن + الرد عليّ + جهات الاتصال فقط. */
        HIGHLIGHTS,

        /** المنشن والرد فقط (الأضيق — حتى جهات الاتصال لا تُنبّه بلا منشن). */
        MENTIONS_ONLY
    }

    /**
     * قرار الإشعار الخفيف (دالة صرفة بلا IO).
     * - ALL → true دائمًا.
     * - HIGHLIGHTS → منشن أو رد أو من جهة اتصال.
     * - MENTIONS_ONLY → منشن أو رد فقط.
     */
    fun shouldNotify(
        mode: NotifyMode,
        isMention: Boolean,
        isReply: Boolean,
        isContact: Boolean
    ): Boolean = when (mode) {
        NotifyMode.ALL -> true
        NotifyMode.HIGHLIGHTS -> isMention || isReply || isContact
        NotifyMode.MENTIONS_ONLY -> isMention || isReply
    }

    /**
     * تفضيلات الكتم (لكل محادثة).
     * @param mutedUntil epoch-ms — 0 تعني غير مكتومة؛ القارئ يقارن مع System.currentTimeMillis().
     * @param muteAtAll كتم منفصل لصوت @all العادي (الطارئ يتجاوزه عبر [shouldBypassMute]).
     */
    data class MutePrefs(
        val mutedUntil: Long = 0L,
        val muteAtAll: Boolean = false
    ) {
        /** هل المحادثة مكتومة الآن؟ */
        fun isMuted(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs < mutedUntil
    }
}
