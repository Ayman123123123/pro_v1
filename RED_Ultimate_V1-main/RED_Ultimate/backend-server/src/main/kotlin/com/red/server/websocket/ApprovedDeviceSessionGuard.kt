package com.red.server.websocket

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Revalidates a long-lived socket against current account and device approval state.
 * Uses a 5-second cache to avoid hitting the DB on every WebSocket frame (120/sec).
 *
 * نافذة الكاش 5s (cacheDurationMs=5_000، مفتاح accountId:deviceId):
 * أول إطار بعد انتهاء النافذة يقرأ DB (الحساب APPROVED + الجهاز APPROVED
 * لنفس المستخدم) ثم تُحفظ النتيجة 5s. الأثر: سحب الموافقة/تعطيل الجهاز
 * يُفعَّل على أي سوكت حي خلال ≤5s، وسوكت نشط يكلّف ≈12 قراءة DB/دقيقة
 * بدل قراءة لكل إطار.
 * (2026-09-24) مسار conference (/ws/conference): المصافحة محمية بـ
 * JwtHandshakeInterceptor، وإعادة التحقق لكل إطار إلزامية عبر
 * closeIfRevoked/isStillAuthorized (إغلاق POLICY_VIOLATION عند الرفض) —
 * كما في RedMasterHandler وLiveStreamWebSocketHandler.
 * usage: if (guard.closeIfRevoked(session)) return
 */
@Component
class ApprovedDeviceSessionGuard(
    private val users: UserAccountRepository,
    private val devices: UserDeviceRepository
) {
    // ذاكرة مؤقتة لمدة 5 ثوانٍ — تمنع 240 query/ثانية على WebSocket
    private data class CacheEntry(val authorized: Boolean, val expiresAt: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheDurationMs = 5_000L

    fun isStillAuthorized(accountId: String?, deviceId: String?): Boolean = runCatching {
        if (accountId == null || deviceId == null) return@runCatching false
        val cacheKey = "$accountId:$deviceId"
        val now = System.currentTimeMillis()
        val cached = cache[cacheKey]
        if (cached != null && cached.expiresAt > now) return@runCatching cached.authorized

        val account = UUID.fromString(accountId)
        val device = UUID.fromString(deviceId)
        val user = users.findById(account).orElse(null)
        val authorized = user != null &&
            user.status == AccountStatus.APPROVED &&
            devices.findByIdAndUserId(device, user.id)?.status == DeviceStatus.APPROVED
        cache[cacheKey] = CacheEntry(authorized, now + cacheDurationMs)
        authorized
    }.getOrDefault(false)

    /**
     * سطر واحد لمسارات conference وغيرها: يقرأ accountId/deviceId من سمات
     * الجلسة (يضعها JwtHandshakeInterceptor) ويغلق بـ POLICY_VIOLATION عند
     * انتفاء التفويض. يعيد true إن أُغلقت الجلسة فيُرجع المتصل فورًا.
     */
    fun closeIfRevoked(session: org.springframework.web.socket.WebSocketSession): Boolean {
        val ok = isStillAuthorized(
            session.attributes["accountId"] as? String,
            session.attributes["deviceId"] as? String
        )
        if (!ok) runCatching { session.close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION) }
        return !ok
    }
}
