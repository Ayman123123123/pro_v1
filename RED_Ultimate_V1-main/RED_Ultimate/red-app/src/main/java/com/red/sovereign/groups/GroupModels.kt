package com.red.sovereign.groups

import kotlinx.serialization.Serializable

@Serializable data class GroupMember(val id: String, val groupId: String, val userId: String, val redId: String, val username: String, val role: String, val joinedAt: String)
@Serializable data class GroupSettings(val onlyAdminsCanSend: Boolean = false, val onlyAdminsCanEditInfo: Boolean = true, val requireJoinApproval: Boolean = true, val onlyAdminsCanAddMembers: Boolean = true, val onlyAdminsCanInvite: Boolean = false, val onlyAdminsCanPin: Boolean = true, val onlyAdminsCanCall: Boolean = false)
@Serializable data class Group(val id: String, val name: String, val description: String? = null, val ownerRedId: String, val avatarUrl: String? = null, val privacy: String = "PRIVATE", val settings: GroupSettings = GroupSettings(), val createdAt: String, val members: List<GroupMember> = emptyList(), val communityId: String? = null)
@Serializable data class CreateGroupRequest(val name: String, val description: String? = null, val privacy: String = "PRIVATE")
@Serializable data class AddGroupMemberRequest(val redId: String, val role: String = "MEMBER")
@Serializable data class UpdateGroupRoleRequest(val role: String)
@Serializable data class TransferGroupOwnershipRequest(val targetUserId: String)
@Serializable data class UpdateGroupAvatarRequest(val mediaKey: String)
@Serializable data class UpdateGroupSettingsRequest(val onlyAdminsCanSend: Boolean? = null, val onlyAdminsCanEditInfo: Boolean? = null, val requireJoinApproval: Boolean? = null, val onlyAdminsCanAddMembers: Boolean? = null, val onlyAdminsCanInvite: Boolean? = null, val onlyAdminsCanPin: Boolean? = null, val onlyAdminsCanCall: Boolean? = null)
@Serializable data class CreateGroupInviteRequest(val expiresHours: Long = 24, val maxUses: Int = 1, val requireApproval: Boolean = true)
@Serializable data class GroupInviteResponse(val id: String, val token: String, val expiresAt: String, val maxUses: Int, val requireApproval: Boolean, val uses: Int = 0)
@Serializable data class JoinGroupRequest(val token: String)
@Serializable data class ResolveJoinRequest(val approve: Boolean)
@Serializable data class GroupJoinRequestResponse(val id: String, val groupId: String, val redId: String, val username: String, val status: String, val createdAt: String)
@Serializable data class UpdateGroupInfoRequest(val name: String? = null, val description: String? = null)

/**
 * يستخرج رمز الدعوة الخام من مدخل المستخدم: يقبل الرمز وحده أو رابطًا كاملًا
 * مثل `https://red.ly/g/<token>` أو `red://join?token=<token>` (مع تجاهل
 * البارامترات اللاحقة والـ fragment). يُستخدم من كل حوارات الانضمام.
 */
fun parseInviteToken(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    // صيغة red://join?token=XYZ (أو أي رابط يحمل بارامتر token=)
    val tokenParam = Regex("[?&]token=([^&#\\s]+)").find(t)?.groupValues?.getOrNull(1)
    if (!tokenParam.isNullOrBlank()) return tokenParam.trim()
    // صيغة https://red.ly/g/XYZ — آخر مقطع في المسار
    if (t.contains("://")) {
        val segment = t.substringAfter("://").substringAfter("/", "").substringAfterLast("/")
            .substringBefore("?").substringBefore("#").trim()
        if (segment.isNotEmpty()) return segment
        return t
    }
    return t
}

/** الرابط القانوني القابل للمشاركة لرمز دعوة خام. */
fun inviteShareLink(token: String): String = "https://red.ly/g/${token.trim()}"

/** LEGENDARY: حمولة QR للدعوة — تُمسح من أي جهاز RED دون كشف redId */
fun inviteQrPayload(token: String): String = "RED-GROUP:${inviteShareLink(token.trim())}"

/** LEGENDARY: قبول QR أو رابط أو رمز خام في مدخل واحد */
fun parseInviteTokenQrAware(raw: String): String {
    val t = raw.trim()
    // تجريد البادئة بلا حساسية لحالة الأحرف (Red-Group:/RED-GROUP:/red-group: كلها مقبولة).
    if (t.startsWith("RED-GROUP:", ignoreCase = true)) return parseInviteToken(t.substring("RED-GROUP:".length))
    return parseInviteToken(t)
}

// ── وجهة واحدة لصلاحيات المجموعات (نص دور الخادم: OWNER/ADMIN/MODERATOR/MEMBER) ──
// كانت الفحوص مبعثرة (require يدوي في updateRole + منطق واجهات) — أي دور جديد
// أو خطأ إملائي كان يفتح ثغرة. هذه الدوال الخالصة هي المرجع الوحيد.

/** تطبيع دور الخادم — غير المعروف يُعامل كعضو (أقل صلاحية). */
fun normalizeGroupRole(raw: String?): String = when (raw?.trim()?.uppercase()) {
    "OWNER" -> "OWNER"
    "ADMIN" -> "ADMIN"
    "MODERATOR" -> "MODERATOR"
    else -> "MEMBER"
}

/** هل الدور إدارة (مالك/مسؤول)؟ */
fun isGroupAdminRole(raw: String?): Boolean {
    val role = normalizeGroupRole(raw)
    return role == "OWNER" || role == "ADMIN"
}

/** هل يحق إدارة الأعضاء (إضافة/إزالة/حظر) مع مراعاة إعدادات المجموعة؟ */
fun canManageGroupMembers(raw: String?, settings: GroupSettings): Boolean {
    if (normalizeGroupRole(raw) == "OWNER") return true
    if (normalizeGroupRole(raw) == "ADMIN") return true
    // MODERATOR/MEMBER: يُسمح فقط عندما تفتح الإعدادات الدعوة للجميع.
    if (normalizeGroupRole(raw) == "MODERATOR") return !settings.onlyAdminsCanAddMembers
    return !settings.onlyAdminsCanAddMembers
}

/** هل يحق تعديل معلومات المجموعة؟ */
fun canEditGroupInfo(raw: String?, settings: GroupSettings): Boolean {
    if (normalizeGroupRole(raw) == "OWNER") return true
    if (normalizeGroupRole(raw) == "ADMIN") return true
    return !settings.onlyAdminsCanEditInfo
}

/** هل يحق الدعوة (رابط/رمز)؟ */
fun canInviteToGroup(raw: String?, settings: GroupSettings): Boolean {
    if (isGroupAdminRole(raw)) return true
    return !settings.onlyAdminsCanInvite
}

/**
 * دوري في المجموعة من قائمة الأعضاء + جلسة الخادم — المرجع الوحيد لحسابه
 * على العميل (كان كل منادٍ يمسح members يدويًا بصيغ مختلفة: redId مقابل
 * userId، حساسية حالة مختلفة). الأولوية لـ redId الجلسة، ثم ownerRedId.
 */
fun myGroupRole(group: Group, myRedId: String?): String {
    val me = myRedId?.trim().orEmpty()
    if (me.isNotEmpty()) {
        group.members.firstOrNull { it.redId.equals(me, ignoreCase = true) }?.let { return normalizeGroupRole(it.role) }
        if (group.ownerRedId.equals(me, ignoreCase = true)) return "OWNER"
    }
    return "MEMBER"
}
