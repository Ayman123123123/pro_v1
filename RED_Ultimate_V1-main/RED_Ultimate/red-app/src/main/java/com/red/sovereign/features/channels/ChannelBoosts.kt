package com.red.sovereign.features.channels

/**
 * ════════════════════════════════════════════════════════════════════════
 *  P1-G — تعزيزات القناة (ChannelBoosts) — red-app, offline-first
 *  - عدّاد boosts فقط + مستوى level = boosts / 5 — بدون NFT/توكن إطلاقًا.
 *  - ربط ChannelService: يستدعي ChannelService هذه الدوال الخالصة قبل
 *    النشر/الرفع (canSendInBroadcast للصلاحية + level/perks للحدود) —
 *    لا شبكة هنا، والخدمة هي التي تدمج النتيجة مع ChannelMode وحالة العضوية.
 *    مثال: if (!canSendInBroadcast(myRole, isBroadcast)) return // ارفض النشر
 *           val perks = ChannelBoosts(channelId, boosts).perks()
 *  - ملاحظة عدم تعارض: يوجد perksForLevel(level): ChannelLevelPerks في
 *    ChannelMode.kt بنفس الحزمة، لذلك النسخة الـ List<String> المطلوبة هنا
 *    مسماة نطاقيًا ChannelBoostsPolicy.perksForLevel + الاسم المختصر
 *    boostPerksForLevel (نفس المنطق) لتفادي Conflicting overloads.
 * ════════════════════════════════════════════════════════════════════════
 */

/**
 * حالة تعزيز قناة واحدة.
 * @param channelId معرف القناة.
 * @param boosts عدد الـ boosts (سالب يُعامل كصفر).
 */
data class ChannelBoosts(
    val channelId: String,
    val boosts: Int = 0
) {
    /** المستوى = boosts / 5 (أعداد سالبة تُعامل كصفر). */
    fun level(): Int = boosts.coerceAtLeast(0) / 5

    /** مزايا المستوى الحالي كقائمة نصية. */
    fun perks(): List<String> = ChannelBoostsPolicy.perksForLevel(level())
}

/**
 * سياسات الـ Boosts — دوال خالصة قابلة للاختبار دون شبكة/DB.
 */
object ChannelBoostsPolicy {
    /**
     * مزايا المستوى:
     * - L1+: إيموجي جماعي (collective_emoji)
     * - L2+: حدود أعلى (higher_limits: ملفات/مثبتات/طول المنشور)
     * - L3+: تجاوز slow mode (slow_mode_bypass)
     * - L0: بلا مزايا.
     */
    fun perksForLevel(level: Int): List<String> {
        if (level <= 0) return emptyList()
        val out = ArrayList<String>(3)
        // L1+
        out.add("collective_emoji")
        // L2+
        if (level >= 2) {
            out.add("higher_limits")
        }
        // L3+
        if (level >= 3) {
            out.add("slow_mode_bypass")
        }
        return out
    }

    /**
     * هل يحق الإرسال في قناة بث؟
     * - isBroadcast=false → أي عضو يرسل (true).
     * - isBroadcast=true → OWNER/ADMIN فقط (MODERATOR/SUBSCRIBER/غير معروف = false).
     * ملاحظة: ChannelMode.isChannelAdmin يشمل MODERATOR للنشر العلوي العام،
     * أما سياسة البث الصارمة هنا فهي OWNER/ADMIN فقط حسب spec P1-G.
     */
    fun canSendInBroadcast(myRole: String, isBroadcast: Boolean): Boolean {
        if (!isBroadcast) return true
        return when (myRole.trim().uppercase()) {
            "OWNER", "ADMIN" -> true
            else -> false
        }
    }
}

/**
 * اسم مختصر top-level لنفس منطق perksForLevel بصيغة List<String>
 * (تجنّبًا للتعارض مع ChannelMode.kt الذي يملك perksForLevel: ChannelLevelPerks).
 */
fun boostPerksForLevel(level: Int): List<String> =
    ChannelBoostsPolicy.perksForLevel(level)

/**
 * فحص صلاحية الإرسال في البث — top-level مباشر حسب spec P1-G.
 * @param myRole دوري كنص (OWNER/ADMIN/MODERATOR/SUBSCRIBER).
 * @param isBroadcast true = قناة بث.
 */
fun canSendInBroadcast(myRole: String, isBroadcast: Boolean): Boolean =
    ChannelBoostsPolicy.canSendInBroadcast(myRole, isBroadcast)
