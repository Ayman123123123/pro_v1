package com.red.sovereign.media

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * واجهة برمجية للملصقات — تتكلم مع /api/admin/content/sticker-packs.
 * الملصقات سيادية: حزم محلية يديرها المسؤول (لا GIPHY/سحابة).
 */
class StickerApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** الحزم المنشورة المتاحة لكل المستخدمين. */
    suspend fun getPublishedPacks(): ApiResult<List<StickerPackDto>> =
        fetchList("/api/admin/content/sticker-packs/published")

    /** الملصقات الفردية داخل حزمة. */
    suspend fun getStickersInPack(packId: String): ApiResult<List<StickerDto>> =
        fetchList("/api/admin/content/sticker-packs/$packId/stickers")

    /** حزم المستخدم المثبّتة. */
    suspend fun getInstalledPacks(): ApiResult<List<StickerPackDto>> =
        fetchList("/api/admin/content/sticker-packs/installed")

    /** يثبّت حزمة. */
    suspend fun install(packId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        when (val r = client.request("POST", "/api/admin/content/sticker-packs/$packId/install")) {
            is ApiResult.Success -> ApiResult.Success(r.code, Unit)
            is ApiResult.Error -> r
        }
    }

    /** يُلغي تثبيت حزمة. */
    suspend fun uninstall(packId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        when (val r = client.request("DELETE", "/api/admin/content/sticker-packs/$packId/install")) {
            is ApiResult.Success -> ApiResult.Success(r.code, Unit)
            is ApiResult.Error -> r
        }
    }

    private suspend fun <T> fetchList(path: String): ApiResult<List<T>> = withContext(Dispatchers.IO) {
        when (val r = client.request("GET", path)) {
            is ApiResult.Success -> runCatching { json.decodeFromString<List<T>>(r.value) }
                .let { if (it.isSuccess) ApiResult.Success(r.code, it.getOrNull().orEmpty()) else ApiResult.Error(r.code, "PARSE_ERROR") }
            is ApiResult.Error -> r
        }
    }
}

@Serializable
data class StickerPackDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val coverMediaKey: String = "",
    val previewMediaKey: String? = null,
    val stickerCount: Int = 0,
    val isOfficial: Boolean = false,
    val isFree: Boolean = true
)

@Serializable
data class StickerDto(
    val id: String,
    val packId: String,
    val name: String? = null,
    val mediaKey: String,
    val emojiTags: List<String> = emptyList(),
    val displayOrder: Int = 0
)

/** حمولة رسالة ملصق — تُرسل كنوع STICKER (JSON مشفّر E2EE). */
@Serializable
data class StickerMessagePayload(
    val mediaKey: String,
    val emoji: String,
    val name: String? = null
)
