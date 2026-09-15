package com.red.sovereign.features.channels

/**
 * ════════════════════════════════════════════════════════════════════════
 *  P1-G — وضع القناة/البث + Boosts lite (red-app, offline-first)
 *  - القناة = بث أحادي افتراضيًا: isBroadcast=true → الأدمن فقط يرسل،
 *    والمشتركون: ردود Thread + تفاعلات فقط (لا نشر علوي).
 *  - Boosts lite: عدّاد فقط (boostsCount) + مستوى level = boosts/5 +
 *    مزايا level (إيموجي جماعي، حدود أعلى) — بدون NFT/توكن إطلاقًا.
 *  - البحث السحابي مكمّل للبحث المحلي (TODO موثّق أدناه) — offline أولًا.
 * ════════════════════════════════════════════════════════════════════════
 */

/** عدد الـ boosts اللازمة لكل مستوى — level = boostsCount / 5 */
const val BOOSTS_PER_LEVEL: Int = 5

/** سقف تشبّع المزايا (المستوى يستمر بالصعود حسابيًا، المزايا تثبت هنا) */
const val MAX_PERK_LEVEL: Int = 5

/** أدوار القناة — يطابق backend (OWNER/ADMIN/MODERATOR/SUBSCRIBER) */
enum class ChannelMemberRole {
    OWNER,
    ADMIN,
    MODERATOR,
    SUBSCRIBER;

    companion object {
        /** تحويل آمن من نص الخادم — غير المعروف يُعامل كمشترك (أقل صلاحية). */
        fun fromServer(raw: String?): ChannelMemberRole = when (raw?.trim()?.uppercase()) {
            "OWNER" -> OWNER
            "ADMIN" -> ADMIN
            "MODERATOR" -> MODERATOR
            else -> SUBSCRIBER
        }
    }
}

/**
 * وضع القناة.
 * @param isBroadcast true = بث (أدمن فقط ينشر) / false = نقاش مفتوح للأعضاء.
 * @param allowThreadReplies الردود كـ Thread على منشورات الأدمن (تعمل في وضع البث).
 * @param allowReactions التفاعلات (إيموجي) مسموحة للمشتركين حتى في وضع البث.
 */
data class ChannelMode(
    val isBroadcast: Boolean = true,
    val allowThreadReplies: Boolean = true,
    val allowReactions: Boolean = true
)

/** هل الدور إدارة (يحق له النشر في وضع البث)؟ */
fun isChannelAdmin(role: ChannelMemberRole): Boolean = when (role) {
    ChannelMemberRole.OWNER, ChannelMemberRole.ADMIN, ChannelMemberRole.MODERATOR -> true
    ChannelMemberRole.SUBSCRIBER -> false
}

/**
 * هل يحق للعضو إرسال منشور علوي جديد؟
 * - وضع البث: الأدمن فقط.
 * - الوضع المفتوح: أي عضو.
 */
fun canSendTopLevel(mode: ChannelMode, role: ChannelMemberRole): Boolean =
    if (mode.isBroadcast) isChannelAdmin(role) else true

/**
 * هل يحق الرد ضمن Thread على منشور موجود؟
 * المشترك في وضع البث يرد Thread فقط (لا ينشر علويًا).
 */
fun canReplyInThread(mode: ChannelMode, role: ChannelMemberRole): Boolean {
    if (role == ChannelMemberRole.SUBSCRIBER && !mode.allowThreadReplies) return false
    // الضيف غير العضو يُمنع خارج هذا الملف (يفحص isMember أولًا) — هنا نفترض عضوًا.
    return mode.allowThreadReplies
}

/** هل يحق التفاعل (إيموجي) على منشور؟ — مسموح للمشتركين في البث إن كانت مفعّلة. */
fun canReact(mode: ChannelMode, @Suppress("UNUSED_PARAMETER") role: ChannelMemberRole): Boolean =
    mode.allowReactions

// ────────────────────────────────────────────────────────────────
// Boosts lite — عدّاد + مستوى + مزايا، بدون NFT
// ────────────────────────────────────────────────────────────────

/** مستوى القناة = boostsCount / 5 (أعداد سالبة تُعامل كصفر). */
fun levelForBoosts(boostsCount: Int): Int =
    boostsCount.coerceAtLeast(0) / BOOSTS_PER_LEVEL

/** حالة التعزيز — level مشتق دائمًا (لا يُخزّن منفصلًا فيختل). */
data class ChannelBoostState(
    val boostsCount: Int = 0
) {
    val level: Int get() = levelForBoosts(boostsCount)
}

/** تعزيز جديد: يضيف [by] ويعيد الحالة (by موجب، يُتجاهل الصفر/السالب). */
fun boostChannel(current: ChannelBoostState, by: Int = 1): ChannelBoostState {
    if (by <= 0) return current
    return current.copy(boostsCount = current.boostsCount.coerceAtLeast(0) + by)
}

