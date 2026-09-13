package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.core.database.LocalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * مدير تكامل مديري المكالمات — Call Manager Integration
 *
 * يوحّد جميع مديري الميزات (CallQualityManager, CallPerformanceManager,
 * CallCaptionManager, etc.) في واجهة واحدة سهلة الاستخدام.
 */
object CallManagerIntegration {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * تهيئة جميع مديري المكالمات
     *
     * تصحيح: كان السطر الأول ينادي `CallQualityManager.clear()` وهي غير موجودة —
     * ذلك المدير لا يعرض أي واجهة تصفير (`lastStats` بـ private set، والكاتب
     * العام الوحيد هو `update(...)` لأن المُصنِّف داخله خاص بقصد). لذلك نُصفّر
     * قياسه بأصفار عبر واجهته الحقيقية: تختفي أرقام المكالمة السابقة من
     * الواجهة. ملاحظة سلوكية: قياس bitrate=0 يُصنَّف POOR (لا GOOD كالحالة
     * الابتدائية)، وهو فرق يُصحّحه أول استقصاء فعلي فوراً (pollStats).
     */
    fun initialize(context: Context) {
        // تهيئة مدير جودة المكالمة
        CallQualityManager.update(rttMs = 0, packetLoss = 0f, bitrateKbps = 0, fps = 30)

        // تهيئة مدير الأداء
        CallPerformanceManager.clearStats()

        // تهيئة مدير الانتظار
        CallWaitingManager.clearAll()

        // تهيئة مدير التحويل
        CallTransferManager.clearState()
    }

    /**
     * تحديث جودة المكالمة
     */
    fun updateCallQuality(rttMs: Int, packetLoss: Float, bitrateKbps: Int, fps: Int = 30) {
        CallQualityManager.update(rttMs, packetLoss, bitrateKbps, fps)
        CallPerformanceManager.updateStats(rttMs, packetLoss, bitrateKbps, fps)
    }

    /**
     * التحقق من الحاجة لإيقاف الفيديو
     */
    fun shouldDisableVideo(): Boolean {
        return CallPerformanceManager.shouldDisableVideo()
    }

    /**
     * الحصول على معدل البت الموصى به
     */
    fun getRecommendedBitrate(): Int {
        return CallPerformanceManager.getAdaptiveBitrate()
    }

    /**
     * التحقق من وجود مكالمة في الانتظار
     */
    fun hasWaitingCall(): Boolean {
        return CallWaitingManager.hasWaitingCall()
    }

    /**
     * تعيين مكالمة في الانتظار
     */
    fun setWaitingCall(callId: String, peer: String, isVideo: Boolean) {
        CallWaitingManager.setWaitingCall(
            CallWaitingManager.WaitingCallInfo(
                callId = callId,
                peer = peer,
                isVideo = isVideo,
                callType = "voice"
            )
        )
    }

    /**
     * قبول المكالمة في الانتظار
     */
    fun acceptWaitingCall() {
        CallWaitingManager.acceptWaitingCall()
    }

    /**
     * رفض المكالمة في الانتظار
     */
    fun rejectWaitingCall() {
        CallWaitingManager.rejectWaitingCall()
    }

    /**
     * بدء تحويل المكالمة
     */
    fun initiateTransfer(callId: String, targetId: String, isAttended: Boolean = false) {
        CallTransferManager.initiateTransfer(
            callId = callId,
            targetId = targetId,
            type = if (isAttended) CallTransferManager.TransferType.ATTENDED else CallTransferManager.TransferType.BLIND
        )
    }

    /**
     * إلغاء تحويل المكالمة
     */
    fun cancelTransfer() {
        CallTransferManager.cancelTransfer()
    }

    /**
     * حفظ سجل المكالمات محلياً
     */
    fun saveCallLog(context: Context, call: CallHistoryItem) {
        scope.launch {
            val repository = LocalRepository(context)
            repository.saveCallLog(call.toCallLogEntity())
        }
    }

    /**
     * الحصول على سجل المكالمات
     */
    suspend fun getCallLogs(context: Context): List<CallHistoryItem> {
        return withContext(Dispatchers.IO) {
            val repository = LocalRepository(context)
            repository.getCallLogs().first().map { it.toCallHistoryItem() }
        }
    }

    /**
     * مسح سجل المكالمات
     */
    fun clearCallLogs(context: Context) {
        scope.launch {
            val repository = LocalRepository(context)
            repository.clearCallLogs()
        }
    }
}

/**
 * تحويل CallLogEntity إلى CallHistoryItem
 */
fun com.red.sovereign.core.database.CallLogEntity.toCallHistoryItem(): CallHistoryItem {
    return CallHistoryItem(
        id = id,
        peerId = id, // In real implementation, decrypt using CallLogCipher
        peerLabel = "",
        direction = direction,
        type = type,
        route = route,
        status = status,
        startedAt = timestamp.toString(),
        answeredAt = answeredAt?.toString(),
        endedAt = endedAt?.toString()
    )
}

/**
 * تحويل CallHistoryItem إلى CallLogEntity
 *
 * تصحيح: كانت الطوابع تُغلَّف بـ `java.sql.Timestamp(...)` بينما حقول
 * [com.red.sovereign.core.database.CallLogEntity] هي `Long`/`Long?` (ميلي ثانية
 * عن الحقبة، وهي وحدة كل من يكتب في نفس الجدول: YounesCallService و
 * GroupCallService و ConferenceService). [parseCallTimestamp] يُعيد أصلاً ميلي
 * ثانية، فالتغليف كان زائداً ومُخالفاً للنوع.
 */
fun CallHistoryItem.toCallLogEntity(): com.red.sovereign.core.database.CallLogEntity {
    return com.red.sovereign.core.database.CallLogEntity(
        id = id,
        peerId = peerId,
        peerLabel = peerLabel,
        type = type,
        direction = direction,
        route = route,
        status = status,
        timestamp = parseCallTimestamp(startedAt) ?: 0L,
        answeredAt = parseCallTimestamp(answeredAt),
        endedAt = parseCallTimestamp(endedAt)
    )
}
