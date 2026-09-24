package com.red.sovereign.calls

/**
 * سقوف المشاركين الموحدة — المصدر الوحيد للحقيقة (4/8/32/100).
 *
 * - LIVE_COHOST_MAX = 4   : مضيفون مشاركون في البث.
 * - MESH_MAX = 8          : حد Mesh الناعم (دردشة المجموعة + تفاوض Mesh).
 * - GROUP_CALL_MAX = 32   : مكالمة المجموعة (واتساب-style).
 * - ZOOM_SFU_MAX = 100    : اجتماع Zoom/SFU الكامل.
 *
 * كل الثوابت القديمة المتناثرة (WHATSAPP_GROUP_CALL_LIMIT, MAX_PEERS,
 * GROUP_CHAT_MESH_LIMIT, FRIENDS_SFU_LIMIT, LIVE_COHOST_LIMIT) تبقى كأسماء
 * توافقية تشير إلى هنا — المنطق والتحقق المسبق يجريان عبر هذا الكائن فقط.
 */
object CallLimits {
    const val LIVE_COHOST_MAX = 4
    const val MESH_MAX = 8
    const val GROUP_CALL_MAX = 32
    const val ZOOM_SFU_MAX = 100

    enum class Kind {
        LIVE_COHOST,
        MESH,
        GROUP_CALL,
        ZOOM
    }

    fun limitFor(kind: Kind): Int = when (kind) {
        Kind.LIVE_COHOST -> LIVE_COHOST_MAX
        Kind.MESH -> MESH_MAX
        Kind.GROUP_CALL -> GROUP_CALL_MAX
        Kind.ZOOM -> ZOOM_SFU_MAX
    }

    /**
     * تحقق مسبق: null تعني مسموح، وإلا رسالة عربية جاهزة للعرض (Toast/Notification).
     */
    fun check(kind: Kind, count: Int): String? {
        val limit = limitFor(kind)
        if (count <= limit) return null
        return message(kind, limit)
    }

    fun exceeds(kind: Kind, count: Int): Boolean = count > limitFor(kind)

    fun message(kind: Kind, limit: Int = limitFor(kind)): String = when (kind) {
        Kind.LIVE_COHOST -> "الحد الأقصى $limit مضيفين مشاركين في البث"
        Kind.MESH -> "الحد الأقصى $limit مشاركين في شبكة Mesh — تتم الترقية إلى SFU تلقائياً"
        Kind.GROUP_CALL -> "الحد الأقصى $limit مشاركاً في المكالمة الجماعية"
        Kind.ZOOM -> "الحد الأقصى $limit مشاركاً في الاجتماع"
    }

    // اختصارات مريحة لمواضع الاستدعاء.
    fun checkGroupCall(totalMembers: Int): String? = check(Kind.GROUP_CALL, totalMembers)
    fun checkZoom(totalMembers: Int): String? = check(Kind.ZOOM, totalMembers)
    fun checkMesh(peerCount: Int): String? = check(Kind.MESH, peerCount)
    fun checkLiveCohost(cohosts: Int): String? = check(Kind.LIVE_COHOST, cohosts)
}
