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
    }

    fun start(
        context: Context,
        group: Group,
        ownRedId: String,
        video: Boolean
    ): StartResult {
        if (ownRedId.isBlank()) return StartResult.MissingIdentity
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
            groupId = group.id
        )
        return StartResult.Started
    }
}
