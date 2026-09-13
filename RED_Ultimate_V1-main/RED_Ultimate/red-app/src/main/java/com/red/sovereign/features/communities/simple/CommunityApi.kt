package com.red.sovereign.features.communities.simple

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.serialization.json.Json

/**
 * P1-B — عميل مجتمعات بسيط (قراءة + انضمام/مغادرة فقط).
 *
 * - `GET /api/communities` (بحث + ترقيم صفحات اختياري)
 * - `POST /api/communities/{id}/join`
 * - `POST /api/communities/{id}/leave`
 *
 * بيانات حقيقية فقط — لا بدائل وهمية ولا mock.
 * (يوجد عميل كامل المواصفات في `CommunitiesApi.kt` بنفس المجلد الأب؛
 * هذا العميل نسخة P1-B البسيطة في حزمة `simple` ولا يمس القديم.)
 */
class CommunityApi(private val client: AuthorizedApiClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    suspend fun list(search: String? = null, page: Int = 0, size: Int = 30): ApiResult<List<Community>> {
        val qs = buildString {
            append("?page=").append(page.coerceAtLeast(0))
                .append("&size=").append(size.coerceIn(1, 100))
            if (!search.isNullOrBlank()) {
                append("&search=").append(java.net.URLEncoder.encode(search.trim(), "UTF-8"))
            }
        }
        val raw = when (val r = client.request("GET", "/api/communities$qs")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<List<Community>>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun join(id: String): ApiResult<Community> {
        val raw = when (val r = client.request("POST", "/api/communities/$id/join")) {
            is ApiResult.Success -> r.value
            is ApiResult.Error -> return r
        }
        return try {
            ApiResult.Success(200, json.decodeFromString<Community>(raw))
        } catch (e: Exception) {
            ApiResult.Error(500, e.message.orEmpty())
        }
    }

    suspend fun leave(id: String): ApiResult<String> =
        client.request("POST", "/api/communities/$id/leave")
}
