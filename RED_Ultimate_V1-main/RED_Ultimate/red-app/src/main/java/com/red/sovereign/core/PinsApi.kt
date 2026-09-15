package com.red.sovereign.core

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * تثبيت رسائل المجموعات — مزامنة مع خادم يونس:
 * POST/DELETE/GET /api/messages/pins (مثبتة لكل مجموعة، تظهر لكل الأعضاء).
 *
 * P0-D:
 * - مدة التثبيت الافتراضية 7 أيام ([DEFAULT_PIN_EXPIRES_SECONDS]) — تُمرر دائماً
 *   للخادم حتى لو نسي المتصل تمرير expiresInSeconds (قيمة null تعني 7 أيام).
 * - التحديث الدوري مشروط عبر [PinsPollPolicy] + [shouldFetch]/[recordResult]:
 *   تخطَّ الجلبة عندما تكون الواجهة غير معروضة (isForeground=false) أو عندما
 *   يكون النقل الحي يبث GROUP_SYNC (transportAlive=true — الـ push يكفي)،
 *   واعتمد [GroupSyncBus.events] + جلبة أولية واحدة عند الفتح بلا حلقة خلفية.
 *   عند الفشل المتكرر يُطبق باك-أوف أُسّي عبر [PinsPollPolicy.nextDelay].
 */
object PinsPollPolicy {
    /** الفاصل الأساسي بين الجلبات الدورية (30s). */
    const val BASE_INTERVAL_MS: Long = 30_000L
    /** سقف الباك-أوف عند الأخطاء المتكررة. */
    const val MAX_INTERVAL_MS: Long = 300_000L

    /** هل يُسمح بجلبة دورية الآن؟ خلفية أو نقل حي نشط = تخطَّ. */
    fun shouldPoll(isForeground: Boolean, transportAlive: Boolean): Boolean {
        if (!isForeground) return false
        if (transportAlive) return false
        return true
    }

    /** باك-أوف أُسّي: 30s, 60s, 120s ... حتى السقف. */
    fun nextDelay(consecutiveErrors: Int): Long {
        if (consecutiveErrors <= 0) return BASE_INTERVAL_MS
        val shift = consecutiveErrors.coerceAtMost(4)
        return (BASE_INTERVAL_MS shl shift).coerceAtMost(MAX_INTERVAL_MS)
    }
}

class PinsApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val lastFetchAt = mutableMapOf<String, Long>()
    private var consecutiveErrors: Int = 0

    /** فحص مشروط قبل listForGroup: يحترم الواجهة/النقل + throttle لكل مجموعة. */
    fun shouldFetch(
        groupId: String,
        nowMs: Long = System.currentTimeMillis(),
        minIntervalMs: Long = PinsPollPolicy.BASE_INTERVAL_MS,
        isForeground: Boolean = true,
        transportAlive: Boolean = false
    ): Boolean {
        if (!PinsPollPolicy.shouldPoll(isForeground, transportAlive)) return false
        val last = lastFetchAt[groupId] ?: return true
        return (nowMs - last) >= minIntervalMs
    }

    /** يُستدعى بعد كل جلبة لتحديث throttle وحساب التأخير التالي (باك-أوف عند الفشل). */
    fun recordResult(groupId: String, success: Boolean, nowMs: Long = System.currentTimeMillis()): Long {
        if (success) {
            consecutiveErrors = 0
            lastFetchAt[groupId] = nowMs
            return PinsPollPolicy.BASE_INTERVAL_MS
        }
        consecutiveErrors++
        return PinsPollPolicy.nextDelay(consecutiveErrors)
    }

    companion object {
        /** مدة التثبيت الافتراضية المُرسلة للخادم عند التثبيت (7 أيام بالثواني). */
        const val DEFAULT_PIN_EXPIRES_SECONDS: Long = 7 * 24 * 3600L
    }

    suspend fun pin(messageUuid: String, groupId: String? = null, conversationId: String? = null, expiresInSeconds: Long? = DEFAULT_PIN_EXPIRES_SECONDS): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(
            PinRequest.serializer(),
            PinRequest(messageUuid = messageUuid, conversationId = conversationId, groupId = groupId, expiresInSeconds = expiresInSeconds)
        )
        when (val r = client.request("POST", "/api/messages/pins", body)) {
            is ApiResult.Success -> ApiResult.Success(r.code, Unit)
            is ApiResult.Error -> r
        }
    }

    suspend fun unpin(messageUuid: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        when (val r = client.request("DELETE", "/api/messages/pins/$messageUuid")) {
            is ApiResult.Success -> ApiResult.Success(r.code, Unit)
            is ApiResult.Error -> r
        }
    }

    /** معرفات الرسائل المثبتة في مجموعة (تعرض أعلى محادثة المجموعة لكل عضو). */
    suspend fun listForGroup(groupId: String): ApiResult<List<PinDto>> = withContext(Dispatchers.IO) {
        when (val r = client.request("GET", "/api/messages/pins?groupId=$groupId")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<PinListResponse>(r.value).pins }
                .let { parsed -> val v = parsed.getOrNull(); if (parsed.isSuccess && v != null) ApiResult.Success(r.code, v) else { java.util.logging.Logger.getLogger("PinsApi").warning("pins parse failed code=${r.code}"); ApiResult.Error(r.code, "PARSE_ERROR") } }
            is ApiResult.Error -> r
        }
    }
}

@Serializable
data class PinRequest(
    val messageUuid: String,
    val conversationId: String? = null,
    val groupId: String? = null,
    val channelId: String? = null,
    val expiresInSeconds: Long? = null
)

@Serializable
data class PinDto(
    val id: String = "",
    val messageUuid: String,
    val conversationId: String? = null,
    val groupId: String? = null,
    val channelId: String? = null,
    val pinnedBy: String = "",
    val pinnedAt: String? = null,
    val expiresAt: String? = null
)

@Serializable
data class PinListResponse(
    val pins: List<PinDto> = emptyList(),
    val count: Int = 0
)