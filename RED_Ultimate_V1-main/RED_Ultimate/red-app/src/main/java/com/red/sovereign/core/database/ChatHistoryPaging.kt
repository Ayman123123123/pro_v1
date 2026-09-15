package com.red.sovereign.core.database

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow

/**
 * Paging3 لسجل المحادثة (2026-09-10).
 *
 * كان الاعتمادان `paging-runtime` و`paging-compose` في `build.gradle.kts`
 * بلا أي استخدام — هذا الملف يفعّل `Pager` فعليًا لسجل المحادثة بدل
 * تحميل `local_history` كاملًا عبر `Flow<List<…>>`.
 *
 * التكامل مع الواجهة (عند الحاجة): `collectAsLazyPagingItems()` من
 * `paging-compose` في `LazyColumn` مع `key = { it.id }`.
 *
 * P0-C (2026-09-12): `ChatHistoryPagingColumn` هو المسار الافتراضي لعرض
 * السجل — المسار القديم `getLocalHistory(): Flow<List>` يبقى للتوافق
 * (استعادة سريعة/بحث) فقط.
 */

/** حجم الصفحة الافتراضي لسجل المحادثة (P0-C 2026-09-12: مثبت 30). */
const val CHAT_HISTORY_PAGE_SIZE = 30

/** سعة كاش النص المفكوك LRU — يُجنّب إعادة فك protobuf/UTF-8 كل تركيب. */
const val CHAT_HISTORY_DECRYPTED_CACHE_SIZE = 200

