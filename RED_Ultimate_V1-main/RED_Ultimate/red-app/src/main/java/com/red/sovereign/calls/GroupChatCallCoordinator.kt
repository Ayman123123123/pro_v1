package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.groups.Group

/**
 * مدخل مكالمات **مجموعة الدردشة** فقط.
 *
 * لا ينشئ هذا الكائن مؤتمراً أو مساحة مستقلة. يختار أعضاء المجموعة الفعليين،
 * ويربط جلسة المكالمة المؤقتة بمعرّف المجموعة كي يفرض الخادم العضوية عند الدعوة.
 */
object GroupChatCallCoordinator {
    sealed interface StartResult {
        data object Started : StartResult
        data object MissingIdentity : StartResult
        data object NoOtherMembers : StartResult
        /** صلاحية المجموعة تحصر بدء المكالمات بالمشرفين. */
        data object NotAllowedBySettings : StartResult
    }

    fun start(
        context: Context,
        group: Group,
        ownRedId: String,
        video: Boolean
    ): StartResult {
        if (ownRedId.isBlank()) return StartResult.MissingIdentity
        // احترام onlyAdminsCanCall — كان الزر يتجاهله ويبدأ المكالمة لأي عضو.
        if (group.settings.onlyAdminsCanCall) {
            val myRole = group.members.firstOrNull { it.redId.equals(ownRedId, ignoreCase = true) }?.role?.uppercase()
            if (myRole != "OWNER" && myRole != "ADMIN") return StartResult.NotAllowedBySettings
        }
        val invitees = group.members.filter {
            it.redId.isNotBlank() && !it.redId.equals(ownRedId, ignoreCase = true)
        }
        if (invitees.isEmpty()) return StartResult.NoOtherMembers

        GroupCallService.startGroupCall(
            context = context,
            myUserId = ownRedId,
            inviteeIds = invitees.map { it.redId },
            inviteeNames = invitees.map { it.username.ifBlank { it.redId } },
            isVideo = video,
            hostName = ownRedId,
            groupId = group.id,
            groupName = group.name
        )
        return StartResult.Started
    }

    fun startZoom(
        context: Context,
        group: Group,
        ownRedId: String,
        video: Boolean,
        title: String = "اجتماع ${group.name}"
    ): StartResult {
        if (ownRedId.isBlank()) return StartResult.MissingIdentity
        if (group.settings.onlyAdminsCanCall) {
            val myRole = group.members.firstOrNull { it.redId.equals(ownRedId, ignoreCase = true) }?.role?.uppercase()
            if (myRole != "OWNER" && myRole != "ADMIN") return StartResult.NotAllowedBySettings
        }
        val invitees = group.members.filter {
            it.redId.isNotBlank() && !it.redId.equals(ownRedId, ignoreCase = true)
        }
        if (invitees.isEmpty()) return StartResult.NoOtherMembers

        ZoomGroupCallService.startZoom(
            context = context,
            myUserId = ownRedId,
            inviteeIds = invitees.map { it.redId },
            inviteeNames = invitees.map { it.username.ifBlank { it.redId } },
            isVideo = video,
            title = title,
            hostName = ownRedId
        )
        return StartResult.Started
    }
}
