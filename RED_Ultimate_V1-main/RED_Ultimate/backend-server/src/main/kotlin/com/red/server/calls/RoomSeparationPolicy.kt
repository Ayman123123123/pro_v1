package com.red.server.calls

/**
 * G13 — الفصل المعماري للغرف (خادم).
 *
 * نتائج بحث الويب (2024-2026) التي بُني عليها الفصل:
 * • Zoom: توجيه وسائط SFU + ترميز SVC طبقي (Lego-blocks: طبقة أساس + تحسينات،
 *   العميل يفكّ الطبقات المناسبة لجهازه) — بلا Transcoding/Mixing في المسار العام،
 *   P2P لطرفين فقط، QoS بين العميل والسحابة. (systemdesign.one 2025، HighScalability 2024)
 * • IMO/WhatsApp-style: مكالمات المجموعة الصغيرة Mesh حتى ~8 ثم SFU؛ الـ SFU يخفّض
 *   تعقيد الرفع من O(n²) إلى O(n). (algorisys-oss/webrtc-katas، Daily 2024)
 * • TikTok/YouTube Live: النشر WHIP (IETF، HTTP POST بدل إشارات معقدة، FFmpeg دمج
 *   WHIP muxer 2025-06) والتشغيل WHEP للزمن شبه-الحقيقي (<1s) مع LL-HLS (500ms-3s)
 *   كبديل متوافق — produce-once/consume-many. (CDNetworks 2025، Cloudflare Stream، MediaMTX)
 * • X Spaces: المتحدثون عبر WebRTC إلى SFU (حتى ~11 متحدث + مضيفان مشاركان)،
 *   والمستمعون يستلمون مزيجاً واحداً عبر مسار توزيع (CDN/HLS) — كتم مفروض خادمياً
 *   (صوت المكتوم يُسقط في الـ SFU حتى لو واصل تطبيقه الإرسال). (techinterview 2026، X Business)
 *
 * | النوع | البادئة | المسار الإعلامي | التذكرة canProduce |
 * | GroupChat (دردشة مجموعة ≤8) | GRP_ | Mesh أولاً → ترقية SFU بعد 8 | كل الأعضاء true |
 * | Friends (أصدقاء/SFU كامل) | FRND_ | SFU كامل (SVC طبقي) + Mesh احتياط | كل الأعضاء true |
 * | Live (بث: إنتاج مرة/استهلاك كثير) | LIVE_ | WHIP نشر + WHEP/LL-HLS استهلاك | المذيع فقط true |
 * | Conference/Spaces (متحدثون+mixer) | CONF_ | SFU للمتحدثين + مزيج واحد للمستمعين | حسب الدور |
 *
 * توافقية: المعرفات القديمة بلا بادئة (LEGACY) تُقبل كما هي ولا تُكسر —
 * الفصل يُطبَّق على الغرف الجديدة، والتذاكر القديمة تبقى صالحة.
 */
object RoomSeparationPolicy {

    const val PREFIX_GROUP = "GRP_"
    const val PREFIX_FRIENDS = "FRND_"
    const val PREFIX_LIVE = "LIVE_"
    const val PREFIX_CONF = "CONF_"

    /** GroupChat: حد Mesh الناعم (IMO-style) — فوقه الترقية إلى SFU. */
    const val GROUP_CHAT_MESH_LIMIT = 8

    /** Friends/Zoom: سقف SFU الكامل. */
    const val FRIENDS_SFU_LIMIT = 100

    /** Live: مضيفون مشاركون بحد أقصى 4 (منتجون إضافيون). */
    const val LIVE_COHOST_LIMIT = 4

    /**
     * Spaces: خانات التحدث (X: حتى 11 متحدثاً بما فيهم المضيف + مضيفان مشاركان = 13).
     * الخادم يفرض ConferenceRoomService.MAX_SPEAKERS=20 و MAX_VIDEO_TILES=12؛
     * هذا الثابت هو سقف تجربة X Spaces المستهدف في الواجهة.
     */
    const val CONF_SPEAKER_SLOTS = 13

    /** نمط معرف الغرفة الموحد 4..128 — كل الملفات تشير إلى ROOM_ID بدل نسخ محلية. */
    const val ROOM_ID_PATTERN = "^[A-Za-z0-9_-]{4,128}$"

    private val legacyAlias = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Regex موحد لمعرفات الغرف 4..128 — المرجع الوحيد لكل REST+WS. */
    val ROOM_ID = Regex(ROOM_ID_PATTERN)

    enum class RoomKind {
        GROUP_CHAT,
        FRIENDS,
        LIVE,
        CONF,
        LEGACY
    }