class ChatHistoryPagingSource(
    private val dao: RedDao,
    private val conversationId: String
) : PagingSource<Int, LocalHistoryEntity>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LocalHistoryEntity> {
        val offset = params.key ?: 0
        return try {
            val page = dao.getLocalHistoryPage(conversationId, params.loadSize, offset)
            LoadResult.Page(
                data = page,
                prevKey = null, // السجل يُقرأ من الأحدث للأقدم فقط
                nextKey = if (page.size < params.loadSize) null else offset + page.size
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    // LEGENDARY FIX: مفتاح تحديث صحيح لـ OFFSET (كان prevKey+pageSize يكسر الموضع عند التدوير)
    override fun getRefreshKey(state: PagingState<Int, LocalHistoryEntity>): Int? =
        state.anchorPosition?.let { anchor ->
            val anchorItem = state.closestItemToPosition(anchor) ?: return null
            val page = state.closestPageToPosition(anchor) ?: return null
            val anchorOffset = page.data.indexOfFirst { it.id == anchorItem.id }.takeIf { it >= 0 } ?: 0
            ((page.nextKey ?: page.prevKey ?: 0) - page.data.size + anchorOffset).coerceAtLeast(0)
        }
}

/** تدفق صفحات سجل محادثة واحدة — الأحدث أولًا، 30 عنصرًا للصفحة. */
fun chatHistoryPager(
    dao: RedDao,
    conversationId: String,
    pageSize: Int = CHAT_HISTORY_PAGE_SIZE
): Flow<PagingData<LocalHistoryEntity>> = Pager(
    config = PagingConfig(
        pageSize = pageSize,
        prefetchDistance = pageSize / 2,
        initialLoadSize = pageSize * 2,
        // كاش صفحات محدود: ~200 عنصر + صفحة — يمنع نمو الذاكرة بلا حد
        // في المحادثات الطويلة (يُكمّله كاش المفكوك في الـ ViewModel).
        maxSize = CHAT_HISTORY_DECRYPTED_CACHE_SIZE + pageSize,
        enablePlaceholders = false
    ),
    pagingSourceFactory = { ChatHistoryPagingSource(dao, conversationId) }
).flow

/**
 * ViewModel ناحل لـ Paging3 (2026-09-10): يخزّن تدفق `PagingData` عبر
 * `cachedIn(viewModelScope)` حتى لا يُعاد إنشاء `Pager` مع كل تركيب.
 * يُستخدم في `ChatHistoryPagingColumn` داخل `ChatThreadScreen.kt`:
 * `val items = vm.pager(convId).collectAsLazyPagingItems()`.
 *
 * P0-C (2026-09-12): هذا هو المسار الافتراضي — `cachedIn(viewModelScope)`
 * إلزامي هنا (لا في الـ Composable) حتى يبقى الـ Pager حيًا عبر تدوير
 * الشاشة. + كاش مفكوك LRU بسعة [CHAT_HISTORY_DECRYPTED_CACHE_SIZE]
 * + hook فهرسة FTS عبر [saveAndIndex].
 */
class ChatHistoryPagingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalRepository(application)
    private var currentConversation: String? = null
    private var currentFlow: Flow<PagingData<LocalHistoryEntity>>? = null
    private val pagerLock = Any()

    /** كاش LRU للنص المفكوك (id → نص) — 200 مدخل، آمن للخيوط عبر pagerLock. */
    private val decryptedCache = object : LinkedHashMap<String, String>(
        CHAT_HISTORY_DECRYPTED_CACHE_SIZE + 16, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > CHAT_HISTORY_DECRYPTED_CACHE_SIZE
    }

    fun pager(conversationId: String, pageSize: Int = CHAT_HISTORY_PAGE_SIZE): Flow<PagingData<LocalHistoryEntity>> {
        synchronized(pagerLock) {
            if (conversationId != currentConversation || currentFlow == null) {
                currentConversation = conversationId
                // الافتراضي: تدفق واحد مُخزّن في viewModelScope لكل محادثة.
                currentFlow = repository.chatHistoryPager(conversationId, pageSize)
                    .cachedIn(viewModelScope)
            }
            return requireNotNull(currentFlow) {
                "ChatHistoryPaging not initialized for conversation=$conversationId"
            }
        }
    }

    /**
     * نص العرض المفكوك مع كاش LRU (P0-C): يفك RICH_TEXT عبر RichMessage
     * مرة واحدة لكل id بدل كل تركيب — القائمة تُعيد التركيب عشرات المرات
     * أثناء التمرير.
     */
    fun decryptedText(entity: LocalHistoryEntity): String {
        synchronized(pagerLock) {
            decryptedCache[entity.id]?.let { return it }
        }
        val decoded = if (entity.messageType == "RICH_TEXT") {
            runCatching { com.red.sovereign.core.RichMessage.decode(entity.encryptedPlaintext)?.text }
                .getOrNull()?.takeIf { it.isNotEmpty() }
                ?: runCatching { entity.encryptedPlaintext.toString(Charsets.UTF_8) }.getOrDefault("")
        } else {
            runCatching { entity.encryptedPlaintext.toString(Charsets.UTF_8) }.getOrDefault("")
        }
        synchronized(pagerLock) {
            decryptedCache[entity.id] = decoded
        }
        return decoded
    }

    /**
     * حفظ + فهرسة FTS من داخل النطاق المسموح (P0-C).
     *
     * DONE(P0-C): كان `LocalRepository.saveLocalHistory` خارج النطاق المسموح
     * (ممنوع لمسه هنا) ولا تُفهرس — كل مناديها السبعة في
     * `RedConnectionService` يحفظ بلا `indexMessage` فيتسع الفارق بين
     * الجدول والفهرس مع كل رسالة. انقل هذا السطرين إلى هناك:
     * `dao.insertLocalHistory(h)` ثم `FtsSearchManager(db).indexLocalHistory(h)` —
     * وحتى يتم النقل، نادِ `saveAndIndex()` بدل `saveLocalHistory()` من أي
     * كود جديد داخل النطاق المسموح.
     */
    suspend fun saveAndIndex(history: LocalHistoryEntity) {
        repository.saveLocalHistory(history)
        runCatching {
            val db = RedDatabase.getInstance(getApplication()).openHelper.writableDatabase
            FtsSearchManager(db).indexLocalHistory(history)
        }.onFailure { e ->
            android.util.Log.w("ChatHistoryPaging", "FTS index failed for ${history.id}", e)
        }
    }
}
