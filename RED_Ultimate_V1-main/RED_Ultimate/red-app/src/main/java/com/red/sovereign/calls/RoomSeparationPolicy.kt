package com.red.sovereign.calls

/**
 * G13 — الفصل المعماري للغرف (عميل). مرآة `com.red.server.calls.RoomSeparationPolicy` في الخادم —
 * أي تغيير على البادئات/الحدود هنا يجب عكسه هناك.
 *
 * | النوع | البادئة | الخدمة | المسار الإعلامي |
 * | GroupChat (دردشة مجموعة ≤8) | GRP_ | GroupCallService | Mesh أولاً → ترقية SFU بعد 8 (IMO-style) |
 * | Friends (أصدقاء/SFU كامل) | FRND_ | ZoomGroupCallService | SFU كامل + Mesh احتياط فقط |
 * | Live (إنتاج مرة/استهلاك كثير) | LIVE_ | LiveStreamService | المذيع ينشر (WHIP) / المشاهد استقبال فقط (WHEP/LL-HLS) |
 * | Conference/Spaces (متحدثون+mixer) | CONF_ | ConferenceService | SFU للمتحدثين + مزيج واحد للمستمعين (X Spaces) |
 *
 * توافقية: معرفات legacy بلا بادئة تُقبل ولا تُكسر — الفصل للغرف الجديدة.
 */
object RoomSeparationPolicy {

    const val PREFIX_GROUP = "GRP_"
    const val PREFIX_FRIENDS = "FRND_"
    const val PREFIX_LIVE = "LIVE_"
    const val PREFIX_CONF = "CONF_"

    /** GroupChat: حد Mesh الناعم — فوقه الترقية إلى SFU. */
    const val GROUP_CHAT_MESH_LIMIT = 8

    /** Friends/Zoom: سقف SFU الكامل (مطابق ZOOM_LIMIT). */
    const val FRIENDS_SFU_LIMIT = 100

    /** Live: مضيفون مشاركون ≤4. */
    const val LIVE_COHOST_LIMIT = 4

    /** Spaces: خانات التحدث المستهدفة (11 متحدثاً + مضيفان = 13). */
    const val CONF_SPEAKER_SLOTS = 13

    private val ROOM_ID = Regex("^[A-Za-z0-9_-]{4,128}$")

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
     * تطبيع معرف: فارغ → بادئة + مولّد؛ مبادأ → كما هو؛ legacy صالح → يُضاف له FRND_ للـ Zoom
     * (alias map للقديم)؛ غير صالح → تنظيف + بادئة. مطابِق لسلوك الخادم تماماً.
     * G13-fix: LEGACY الصالح للـ Zoom يُضاف له FRND_ لا يُمرر كما هو — حتى لا تتفرع الخريطة/Redis.
     */
    fun normalize(prefix: String, raw: String?, generate: () -> String): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return prefix + generate()
        if (kindOf(v) != RoomKind.LEGACY) return v
        // FRND_ (Friends/Zoom): حتى المعرفات القديمة الصالحة (XXXX-XXXX) تُطبَّع إلى FRND_XXXX-XXXX
        // مع حفظ alias للقديم في ZoomRoomService حتى لا تنكسر الروابط القديمة.
        if (isValidRoomId(v)) {
            if (prefix == PREFIX_FRIENDS) return prefix + v
            return v
        }
        val clean = v.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
        if (clean.length < 4) return prefix + generate()
        return prefix + clean
    }

    fun normalizeGroupCallId(raw: String?): String =
        normalize(PREFIX_GROUP, raw) {
            java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        }

    fun normalizeMeetingId(raw: String?): String =
        normalize(PREFIX_FRIENDS, raw) { freshMeetingCore() }

    fun normalizeStreamId(raw: String?): String =
        normalize(PREFIX_LIVE, raw) {
            java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        }

    fun normalizeRoomId(raw: String?): String =
        normalize(PREFIX_CONF, raw) {
            java.util.UUID.randomUUID().toString().replace("-", "").take(12)
        }

    /** نواة معرف اجتماع FRND_ بصيغة XXXX-XXXX (مطابقة لمولّد الخادم). */
    fun freshMeetingCore(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("").chunked(4).joinToString("-")
    }

    /** GroupChat: هل يبدأ Mesh أولاً؟ (≤8 أعضاء إجمالاً) — فوقه SFU مباشرة. */
    fun meshFirst(totalMembers: Int): Boolean = totalMembers <= GROUP_CHAT_MESH_LIMIT
}