    fun kindOf(roomId: String): RoomKind = when {
        roomId.startsWith(PREFIX_GROUP) -> RoomKind.GROUP_CHAT
        roomId.startsWith(PREFIX_FRIENDS) -> RoomKind.FRIENDS
        roomId.startsWith(PREFIX_LIVE) -> RoomKind.LIVE
        roomId.startsWith(PREFIX_CONF) -> RoomKind.CONF
        else -> RoomKind.LEGACY
    }

    fun isValidRoomId(id: String): Boolean = id.matches(ROOM_ID)

    /**
     * تطبيع معرف غرفة: فارغ → بادئة + مولّد؛ مبادأ فعلاً → كما هو (لا ازدواج)؛
     * FRND_: legacy صالح → يُضاف له FRND_ (لا يُمرر كما هو) مع alias map للقديم؛
     * الأنواع الأخرى: legacy صالح → يُحفظ (توافق)؛ غير صالح → تنظيف + بادئة.
     */
    fun normalize(prefix: String, raw: String?, generate: () -> String): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return prefix + generate()
        if (kindOf(v) != RoomKind.LEGACY) return v
        if (isValidRoomId(v)) {
            if (prefix == PREFIX_FRIENDS) return prefix + v
            return v
        }
        val clean = v.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
        if (clean.length < 4) return prefix + generate()
        return prefix + clean
    }

    /**
     * قدرة التذكرة المتوقعة لكل نوع — مرجع قبول التذاكر في SfuTicketController:
     * • GROUP_CHAT/FRIENDS: الكل ينتج (شبكة/Mesh أو SFU كامل).
     * • LIVE: المذيع ينتج (WHIP)، المشاهد يستهلك فقط (WHEP/LL-HLS).
     * • CONF: المتحدث (HOST/CO_HOST/SPEAKER) ينتج، المستمع يستهلك المزيج فقط.
     */
    fun expectedCanProduce(kind: RoomKind, isPrivileged: Boolean): Boolean = when (kind) {
        RoomKind.GROUP_CHAT -> true
        RoomKind.FRIENDS -> true
        RoomKind.LIVE -> isPrivileged
        RoomKind.CONF -> isPrivileged
        RoomKind.LEGACY -> true
    }

    // ── فصل الغرف: تطبيع إجباري + alias legacy→canonical ──

    /** حلّ alias داخل الذاكرة: legacy مربوط → canonical، وإلا الخام مقلّماً كما هو. */
    fun resolve(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return v
        return legacyAlias[v] ?: v
    }

    /** ربط legacy→canonical في جدول الذاكرة (تُكمله طبقة Redis في RoomAliasService). */
    fun linkAlias(legacyRaw: String?, canonical: String) {
        val legacy = legacyRaw?.trim().orEmpty()
        if (legacy.isEmpty() || legacy == canonical) return
        if (kindOf(legacy) != RoomKind.LEGACY) return
        if (canonical.isBlank()) return
        legacyAlias.putIfAbsent(legacy, canonical)
    }

    fun aliasOf(legacyRaw: String?): String? {
        val legacy = legacyRaw?.trim().orEmpty()
        if (legacy.isEmpty()) return null
        return legacyAlias[legacy]
    }

    /**
     * إنشاء قانوني: يُرجع دائماً معرفاً مسبوقاً بالبادئة المطلوبة (لا room_ عارٍ)،
     * ويربط أي legacy مُدخل في جدول alias. المبادأ ببادئة مخالفة يُرفض (تقاطع مرفوض).
     * الفارغ → بادئة + مولّد. غير الصالح → تنظيف + بادئة.
     */
    fun canonicalizeForCreate(prefix: String, raw: String?, generate: () -> String): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return prefix + generate()
        val kind = kindOf(v)
        if (kind != RoomKind.LEGACY) {
            require(v.startsWith(prefix)) { "WRONG_NAMESPACE: expected $prefix room, got $v" }
            require(v.matches(ROOM_ID)) { "Invalid room ID" }
            return v
        }
        // legacy: شرعي → بادئة + الأصل مع حفظ alias؛ غير شرعي → تنظيف + بادئة.
        if (isValidRoomId(v)) {
            val canonical = prefix + v
            linkAlias(v, canonical)
            return canonical
        }
        val clean = v.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
        if (clean.length < 4) return prefix + generate()
        val canonical = prefix + clean
        linkAlias(v, canonical)
        return canonical
    }

    /** رفض متقاطع: يرمي عند خروج المعرف عن الأنواع المسموحة (LEGACY تُقبل ضمن المسموح فقط). */
    fun requireKindIn(roomId: String, vararg allowed: RoomKind): RoomKind {
        val kind = kindOf(roomId)
        require(kind in allowed) { "WRONG_NAMESPACE: $roomId is $kind, allowed=${allowed.joinToString()}" }
        return kind
    }
}