/**
 * مزايا المستوى — تتصاعد مع level وتتشبّع عند [MAX_PERK_LEVEL].
 * - L1 (5 boosts): إيموجي جماعي مفتوح.
 * - L2 (10): حدود أعلى (ملفات/مثبتات/طول المنشور).
 * - L3+: توسّع إضافي (روابط مخصّصة + HD — [ChannelLevelPerks.customLinks]) ثم ثبات.
 */
data class ChannelLevelPerks(
    val level: Int,
    /** إيموجي جماعي: حزمة القناة متاحة لكل المشتركين */
    val hasCollectiveEmoji: Boolean,
    val maxFileMb: Int,
    val maxPinnedMessages: Int,
    val maxPostLength: Int
) {
    /**
     * P1-G (إصلاح 2026-09-16): روابط مخصّصة + HD — تُفتح عند المستوى الثالث
     * («توسّع إضافي» في سلّم المزايا أعلاه) وتُشتق من [level] فلا تُخزَّن ولا
     * تُرسَل من الخادم، وبذلك لا تتغيّر عقود الـ API ولا تُلمس النسخة الخلفية.
     *
     * سبب الإضافة: ChannelsScreen يستعمل `levelPerks.customLinks` وكان الحقل
     * غير موجود ⇒ خطأ ترجمة `Unresolved reference 'customLinks'` أحمرّ مهمة
     * Android في CI على main.
     */
    val customLinks: Boolean get() = level >= 3
}

fun perksForLevel(level: Int): ChannelLevelPerks {
    val saturated = level.coerceIn(0, MAX_PERK_LEVEL)
    return when {
        saturated <= 0 -> ChannelLevelPerks(0, false, 100, 3, 1024)
        saturated == 1 -> ChannelLevelPerks(1, true, 200, 5, 2048)
        saturated == 2 -> ChannelLevelPerks(2, true, 500, 10, 4096)
        saturated == 3 -> ChannelLevelPerks(3, true, 1000, 20, 4096)
        else -> ChannelLevelPerks(saturated, true, 2000, 50, 8192)
    }
}

/** اختصار: مزايا الحالة الحالية مباشرة. */
fun ChannelBoostState.perks(): ChannelLevelPerks = perksForLevel(level)

// ────────────────────────────────────────────────────────────────
// بحث سحابي مكمّل — المحلي أولًا، السحابي مكمّل فقط
// ────────────────────────────────────────────────────────────────

/**
 * عنصر قناة للبحث/الدمج — نسخة عرض خفيفة (offline-safe، بلا شبكة).
 */
data class ChannelSearchItem(
    val id: String,
    val name: String,
    val username: String? = null
)

/**
 * دمج نتائج البحث: المحلي أولًا ثم إضافات السحابي (إزالة تكرار حسب id).
 * السياسة: البحث المحلي (FTS5/Room) هو الأساس offline؛ السحابي يُكمّل فقط —
 * لا يحجب ولا يستبدل، ويُدمج بهذه الدالة الخالصة (قابلة للاختبار دون شبكة).
 */
fun mergeChannelSearch(
    local: List<ChannelSearchItem>,
    cloud: List<ChannelSearchItem>
): List<ChannelSearchItem> {
    if (cloud.isEmpty()) return local
    if (local.isEmpty()) return cloud
    val seen = LinkedHashSet<String>(local.size + cloud.size)
    val out = ArrayList<ChannelSearchItem>(local.size + cloud.size)
    for (item in local) {
        if (seen.add(item.id)) out.add(item)
    }
    for (item in cloud) {
        if (seen.add(item.id)) out.add(item)
    }
    return out
}

// السلك السحابي مكتمل الآن (كان TODO(P1-G)):
//  1) Backend: `GET /api/channels?search=` مفعَّل في ChannelController.list →
//     ChannelService.searchCloudComplement (ILIKE على name/username/description،
//     حد 1..100، ترتيب subscriberCount DESC، وفشل الاستعلام يعيد قائمة فارغة).
//  2) Client: features/channels/ChannelsApi.kt — بنمط CommunitiesApi عبر
//     AuthorizedApiClient، ودالة الدمج `ChannelsApi.searchMerged(local, query)`
//     تُطبّق هذه السياسة في مكان واحد: حد أدنى حرفين بلا شبكة، وتجاهل أي فشل
//     (OFFLINE/401/429/500) بصمت فيُعرض المحلي وحده.
//  3) Debounce موحّد 300ms (MESSAGE_SEARCH_DEBOUNCE_MS) داخل collectLatest مثل
//     RedGlobalSearch — يُطبّقه مستهلك الواجهة عند بناء شاشة القنوات.
// المتبقي: لا توجد شاشة قنوات في red-app بعد؛ هذه الطبقة جاهزة لها.
