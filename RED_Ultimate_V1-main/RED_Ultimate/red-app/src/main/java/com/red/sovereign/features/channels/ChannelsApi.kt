package com.red.sovereign.features.channels

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.ui.MESSAGE_SEARCH_MIN_LENGTH
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * ════════════════════════════════════════════════════════════════════════
 *  Channels API — Android client
 *  - Talks to /api/channels on the backend (ChannelController)
 *  - Real data only — no fixtures, no mock data
 * ════════════════════════════════════════════════════════════════════════
 *
 * P1-G: كان `features/channels` معزولًا تمامًا على العميل (سياسة بلا شبكة
 * ولا واجهة) و`ChannelService.searchCloudComplement` كودًا ميتًا على الخادم
 * لأن `GET /api/channels` لم يكن يقبل `search`. هذا الملف هو جسر الشبكة
 * الناقص بين الاثنين، بنمط CommunitiesApi نفسه (AuthorizedApiClient).
 *
 * offline-first: كل دوال البحث تُرجع المحلي عند أي فشل — لا استثناء ولا حجب.
 */

/** استجابة الخادم لـ ChannelResponse (createdAt نص ISO-8601 من Instant). */
@Serializable
data class Channel(
    val id: String,
    val name: String,
    val username: String? = null,
    val description: String? = null,
    val ownerId: String = "",
    val isPublic: Boolean = true,
    val subscriberCount: Int = 0,
    /** P1-G: وضع البث — الأدمن فقط ينشر. */
    val isBroadcast: Boolean = true,
    /** P1-G: Boosts lite — عدّاد فقط، بلا أي أصل مشفّر/NFT. */
    val boostsCount: Int = 0,
    val createdAt: String? = null
) {
    /** المستوى مشتق دائمًا من العدّاد — نفس دالة القاعدة على الخادم (boosts/5). */
    val level: Int get() = levelForBoosts(boostsCount)
}

@Serializable
data class CreateChannelBody(
    val name: String,
    val username: String? = null,
    val description: String? = null,
    val isPublic: Boolean = true,
    val isBroadcast: Boolean = true
)

/** مغلّف `GET /api/channels` — `{"channels":[…],"count":n}`. */
@Serializable
private data class ChannelListEnvelope(
    val channels: List<Channel> = emptyList(),
    val count: Int = 0
)

/** مغلّف `POST /api/channels` — `{"success":true,"channel":{…}}`. */
@Serializable
private data class ChannelCreateEnvelope(
    val success: Boolean = false,
    val channel: Channel? = null
)

/** نسخة عرض خفيفة للبحث — تُدمج مع نتائج المحلي بلا شبكة. */
fun Channel.toSearchItem(): ChannelSearchItem =
    ChannelSearchItem(id = id, name = name, username = username)

class ChannelsApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    /**
     * `GET /api/channels?limit=&search=`
     * - `search = null`: القائمة العامة (الأكثر مشتركين أولًا).
     * - `search` غير فارغ: بحث سحابي مكمّل على name/username/description.
     * الحد يُقيَّد 1..100 هنا أيضًا مطابقةً للخادم (listPublic/searchCloudComplement).
     */
    suspend fun list(search: String? = null, limit: Int = 20): ApiResult<List<Channel>> {
        val qs = buildString {
            append("?limit=").append(limit.coerceIn(1, 100))
            if (!search.isNullOrBlank()) {
                append("&search=").append(java.net.URLEncoder.encode(search.trim(), "UTF-8"))
            }
        }
        val raw = when (val r = client.request("GET", "/api/channels$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<ChannelListEnvelope>(raw).channels)
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    /**
     * بحث القنوات — المحلي أولًا والسحابي مكمّل فقط.
     *
     * هذه هي نقطة الدمج الوحيدة، فتبقى السياسة (حد أدنى حرفين + تجاهل الفشل
     * بصمت) في مكان واحد قابل للاختبار بدل تكرارها في كل شاشة:
     * - `query` أقصر من [MESSAGE_SEARCH_MIN_LENGTH] → لا شبكة إطلاقًا، يُعاد `local`.
     * - أي `ApiResult.Error` (بلا اتصال `OFFLINE`، 401، 429، 500) → يُعاد `local`.
     * - نجاح → `mergeChannelSearch(local, cloud)` (المحلي أولًا، بلا تكرار بالـ id).
     *
     * العميل يستدعيها بعد debounce موحّد (MESSAGE_SEARCH_DEBOUNCE_MS = 300ms)
     * داخل `collectLatest` حتى يُلغى الطلب السابق — نفس سياسة RedGlobalSearch.
     */
    suspend fun searchMerged(
        local: List<ChannelSearchItem>,
        query: String,
        limit: Int = 20
    ): List<ChannelSearchItem> {
        if (query.trim().length < MESSAGE_SEARCH_MIN_LENGTH) return local
        return when (val r = list(query, limit)) {
            is ApiResult.Success -> mergeChannelSearch(local, r.value.map { it.toSearchItem() })
            is ApiResult.Error -> local
        }
    }

    suspend fun create(body: CreateChannelBody): ApiResult<Channel> {
        val payload = json.encodeToString(body).toRequestBody()
        val raw = when (val r = client.requestBody("POST", "/api/channels", payload)) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            val env = json.decodeFromString<ChannelCreateEnvelope>(raw)
            val channel = env.channel
            when {
                channel != null -> ApiResult.Success(200, channel)
                // 200 بلا قناة = رفض منطقي من الخادم (اسم محجوز مثلًا).
                else -> ApiResult.Error(400, "CHANNEL_CREATE_REJECTED")
            }
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    /** تفاصيل قناة — `GET /api/channels/{id}` يعيد الكائن مباشرة (بلا مغلّف). */
    suspend fun details(id: String): ApiResult<Channel> {
        val raw = when (val r = client.request("GET", "/api/channels/$id")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<Channel>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    /** انضمام — `{"success":bool}`؛ الخادم يحدّ 20/دقيقة ويعيد 429 عند التجاوز. */
    suspend fun join(id: String): ApiResult<String> =
        client.request("POST", "/api/channels/$id/join")

    suspend fun leave(id: String): ApiResult<String> =
        client.request("POST", "/api/channels/$id/leave")
}
