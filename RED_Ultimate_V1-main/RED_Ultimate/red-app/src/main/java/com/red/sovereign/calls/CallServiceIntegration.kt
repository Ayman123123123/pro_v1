package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import com.red.sovereign.auth.TokenStore

/**
 * مدير تكامل خدمات المكالمات — Call Service Integration Manager
 *
 * يدير تفاعل الخدمات المختلفة (YounesCallService, GroupCallService,
 * ConferenceService, LiveStreamService) مع بعضها البعض ومع النظام.
 *
 * تصحيح شامل: كانت كل الدوال تبني Intents بأسماء ثوابت غير موجودة
 * (EXTRA_CALL_ID/EXTRA_PEER/ACTION_DIAL…). الآن تمرّ كلها عبر مصانع
 * الـ companion الحقيقية لكل خدمة، وهي المسار الذي تستخدمه بقية الواجهات.
 */
object CallServiceIntegration {

    /**
     * بدء مكالمة فردية
     *
     * تصحيح: YounesCallService لا يقرأ callId من الـ Intent — يُولّده بنفسه
     * (UUID) عند ACTION_START، وأسماء الحقول الحقيقية هي EXTRA_TARGET
     * و EXTRA_MODE. لذا [callId] يبقى في التوقيع للتوافق لكنه غير مُستخدم.
     */
    fun startCall(context: Context, callId: String, peer: String, isVideo: Boolean) {
        YounesCallService.start(context, peer, isVideo)
    }

    /**
     * إنهاء مكالمة فردية
     */
    fun endCall(context: Context, callId: String) {
        YounesCallService.action(context, YounesCallService.ACTION_END)
    }

    /**
     * بدء مكالمة جماعية
     *
     * تصحيح: الإجراء الحقيقي هو ACTION_START_GROUP_CALL والمدعوون يُمرّرون
     * في EXTRA_INVITEE_IDS (لا EXTRA_MEMBERS). الخدمة تحتاج أيضاً هويتي
     * (EXTRA_MY_USER_ID) لتثبيت مسار الـ Mesh — مصدرها الحقيقي [TokenStore].
     * أسماء العرض تُترك فارغة لأن الخدمة ترجع تلقائياً إلى المعرّف.
     */
    fun startGroupCall(
        context: Context,
        groupId: String,
        groupName: String,
        isVideo: Boolean,
        members: List<String>
    ) {
        val tokens = TokenStore(context)
        GroupCallService.startGroupCall(
            context = context,
            myUserId = tokens.redId.orEmpty(),
            inviteeIds = members,
            inviteeNames = emptyList(),
            isVideo = isVideo,
            hostName = tokens.username.orEmpty(),
            groupId = groupId,
            groupName = groupName
        )
    }

    /**
     * إنهاء مكالمة جماعية
     */
    fun endGroupCall(context: Context, groupId: String) {
        GroupCallService.end(context)
    }

    /**
     * بدء مؤتمر
     *
     * تصحيح: ConferenceService لا يعرف ACTION_START — الانضمام يتم بـ
     * ACTION_JOIN (عبر [ConferenceService.join])، والحقل الحقيقي للفيديو هو
     * EXTRA_VIDEO. من يبدأ المؤتمر هو المضيف، لذا asHost = true حتى تُسجَّل
     * الغرفة على الخادم قبل الانضمام.
     */
    fun startConference(context: Context, roomId: String, isVideo: Boolean) {
        ConferenceService.join(
            context = context,
            roomId = roomId,
            userId = TokenStore(context).redId.orEmpty(),
            video = isVideo,
            asHost = true
        )
    }

    /**
     * مغادرة مؤتمر
     */
    fun leaveConference(context: Context) {
        ConferenceService.leave(context)
    }

    /**
     * بدء بث مباشر
     *
     * تصحيح: معرّف البث الحقيقي هو EXTRA_STREAM_ID (لا EXTRA_ROOM_ID)، ولا
     * يملك مسار البدء أي مفتاح صوت/فيديو إطلاقاً — المُبثّ يبدأ دائماً بـ
     * CallMediaKind.LIVE. المفتاح الحقيقي الوحيد هو ACTION_TOGGLE_AUDIO_ONLY
     * وهو يعمل على محرك موجود (يُنشأ لاحقاً في onConnected)، فإرساله هنا كان
     * سيضبط علم الواجهة فقط والكاميرا تبقى مفتوحة — حالة كاذبة. لذلك
     * [isVideo] بلا مسند حقيقي في البدء، والتبديل يبقى بيد الواجهة بعد الاتصال.
     */
    fun startLiveStream(context: Context, roomId: String, isVideo: Boolean) {
        LiveStreamService.start(
            context = context,
            streamId = roomId,
            userId = TokenStore(context).redId.orEmpty(),
            isBroadcaster = true
        )
    }

    /**
     * إيقاف البث المباشر
     */
    fun stopLiveStream(context: Context) {
        LiveStreamService.stop(context)
    }

    /**
     * التحقق من وجود مكالمة نشطة
     *
     * تصحيح: لا خدمة من الخدمات تعرض دالة "هل نشطة" — الحالة الحقيقية تعيش في
     * كائنات الـ Runtime (CallRuntime / GroupCallRuntime / ConferenceRuntime)
     * ولا تحتاج Context. [context] يبقى في التوقيع للتوافق فقط.
     */
    fun hasActiveCall(context: Context): Boolean {
        return isOneToOneActive() || isGroupActive() || isConferenceActive()
    }

    /**
     * الحصول على نوع المكالمة النشطة
     */
    fun getActiveCallType(context: Context): String? {
        return when {
            isOneToOneActive() -> "1to1"
            isGroupActive() -> "group"
            isConferenceActive() -> "conference"
            else -> null
        }
    }

    /** مكالمة فردية قائمة — أي حالة غير Idle وغير نهائية في [CallRuntime.state]. */
    private fun isOneToOneActive(): Boolean =
        CallRuntime.state !is CallUiState.Idle && !CallUiState.isTerminal(CallRuntime.state)

    /** مكالمة جماعية قائمة — Ringing/IncomingGroup/Active في [GroupCallRuntime.state]. */
    private fun isGroupActive(): Boolean = when (GroupCallRuntime.state) {
        is GroupCallUiState.Idle, is GroupCallUiState.Ended -> false
        else -> true
    }

    /** مؤتمر أو مساحة قائمة — Incoming/Connecting/Active في [ConferenceRuntime.state]. */
    private fun isConferenceActive(): Boolean = when (ConferenceRuntime.state) {
        is ConferenceUiState.Idle, is ConferenceUiState.Error -> false
        else -> true
    }

    /**
     * إرسال إجراء لجميع الخدمات النشطة
     *
     * تصحيح: كان المعامل يُسمّى `action` فيظلّل خاصية `Intent.action` داخل
     * الـ apply (val cannot be reassigned)، وكانت القيمة الإضافية من نوع Any
     * وهو نوع لا يقبله putExtra. الآن اسم المعامل [act] والقيمة String،
     * والبثّ محصور بحزمة التطبيق حتى لا تصل الحقول لتطبيقات أخرى.
     */
    fun broadcastAction(context: Context, act: String, extraKey: String? = null, extraValue: String? = null) {
        val intent = Intent(act).apply {
            setPackage(context.packageName)
            extraKey?.let { putExtra(it, extraValue ?: "") }
        }
        context.sendBroadcast(intent)
    }
}
