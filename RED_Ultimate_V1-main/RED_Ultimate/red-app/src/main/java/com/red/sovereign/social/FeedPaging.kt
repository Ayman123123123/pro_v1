package com.red.sovereign.social

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow

/**
 * ── Feed Paging3 (2026-09-10 · Stories/Feed) ──
 *
 * كان الموجز يحمّل دفعة واحدة limit=20 عبر `FeedApi.load(scope)` بلا ترقيم
 * فعلي: التمرير الطويل يعيد `load()` كاملًا (وميض + استهلاك)، ولا توجد
 * `collectAsLazyPagingItems` رغم وجود الاعتمادين في build.gradle.
 *
 * هذا المصدر يستخدم cursor الخادم (`FeedResponse.nextCursor` عبر `before=`)
 * كمفتاح صفحة — نفس عقد `FeedApi.load(scope, cursor)`.
 * المفتاح String? لا Int: null للصفحة الأولى، وnextCursor للتالية.
 */
class FeedPagingSource(
    private val api: FeedApi,
    private val scope: String?
) : PagingSource<String, Post>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Post> {
        return try {
            when (val result = api.load(scope, cursor = params.key)) {
                is com.red.sovereign.auth.ApiResult.Success -> {
                    val page = result.value
                    LoadResult.Page(
                        data = page.posts,
                        prevKey = null, // الموجز أحادي الاتجاه: الأحدث → الأقدم فقط
                        nextKey = page.nextCursor?.ifBlank { null }
                    )
                }
                is com.red.sovereign.auth.ApiResult.Error ->
                    LoadResult.Error(IllegalStateException(result.message))
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, Post>): String? = null
}

/** تدفق صفحات الموجز — 20 عنصرًا للصفحة (مطابق limit الخادم)، بلا placeholders. */
fun feedPager(api: FeedApi, scope: String?): Flow<PagingData<Post>> = Pager(
    config = PagingConfig(pageSize = 20, enablePlaceholders = false, initialLoadSize = 20),
    pagingSourceFactory = { FeedPagingSource(api, scope) }
).flow
