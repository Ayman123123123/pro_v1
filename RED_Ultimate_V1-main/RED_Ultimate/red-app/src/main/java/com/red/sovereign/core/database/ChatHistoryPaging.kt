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
import androidx.sqlite.db.SupportSQLiteDatabase
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
 *
 * إصلاح 2026-09-24 (نطاق هذا الملف فقط):
 * - كان المفتاح `Int` = OFFSET رقمي هش: أي رسالة جديدة في الأعلى تزيح كل
 *   الإزاحات فيتكرر/يُسقط عناصر. أصبح المفتاح (createdAt,id) مستقرًا.
 * - الترتيب موحّد: الأحدث أولًا DESC (createdAt DESC, id DESC) مثل
 *   `getLocalHistoryPage` وواجهة الدردشة. ملاحظة: `getLocalHistory()`
 *   في RedDao ما زال ASC (نطاق زميل — لا يُلمس هنا).
 * - لا إعادة بناء للعناصر: تُعاد الكيانات كما هي من Room/Cursor بكل أعمدة
 *   replyTo* (لا copy يُسقطها).
 */

/** حجم الصفحة الافتراضي لسجل المحادثة (P0-C 2026-09-12: مثبت 30). */
const val CHAT_HISTORY_PAGE_SIZE = 30

/** سعة كاش النص المفكوك LRU — يُجنّب إعادة فك protobuf/UTF-8 كل تركيب. */
const val CHAT_HISTORY_DECRYPTED_CACHE_SIZE = 200

/** مفتاح keyset مستقر: (createdAt,id) — لا يتزحزح بإدراج رسائل جديدة في الأعلى. */
data class ChatHistoryKey(val createdAt: Long, val id: String)

private fun LocalHistoryEntity.key() = ChatHistoryKey(createdAt, id)

