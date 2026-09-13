package com.red.sovereign.core

import android.content.Context
import android.content.SharedPreferences
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.groups.GroupMember

data class MentionCandidate(
    val redId: String,
    val username: String,
    val displayName: String,
)

/**
 * إشارات المجموعة بالاسم — بلا API جديد.
 *
 * النص يظهر `@username` ليقرأه البشر. حقل [RichMessage.mentions] يبقى
 * `@REDID` لأن الإشعار والتوجيه يعتمدان على المعرّف لا على الاسم.
 */
object GroupMentions {

    val AT_QUERY = Regex("""@([A-Za-z0-9_\u0600-\u06FF.]{0,20})$""")
    val NAME_TOKEN = Regex("""@([A-Za-z0-9_\u0600-\u06FF.]{2,32})""")

    // ── P1-D: @all ذكي ──────────────────────────────────────────────
    /** مجموعات أكبر من هذا الحد: @all للمشرفين فقط (OWNER/ADMIN). */
    const val LARGE_GROUP_ALL_THRESHOLD = 32

    /** كلمات الطوارئ — @all مع إحداها يتجاوز كل الكتم (حتى كتم @all المنفصل). */
    val EMERGENCY_TOKENS = listOf("عاجل", "طارئ", "طوارئ", "urgent", "emergency", "sos", "🆘")

    /** تخزين إعداد الكتم المنفصل لـ @all (لكل محادثة) — SharedPreferences خفيف بلا جدول جديد. */
    const val MUTE_ALL_PREFS = "group_mentions_prefs"
    private fun muteAllKey(conversationId: String) = "mute_all_" + conversationId.trim()

    fun isAllMuted(prefs: SharedPreferences, conversationId: String): Boolean {
        if (conversationId.isBlank()) return false
        return runCatching { prefs.getBoolean(muteAllKey(conversationId), false) }.getOrDefault(false)
    }

    fun setAllMuted(prefs: SharedPreferences, conversationId: String, muted: Boolean) {
        if (conversationId.isBlank()) return
        runCatching { prefs.edit().putBoolean(muteAllKey(conversationId), muted).apply() }
    }

    fun isAllMuted(context: Context, conversationId: String): Boolean =
        runCatching {
            isAllMuted(context.applicationContext.getSharedPreferences(MUTE_ALL_PREFS, Context.MODE_PRIVATE), conversationId)
        }.getOrDefault(false)

    fun setAllMuted(context: Context, conversationId: String, muted: Boolean) =
        runCatching {
            setAllMuted(context.applicationContext.getSharedPreferences(MUTE_ALL_PREFS, Context.MODE_PRIVATE), conversationId, muted)
        }

    /** OWNER/ADMIN فقط — MODERATOR/MEMBER/الفارغ مرفوض. */
    fun isAdminRole(role: String?): Boolean =
        role?.trim()?.uppercase() in setOf("OWNER", "ADMIN")

    /**
     * هل يحق للمرسل استعمال @all؟
     * - مجموعات ≤ [LARGE_GROUP_ALL_THRESHOLD]: الجميع.
     * - مجموعات أكبر: المشرفون (OWNER/ADMIN) فقط.
     * - memberCount ≤ 0 يعني "غير معروف" (متصل قديم): نسمح حفاظًا على التوافق الخلفي.
     */
    fun canUseAll(memberCount: Int, senderRole: String?): Boolean {
        if (memberCount <= 0) return true
        if (memberCount <= LARGE_GROUP_ALL_THRESHOLD) return true
        return isAdminRole(senderRole)
    }

    /** نفس الفحص من قائمة الأعضاء بدل تمرير الدور يدويًا. */
    fun canUseAll(members: List<GroupMember>, senderRedId: String): Boolean {
        if (members.size <= LARGE_GROUP_ALL_THRESHOLD) return true
        val role = members.firstOrNull { it.redId == senderRedId }?.role
        return isAdminRole(role)
    }

    /** رسالة منع عربية للواجهة — null تعني مسموح. */
    fun describeAllRestriction(memberCount: Int, senderRole: String?): String? =
        if (canUseAll(memberCount, senderRole)) null
        else "@all للمشرفين فقط في المجموعات الكبيرة (أكثر من $LARGE_GROUP_ALL_THRESHOLD عضوًا)"

