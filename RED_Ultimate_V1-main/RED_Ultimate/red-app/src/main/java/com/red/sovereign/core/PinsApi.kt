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
 * - TODO(push): علّق polling كل 30s في ChatsScreen/RedDashboard
 *   (LaunchedEffect(groupConversationId) { while(...) { listForGroup(); delay(30_000) } })
 *   واستبدله بـ push عبر GROUP_SYNC (GroupSyncBus.events):
 *     • الخادم يبث GROUP_SYNC عند pin/unpin في مجموعة → needRefresh(groupId)
 *     • الواجهة تجمع lifecycle-aware فقط أثناء العرض:
 *       repeatOnLifecycle(RESUMED) { GroupSyncBus.events.collect { if (it == groupId) refresh() } }
 *       + جلبة أولية واحدة عند الفتح، بلا حلقة while/delay.
 *   إن بقي polling مؤقتاً: قلّل المدة فقط أثناء فتح شاشة المجموعة وأوقفه في onDispose
 *   (lifecycle-aware) — لا polling في الخلفية.
 */
class PinsApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

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
                .let { if (it.isSuccess) ApiResult.Success(r.code, it.getOrNull()!!) else ApiResult.Error(r.code, "PARSE_ERROR") }
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