class ChatHistoryPagingSource(
    private val dao: RedDao,
    private val conversationId: String,
    private val db: SupportSQLiteDatabase? = null
) : PagingSource<ChatHistoryKey, LocalHistoryEntity>() {

    override suspend fun load(params: LoadParams<ChatHistoryKey>): LoadResult<ChatHistoryKey, LocalHistoryEntity> {
        if (conversationId.isBlank()) {
            return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
        }
        val limit = params.loadSize.coerceIn(1, 200)
        return try {
            val data: List<LocalHistoryEntity> = when (params) {
                is LoadParams.Refresh -> {
                    val k = params.key
                    if (k == null) loadAfter(null, limit)
                    else loadFromInclusive(k, limit)
                }
                is LoadParams.Append -> loadAfter(params.key, limit)
                is LoadParams.Prepend -> loadBefore(params.key, limit)
            }
            val firstKey = data.firstOrNull()?.key()
            val lastKey = data.lastOrNull()?.key()
            val nextKey = if (data.size < limit) null else lastKey
            val prevKey: ChatHistoryKey? = when (params) {
                is LoadParams.Refresh -> if (params.key == null) null else firstKey
                is LoadParams.Append -> firstKey
                is LoadParams.Prepend -> if (data.size < limit) null else firstKey
            }
            // صف فارغ في الوسط (حُذف المرساة): أنهِ الاتجاه المطلوب فقط.
            if (data.isEmpty()) {
                when (params) {
                    is LoadParams.Prepend -> LoadResult.Page(data, prevKey = null, nextKey = params.key)
                    is LoadParams.Append -> LoadResult.Page(data, prevKey = params.key, nextKey = null)
                    else -> LoadResult.Page(data, prevKey = null, nextKey = null)
                }
            } else {
                LoadResult.Page(data, prevKey = prevKey, nextKey = nextKey)
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<ChatHistoryKey, LocalHistoryEntity>): ChatHistoryKey? =
        state.anchorPosition?.let { anchor ->
            state.closestItemToPosition(anchor)?.key()
        }

    // ---------- توجيه: raw keyset إن توفر db، وإلا مسح مستقر فوق DAO ----------

    private suspend fun loadAfter(key: ChatHistoryKey?, limit: Int): List<LocalHistoryEntity> =
        if (db != null) queryAfter(db, conversationId, key, limit)
        else scanAfter(key, limit)

    private suspend fun loadBefore(key: ChatHistoryKey?, limit: Int): List<LocalHistoryEntity> {
        if (key == null) return emptyList()
        return if (db != null) queryBefore(db, conversationId, key, limit)
        else scanBefore(key, limit)
    }

    private suspend fun loadFromInclusive(key: ChatHistoryKey, limit: Int): List<LocalHistoryEntity> =
        if (db != null) queryFromInclusive(db, conversationId, key, limit)
        else scanFromInclusive(key, limit)

    // ---------- مسار DAO الاحتياطي: keyset semantics فوق OFFSET API ----------
    // يجد المرساة بالمعرف (لا بالموضع) ثم يجمع ما بعدها — فالإدراج في الأعلى
    // لا يُكرر/يُسقط عناصر، فقط يكلف O(offset) استعلامات. المسار الأساسي (db)
    // هو O(limit). لا يُعاد بناء الكيانات: تُعاد كما هي ومعها replyTo*.

    private suspend fun scanAfter(key: ChatHistoryKey?, limit: Int): List<LocalHistoryEntity> {
        if (key == null) return dao.getLocalHistoryPage(conversationId, limit, 0)
        val batch = 100
        var offset = 0
        while (true) {
            val page = dao.getLocalHistoryPage(conversationId, batch, offset)
            if (page.isEmpty()) return emptyList()
            val idx = page.indexOfFirst { it.id == key.id }
            if (idx >= 0) {
                val out = ArrayList<LocalHistoryEntity>(limit)
                for (i in idx + 1 until page.size) {
                    out += page[i]
                    if (out.size == limit) return out
                }
                // المرساة قرب نهاية الدفعة: أكمل من الدفعة التالية مباشرة.
                var nextOffset = offset + page.size
                while (out.size < limit) {
                    val nxt = dao.getLocalHistoryPage(conversationId, batch, nextOffset)
                    if (nxt.isEmpty()) break
                    for (e in nxt) {
                        out += e
                        if (out.size == limit) break
                    }
                    if (nxt.size < batch) break
                    nextOffset += nxt.size
                }
                return out
            }
            if (page.size < batch) return emptyList() // المرساة حُذفت ولا أحدث منها معروف
            offset += page.size
        }
    }

    private suspend fun scanBefore(key: ChatHistoryKey, limit: Int): List<LocalHistoryEntity> {
        // اجمع من البداية (الأحدث) حتى المرساة، ثم خذ الأقرب إليها.
        val batch = 100
        var offset = 0
        val newer = ArrayList<LocalHistoryEntity>()
        while (true) {
            val page = dao.getLocalHistoryPage(conversationId, batch, offset)
            if (page.isEmpty()) break
            val idx = page.indexOfFirst { it.id == key.id }
            if (idx >= 0) {
                for (i in 0 until idx) newer += page[i]
                break
            }
            newer.addAll(page)
            if (page.size < batch) break // المرساة غير موجودة (حُذفت): لا تخمّن
            offset += page.size
            if (offset > 20_000) break // سقف أمان للمحادثات الضخمة في المسار الاحتياطي
        }
        if (newer.isEmpty()) return emptyList()
        // newer مرتب DESC (الأحدث أولًا)؛ takeLast = الأقرب للمرساة مع الحفاظ على DESC.
        return newer.takeLast(limit)
            .sortedWith(compareByDescending<LocalHistoryEntity> { it.createdAt }.thenByDescending { it.id })
    }

    private suspend fun scanFromInclusive(key: ChatHistoryKey, limit: Int): List<LocalHistoryEntity> {
        val anchor = runCatching { dao.getLocalHistoryEntry(key.id) }.getOrNull()
        if (anchor != null && anchor.conversationId == conversationId) {
            if (limit <= 1) return listOf(anchor)
            return listOf(anchor) + scanAfter(key, limit - 1)
        }
        return scanAfter(key, limit) // المرساة حُذفت: الأقدم منها مباشرة
    }
}

/** قراءة صف واحد مع كل الأعمدة — replyTo* محفوظة صراحة (لا تُسقط). */
private fun mapHistoryCursor(c: android.database.Cursor): LocalHistoryEntity {
    val iId = c.getColumnIndexOrThrow("id")
    val iConv = c.getColumnIndexOrThrow("conversationId")
    val iSender = c.getColumnIndexOrThrow("senderId")
    val iBlob = c.getColumnIndexOrThrow("encryptedPlaintext")
    val iType = c.getColumnIndexOrThrow("messageType")
    val iAt = c.getColumnIndexOrThrow("createdAt")
    val iOut = c.getColumnIndexOrThrow("outgoing")
    val iStatus = c.getColumnIndexOrThrow("status")
    val iRId = c.getColumnIndexOrThrow("replyToMessageId")
    val iRText = c.getColumnIndexOrThrow("replyToMessageText")
    val iRSender = c.getColumnIndexOrThrow("replyToSenderId")
    val iDel = c.getColumnIndexOrThrow("deletedForAll")
    val iDelBy = c.getColumnIndexOrThrow("deletedBySenderId")
    return LocalHistoryEntity(
        id = c.getString(iId),
        conversationId = c.getString(iConv),
        senderId = c.getString(iSender),
        encryptedPlaintext = c.getBlob(iBlob) ?: ByteArray(0),
        messageType = c.getString(iType),
        createdAt = c.getLong(iAt),
        outgoing = c.getInt(iOut) == 1,
        status = c.getString(iStatus),
        replyToMessageId = if (c.isNull(iRId)) null else c.getString(iRId),
        replyToMessageText = if (c.isNull(iRText)) null else c.getString(iRText),
        replyToSenderId = if (c.isNull(iRSender)) null else c.getString(iRSender),
        deletedForAll = c.getInt(iDel) == 1,
        deletedBySenderId = if (c.isNull(iDelBy)) null else c.getString(iDelBy)
    )
}

private const val HISTORY_COLS =
    "id, conversationId, senderId, encryptedPlaintext, messageType, createdAt, " +
        "outgoing, status, replyToMessageId, replyToMessageText, replyToSenderId, " +
        "deletedForAll, deletedBySenderId"

/** الأحدث أولًا DESC — موحّد مع getLocalHistoryPage وواجهة الدردشة. */
private fun queryAfter(
    db: SupportSQLiteDatabase,
    convId: String,
    key: ChatHistoryKey?,
    limit: Int
): List<LocalHistoryEntity> {
    val sql: String
    val args: Array<Any?>
    if (key == null) {
        sql = "SELECT $HISTORY_COLS FROM local_history WHERE conversationId = ? " +
            "ORDER BY createdAt DESC, id DESC LIMIT ?"
        args = arrayOf<Any?>(convId, limit)
    } else {
        sql = "SELECT $HISTORY_COLS FROM local_history WHERE conversationId = ? " +
            "AND (createdAt < ? OR (createdAt = ? AND id < ?)) " +
            "ORDER BY createdAt DESC, id DESC LIMIT ?"
        args = arrayOf<Any?>(convId, key.createdAt, key.createdAt, key.id, limit)
    }
    db.query(sql, args).use { c ->
        val out = ArrayList<LocalHistoryEntity>(limit)
        while (c.moveToNext()) out += mapHistoryCursor(c)
        return out
    }
}

private fun queryBefore(
    db: SupportSQLiteDatabase,
    convId: String,
    key: ChatHistoryKey,
    limit: Int
): List<LocalHistoryEntity> {
    // الأحدث من المرساة: نجيب ASC ثم نعكس للحفاظ على DESC في Paging.
    val sql = "SELECT $HISTORY_COLS FROM local_history WHERE conversationId = ? " +
        "AND (createdAt > ? OR (createdAt = ? AND id > ?)) " +
        "ORDER BY createdAt ASC, id ASC LIMIT ?"
    val args = arrayOf<Any?>(convId, key.createdAt, key.createdAt, key.id, limit)
    db.query(sql, args).use { c ->
        val asc = ArrayList<LocalHistoryEntity>(limit)
        while (c.moveToNext()) asc += mapHistoryCursor(c)
        asc.reverse() // إلى DESC
        return asc
    }
}

private fun queryFromInclusive(
    db: SupportSQLiteDatabase,
    convId: String,
    key: ChatHistoryKey,
    limit: Int
): List<LocalHistoryEntity> {
    // <= تشمل المرساة إن وُجدت، وإلا الأقدم منها مباشرة (حالة الحذف).
    val sql = "SELECT $HISTORY_COLS FROM local_history WHERE conversationId = ? " +
        "AND (createdAt < ? OR (createdAt = ? AND id <= ?)) " +
        "ORDER BY createdAt DESC, id DESC LIMIT ?"
    val args = arrayOf<Any?>(convId, key.createdAt, key.createdAt, key.id, limit)
    db.query(sql, args).use { c ->
        val out = ArrayList<LocalHistoryEntity>(limit)
        while (c.moveToNext()) out += mapHistoryCursor(c)
        return out
    }
}

private fun pagingConfig(pageSize: Int): PagingConfig {
    val ps = pageSize.coerceIn(10, 100)
    val prefetch = (ps / 2).coerceAtLeast(10)
    // maxSize يجب أن يتجاوز بكثير pageSize + prefetch وإلا رمى PagingConfig.
    val max = maxOf(CHAT_HISTORY_DECRYPTED_CACHE_SIZE + ps, ps * 3 + prefetch * 2)
    return PagingConfig(
        pageSize = ps,
        prefetchDistance = prefetch,
        initialLoadSize = (ps * 2).coerceIn(ps, 200),
        maxSize = max,
        enablePlaceholders = false
    )
}

/**
 * تدفق صفحات سجل محادثة واحدة — الأحدث أولًا (DESC)، مفتاح (createdAt,id).
 * مسار التوافق: يستعمل DAO فقط (مسح مستقر فوق OFFSET) — يُبقي توقيع
 * LocalRepository.chatHistoryPager يعمل دون تعديله (خارج النطاق).
 */
fun chatHistoryPager(
    dao: RedDao,
    conversationId: String,
    pageSize: Int = CHAT_HISTORY_PAGE_SIZE
): Flow<PagingData<LocalHistoryEntity>> = Pager(
    config = pagingConfig(pageSize),
    pagingSourceFactory = { ChatHistoryPagingSource(dao, conversationId, null) }
).flow

/**
 * المسار المفضل: keyset فعّال O(limit) عبر استعلام خام يستغل الفهرس
 * index_local_history_conv_created_desc. يستعمله ViewModel أدناه.
 */
fun chatHistoryPager(
    database: RedDatabase,
    conversationId: String,
    pageSize: Int = CHAT_HISTORY_PAGE_SIZE
): Flow<PagingData<LocalHistoryEntity>> {
    val dao = database.redDao()
    val sqlite = database.openHelper.readableDatabase
    return Pager(
        config = pagingConfig(pageSize),
        pagingSourceFactory = { ChatHistoryPagingSource(dao, conversationId, sqlite) }
    ).flow
}

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
    private val app = application
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
        if (conversationId.isBlank()) {
            // محادثة فارغة: تدفق فارغ بدل كسر SQL بـ WHERE conversationId = ''.
            return Pager(
                config = pagingConfig(pageSize),
                pagingSourceFactory = {
                    ChatHistoryPagingSource(
                        RedDatabase.getInstance(app).redDao(),
                        conversationId,
                        RedDatabase.getInstance(app).openHelper.readableDatabase
                    )
                }
            ).flow.cachedIn(viewModelScope)
        }
        synchronized(pagerLock) {
            if (conversationId != currentConversation || currentFlow == null) {
                currentConversation = conversationId
                // المسار المفضل keyset عبر RedDatabase (لا نمر بـ repository
                // حتى لا نبقى على OFFSET) — ومخزّن في viewModelScope.
                val database = RedDatabase.getInstance(app)
                currentFlow = chatHistoryPager(database, conversationId, pageSize)
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
     * إصلاح 2026-09-24: كان هنا فهرسة يدوية ثانية بعد
     * `repository.saveLocalHistory` — لكن تلك الدالة أصبحت تُفهرس
     * داخليًا (LocalRepository) فكانت الفهرسة تتكرر لكل رسالة.
     * الآن تفويض واحد فقط (لا إسقاط لـ replyTo* — النسخ في Repository
     * يحملها).
     */
    suspend fun saveAndIndex(history: LocalHistoryEntity) {
        repository.saveLocalHistory(history)
    }
}