    fun containsAllMention(text: String): Boolean =
        text.contains("@all", ignoreCase = true) || text.contains("@الجميع")

    fun containsOnlineMention(text: String): Boolean =
        text.contains("@online", ignoreCase = true) || text.contains("@متصل")

    fun isEmergencyText(text: String): Boolean =
        EMERGENCY_TOKENS.any { it in text || (it.length > 2 && text.contains(it, ignoreCase = true)) }

    /** @all طارئ: يتجاوز كل الكتم بما فيه كتم @all المنفصل. */
    fun isEmergencyAll(text: String): Boolean = containsAllMention(text) && isEmergencyText(text)

    /** هل mentions تتضمن المستخدم مباشرة (@REDID)؟ */
    fun isDirectMention(mentions: List<String>, ownRedId: String): Boolean {
        if (ownRedId.isBlank()) return false
        return mentions.any { it.removePrefix("@") == ownRedId }
    }

    /**
     * قرار الإشعار الموحّد للمجموعات (دالة صرفة بلا IO — يقرأها RedConnectionService مستقبلًا).
     *
     * | الحالة | السلوك |
     * |---|---|
     * | @all طارئ (عاجل/طارئ/urgent…) | يُنبّه دائمًا — يتجاوز كل الكتم |
     * | @all عادي + كتم @all المنفصل مفعّل | صمت (غير الطارئ فقط) |
     * | غير مكتوم + Highlights OFF | كل الرسائل تُنبّه |
     * | غير مكتوم + Highlights ON | فقط: منشن مباشر / رد عليّ / من جهة اتصال / @all |
     * | مكتوم + Highlights ON | فقط: منشن مباشر / رد عليّ / @all (غير المكتوم منفصلًا) |
     * | مكتوم + Highlights OFF | صمت إلا @all (غير المكتوم منفصلًا) والطارئ |
     *
     * @param isMuted الكتم العام للمحادثة (muted_until).
     * @param muteAllSeparate إعداد الكتم المنفصل لـ @all.
     * @param highlightsOnly وضع Highlights (منشن/رد/جهات فقط).
     */
    fun shouldNotifyForGroupMessage(
        isMuted: Boolean,
        muteAllSeparate: Boolean,
        highlightsOnly: Boolean,
        isMentioned: Boolean,
        isReplyToMe: Boolean,
        isFromContact: Boolean,
        isAll: Boolean,
        isEmergency: Boolean,
    ): Boolean {
        // الطوارئ تتجاوز كل شيء — لكن فقط إن كانت موجهة لي (@all أو منشن مباشر).
        if (isEmergency && (isAll || isMentioned)) return true
        // الكتم المنفصل يُسكت @all العادي (الطارئ عولج أعلاه).
        if (isAll && muteAllSeparate) return false
        if (!isMuted) {
            if (!highlightsOnly) return true
            return isMentioned || isReplyToMe || isFromContact || isAll
        }
        // المحادثة مكتومة: @all العادي يتجاوز الكتم العام (طوارئ مصغّرة) ما لم يُكتم منفصلًا.
        if (isAll) return true
        // المنشن/الرد يتجاوزان الكتم فقط في وضع Highlights (منع إزعاج المجموعات المكتومة افتراضيًا).
        if (!highlightsOnly) return false
        return isMentioned || isReplyToMe
    }

    fun query(text: String): String? = AT_QUERY.find(text)?.groupValues?.get(1)

    fun candidates(
        query: String,
        members: List<GroupMember>,
        friends: List<PublicRedProfile>,
        ownRedId: String,
        memberCount: Int = 0,
        senderRole: String? = null,
    ): List<MentionCandidate> {
        val friendById = friends.associateBy { it.redId }
        val specialCandidates = mutableListOf<MentionCandidate>()
        // P1-D: إخفاء @all عن غير المشرفين في المجموعات الكبيرة (>32).
        // memberCount=0 (متصل قديم) يُبقي السلوك القديم.
        val showAll = if (memberCount > 0) canUseAll(memberCount, senderRole) else true
        if (showAll && ("all".contains(query, ignoreCase = true) || "الجميع".contains(query))) {
            specialCandidates += MentionCandidate(redId = "ALL", username = "all", displayName = "الجميع (@all)")
        }
        if ("online".contains(query, ignoreCase = true) || "متصل".contains(query)) {
            specialCandidates += MentionCandidate(redId = "ONLINE", username = "online", displayName = "المتصلون الآن (@online)")
        }

        val memberCandidates = members
            .asSequence()
            .filter { it.redId != ownRedId && it.redId.isNotBlank() }
            .map { member ->
                val friend = friendById[member.redId]
                MentionCandidate(
                    redId = member.redId,
                    username = member.username,
                    displayName = friend?.displayName?.ifBlank { member.username } ?: member.username.ifBlank { member.redId },
                )
            }
            .filter { candidate ->
                query.isBlank() ||
                    candidate.displayName.contains(query, ignoreCase = true) ||
                    candidate.username.contains(query, ignoreCase = true) ||
                    candidate.redId.contains(query)
            }
            .sortedBy { it.displayName }
            .take(8)
            .toList()

        return specialCandidates + memberCandidates
    }

    fun insert(text: String, username: String): String {
        val token = sanitizeUsername(username)
        return text.replace(AT_QUERY, "@$token ")
    }

    fun mentionIds(
        text: String,
        members: List<GroupMember>,
        friends: List<PublicRedProfile> = emptyList(),
        senderRole: String? = null,
        memberCount: Int = 0,
    ): List<String> {
        if (containsAllMention(text)) {
            // P1-D: منع توسيع @all لغير المشرفين في المجموعات الكبيرة — لا إشعار جماعي من عضو عادي.
            if (memberCount > 0 && !canUseAll(memberCount, senderRole)) {
                return emptyList()
            }
            return members.filter { it.redId.isNotBlank() }.map { "@${it.redId}" }.take(200)
        }
        if (containsOnlineMention(text)) {
            return members.filter { it.redId.isNotBlank() }.map { "@${it.redId}" }.take(200)
        }
        val tokens = NAME_TOKEN.findAll(text).map { it.groupValues[1] }.toList()
        return tokens.mapNotNull { token ->
            when {
                YounesId.isValid(token) -> "@$token"
                token.equals("all", ignoreCase = true) || token == "الجميع" -> null
                token.equals("online", ignoreCase = true) || token == "متصل" -> null
                else -> {
                    val member = members.firstOrNull { it.username.equals(token, ignoreCase = true) }
                    val friend = friends.firstOrNull { it.username.equals(token, ignoreCase = true) }
                    (member?.redId ?: friend?.redId)?.let { "@$it" }
                }
            }
        }.distinct().take(20)
    }

    fun displayLabel(
        redIdOrAt: String,
        members: List<GroupMember>,
        friends: List<PublicRedProfile>,
    ): String {
        val id = redIdOrAt.removePrefix("@")
        friends.firstOrNull { it.redId == id }?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        members.firstOrNull { it.redId == id }?.username?.takeIf { it.isNotBlank() }?.let { return it }
        return id
    }

    /** اسم يظهر فوق فقاعة المجموعة: صديق ثم username ثم المعرّف كاملاً — لا نقطع خمسة أرقام. */
    fun senderName(
        redId: String,
        members: List<GroupMember>,
        friends: List<PublicRedProfile>,
        ownRedId: String = "",
    ): String {
        if (redId.isNotBlank() && redId == ownRedId) return "أنت"
        return displayLabel(redId, members, friends)
    }

    fun highlightTokens(
        text: String,
        members: List<GroupMember>,
        friends: List<PublicRedProfile>,
    ): List<String> {
        val fromNames = NAME_TOKEN.findAll(text).map { it.value }.toList()
        val fromIds = members.map { "@${it.redId}" } + friends.map { "@${it.redId}" }
        return (fromNames + fromIds.filter { it in text }).distinct()
    }

    fun sanitizeUsername(username: String): String =
        username.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(20).ifBlank { username.take(20) }
}
