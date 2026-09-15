package com.red.sovereign.social

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val api = FeedApi(AuthorizedApiClient(TokenStore(application)))
    private val drafts = DraftsStore(application)
    private val scheduled = ScheduledPostsStore(application)
    private val bookmarks = BookmarksStore(application)
    val posts = mutableStateListOf<Post>()
    val threadPosts = mutableStateListOf<Post>()
    var state: FeedState by mutableStateOf(FeedState.Loading); private set
    var threadState: ThreadState by mutableStateOf(ThreadState.Idle); private set
    var scope: String? = null; private set
    var nextCursor: String? by mutableStateOf(null); private set
    var isLoadingMore: Boolean by mutableStateOf(false); private set
    var searchQuery: String by mutableStateOf("")
    var activeHashtag: String? by mutableStateOf(null)
    /** المنشورات المجدولة (محلية، تُنشر عبر Worker في موعدها). */
    val scheduledPosts = scheduled.items
    val bookmarkedPosts = bookmarks.bookmarkedPosts

    // === Paging3 (2026-09-10): تدفق صفحات لكل scope، مخزّن عبر cachedIn. ===
    // null scope صالح ("لك") فيميّز أول إنشاء بعلم منفصل لا بقيمة null.
    private var pagerInit = false
    private var pagerScope: String? = null
    private var pagerFlow: Flow<PagingData<Post>>? = null
    private val pagerLock = Any()

    fun pager(feedScope: String?): Flow<PagingData<Post>> {
        synchronized(pagerLock) {
            if (!pagerInit || pagerFlow == null || pagerScope != feedScope) {
                pagerInit = true
                pagerScope = feedScope
                pagerFlow = feedPager(api, feedScope).cachedIn(viewModelScope)
            }
            return requireNotNull(pagerFlow) {
                "Feed pager not initialized for scope=$feedScope"
            }
        }
    }

    fun invalidatePager() {
        synchronized(pagerLock) {
            pagerFlow = null
            pagerScope = null
        }
    }

    // === Drafts API (encrypted, per-scope) ===
    val hasDraft = drafts.hasDraft

    init { load(null) }

    fun load(newScope: String?) = viewModelScope.launch {
        scope = newScope; state = FeedState.Loading
        nextCursor = null
        try {
            when (val result = api.load(newScope)) {
                is ApiResult.Success -> {
                    posts.clear()
                    posts.addAll(result.value.posts)
                    nextCursor = result.value.nextCursor?.ifBlank { null }
                    state = FeedState.Ready
                }
                is ApiResult.Error -> state = FeedState.Error(result.message)
            }
        } catch (e: Exception) {
            // بلا هذا تبقى state=Loading للأبد عند استثناء غير متوقع (JSON/IO خارج العميل).
            Log.e("FeedViewModel", "load failed: ${e.message}", e)
            state = FeedState.Error(e.message ?: "تعذر التحميل")
        }
    }

    fun loadMore() = viewModelScope.launch {
        val cursor = nextCursor ?: return@launch
        if (isLoadingMore || state == FeedState.Loading) return@launch
        isLoadingMore = true
        try {
            when (val result = api.load(scope, cursor = cursor)) {
                is ApiResult.Success -> {
                    val newPosts = result.value.posts.filter { p -> posts.none { it.id == p.id } }
                    posts.addAll(newPosts)
                    nextCursor = result.value.nextCursor?.ifBlank { null }
                }
                is ApiResult.Error -> Log.w("FeedViewModel", "loadMore error: ${result.message}")
            }
        } catch (e: Exception) {
            Log.e("FeedViewModel", "loadMore failed: ${e.message}", e)
        } finally {
            isLoadingMore = false
        }
    }

    fun toggleBookmark(post: Post): Boolean = bookmarks.toggle(post)

    fun isBookmarked(postId: String): Boolean = bookmarks.isBookmarked(postId)

    fun create(text: String, visibility: String = "PUBLIC", done: () -> Unit) = viewModelScope.launch {
        state = FeedState.Publishing
        try {
            when (val result = api.create(text, visibility)) {
                is ApiResult.Success -> { posts.add(0, result.value); invalidatePager(); state = FeedState.Ready; discardDraft(); done() }
                is ApiResult.Error -> state = FeedState.Error(result.message)
            }
        } catch (e: Exception) {
            Log.e("FeedViewModel", "create failed: ${e.message}", e)
            state = FeedState.Error(e.message ?: "تعذر النشر")
        }
    }

    /**
     * إنشاء منشور مع وسائط مرفوعة مسبقًا (objectKey/JPEG...).
     * تُبنى القائمة في الـ Composer عبر MediaApi.upload ثم تُمرر هنا —
     * ViewModel لا يلمس ContentResolver (قابل للاختبار).
     */
    fun createWithMedia(
        text: String,
        visibility: String = "PUBLIC",
        media: List<PostMedia> = emptyList(),
        hashtags: List<String> = emptyList(),
        mentions: List<String> = emptyList(),
        location: String? = null,
        done: () -> Unit
    ) = viewModelScope.launch {
        if (text.isBlank() && media.isEmpty()) {
            state = FeedState.Error("اكتب نصًا أو أرفق وسيطًا")
            return@launch
        }
        state = FeedState.Publishing
        val request = CreatePostRequest(
            text = text.trim().ifBlank { "📷" },
            visibility = visibility,
            media = media,
            hashtags = hashtags,
            mentions = mentions,
            location = location?.ifBlank { null }
        )
        try {
            when (val result = api.create(request)) {
                is ApiResult.Success -> { posts.add(0, result.value); invalidatePager(); state = FeedState.Ready; discardDraft(); done() }
                is ApiResult.Error -> state = FeedState.Error(result.message)
            }
        } catch (e: Exception) {
            Log.e("FeedViewModel", "createWithMedia failed: ${e.message}", e)
            state = FeedState.Error(e.message ?: "تعذر النشر")
        }
    }

    fun createPoll(
        question: String,
        options: List<String>,
        durationHours: Int = 24,
        visibility: String = "PUBLIC",
        optionImages: List<String?> = emptyList(),
        done: () -> Unit
    ) = viewModelScope.launch {
        val cleanOptions = options.map(String::trim).filter { it.length >= 2 }.distinct().take(6)
        if (question.isBlank() || cleanOptions.size < 2) {
            state = FeedState.Error("اكتب سؤالاً واضحاً وخيارين على الأقل")
            return@launch
        }
        // صور الخيارات (نمط X): تُقبل فقط مع 2..4 خيارات، مصفوفة موازية
        // بنفس الترتيب — null للخيار النصي. أي طول مخالف يُرفض هنا قبل
        // أن يرفضه الخادم بـ400.
        val images = optionImages.take(cleanOptions.size) + List<String?>((cleanOptions.size - optionImages.size).coerceAtLeast(0)) { null }
        if (images.any { it != null } && cleanOptions.size !in 2..4) {
            state = FeedState.Error("الخيارات المصوّرة تدعم من خيارين إلى أربعة فقط")
            return@launch
        }
        state = FeedState.Publishing
        val request = CreatePostRequest(
            text = question.trim(),
            // `visibility` هنا قيمة PostVisibility ("PUBLIC" أو "FRIENDS")
            // يختارها المستخدم من PostVisibilityPicker، لا قيمة FeedScope.
            // كان الفرع السابق يثبّتها على DEFAULT_POST_VISIBILITY تفاديًا
            // لعطبٍ قديم يُمرَّر فيه `scope` مكان `visibility`؛ وقد زال
            // العطب بإضافة المنتقي، فالتثبيت الآن يُلغي اختيار المستخدم.
            visibility = visibility,
            pollOptions = cleanOptions,
            pollDurationHours = durationHours.coerceIn(1, 168),
            pollOptionImages = images
        )
        try {
            when (val result = api.create(request)) {
                is ApiResult.Success -> { posts.add(0, result.value); invalidatePager(); state = FeedState.Ready; discardDraft(); done() }
                is ApiResult.Error -> state = FeedState.Error(result.message)
            }
        } catch (e: Exception) {
            Log.e("FeedViewModel", "createPoll failed: ${e.message}", e)
            state = FeedState.Error(e.message ?: "تعذر نشر الاستطلاع")
        }
    }

    /** جدولة منشور/استطلاع (نص فقط V1) — تُحفظ محليًا وتُنشر عبر Worker. */
    fun schedulePost(
        text: String,
        visibility: String = "PUBLIC",
        pollOptions: List<String> = emptyList(),
        pollDurationHours: Int? = null,
        scheduledAtMs: Long,
        done: () -> Unit
    ) {
        // تحقق مبكر يميّز الموعد الخاطئ عن فشل الكتابة على القرص (كلاهما null من المخزن).
        if (scheduledAtMs <= System.currentTimeMillis() + 60_000L) {
            state = FeedState.Error("اختر موعدًا بعد دقيقة من الآن على الأقل")
            return
        }
        val post = scheduled.schedule(text, visibility, pollOptions, pollDurationHours, scheduledAtMs)
        if (post == null) {
            Log.e("FeedViewModel", "schedule persist failed (disk full?)")
            state = FeedState.Error("فشل حفظ الجدولة — تحقق من مساحة التخزين ثم أعد المحاولة")
            return
        }
        discardDraft()
        state = FeedState.Message("تمت الجدولة · ${scheduledLabel(post.scheduledAtMs)}")
        done()
    }

    fun cancelScheduled(id: String) {
        scheduled.cancel(id)
        state = FeedState.Message("أُلغيت الجدولة")
    }

    fun requestFriend(post: Post) = viewModelScope.launch {
        when (val result = api.requestFriend(post.authorRedId)) {
            is ApiResult.Success -> state = FeedState.Message("تم إرسال طلب صداقة إلى @${post.authorUsername}")
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun toggleLike(post: Post) = react(post, "LIKE")

    /**
     * التفاعلات الأربع المدعومة خادماً (LIKE/LOVE/SUPPORT/INSIGHTFUL) — كانت LIKE فقط.
     * تفوق: تحديث متفائل فوري (Optimistic) — يزيد العدّاد محليًا أولًا فيرتسم
     * التفاعل بلا انتظار الشبكة، وعند فشل الخادم يُعاد العدّاد السابق.
     */
    fun react(post: Post, type: String) = viewModelScope.launch {
        val idx = posts.indexOfFirst { it.id == post.id }
        val prev = if (idx >= 0) posts[idx] else post
        var succeeded = false
        if (idx >= 0) {
            val bumped = prev.reactionCounts.toMutableMap().apply { this[type] = (this[type] ?: 0) + 1 }
            posts[idx] = prev.copy(reactionCounts = bumped)
        }
        try {
            when (val result = api.react(post.id, type, true)) {
                is ApiResult.Success -> { replace(result.value); succeeded = true }
                is ApiResult.Error -> {
                    Log.w("FeedViewModel", "react failed, rolling back: ${result.message}")
                    state = FeedState.Error(result.message)
                }
            }
        } catch (e: Exception) {
            // استثناء/إلغاء بعد التحديث المتفائل — لا يبقى العدّاد مكذوبًا.
            Log.w("FeedViewModel", "react exception, rolling back", e)
            state = FeedState.Error(e.message ?: "تعذر التفاعل")
        } finally {
            if (!succeeded && idx >= 0) posts[idx] = prev // تراجع عن التفاؤل
        }
    }

    fun vote(post: Post, optionId: String) = viewModelScope.launch {
        if (isPollExpired(post.poll?.expiresAt)) {
            state = FeedState.Message("انتهى التصويت")
            return@launch
        }
        when (val result = api.vote(post.id, optionId)) {
            is ApiResult.Success -> replace(result.value)
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun repost(post: Post) = viewModelScope.launch {
        when (val result = api.repost(post.id)) {
            is ApiResult.Success -> replace(result.value)
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun loadThread(post: Post) = viewModelScope.launch {
        threadState = ThreadState.Loading
        when (val result = api.thread(post.id)) {
            is ApiResult.Success -> { threadPosts.clear(); threadPosts.addAll(result.value); threadState = ThreadState.Ready }
            is ApiResult.Error -> threadState = ThreadState.Error(result.message)
        }
    }

    fun reply(post: Post, text: String, done: () -> Unit) = viewModelScope.launch {
        threadState = ThreadState.Publishing
        when (val result = api.create(CreatePostRequest(text.trim(), post.visibility, parentId = post.id))) {
            is ApiResult.Success -> {
                threadPosts.add(result.value)
                posts.indexOfFirst { it.id == post.id }.takeIf { it >= 0 }?.let { index -> posts[index] = posts[index].copy(replyCount = posts[index].replyCount + 1) }
                threadState = ThreadState.Ready
                done()
            }
            is ApiResult.Error -> threadState = ThreadState.Error(result.message)
        }
    }

    fun quote(post: Post, text: String, done: () -> Unit) = viewModelScope.launch {
        state = FeedState.Publishing
        when (val result = api.create(CreatePostRequest(text.trim(), post.visibility, quotePostId = post.id))) {
            is ApiResult.Success -> { posts.add(0, result.value); state = FeedState.Ready; done() }
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun edit(post: Post, newText: String, done: () -> Unit = {}) = viewModelScope.launch {
        when (val result = api.edit(post.id, newText)) {
            is ApiResult.Success -> { replace(result.value); done() }
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    // === حذف مع تراجع 5s (نافذة Snackbar) ===
    // يُزال محليًا فورًا ويُستدعى الخادم بعد مهلة — التراجع داخل المهلة
    // يلغي طلب الحذف ويعيد العنصر لموضعه. بلا هذا لا عودة بعد الحذف.
    private var pendingDeleteJob: Job? = null
    private var pendingDelete: Post? = null
    private var pendingDeleteIndex: Int = -1

    fun delete(post: Post) {
        pendingDeleteJob?.cancel()
        val idx = posts.indexOfFirst { it.id == post.id }.takeIf { it >= 0 }
            ?: posts.indexOf(post).takeIf { it >= 0 } ?: -1
        pendingDelete = post
        pendingDeleteIndex = idx
        if (idx >= 0) posts.removeAt(idx) else posts.remove(post)
        pendingDeleteJob = viewModelScope.launch {
            try {
                delay(UNDO_WINDOW_MS)
                when (val result = api.delete(post.id)) {
                    is ApiResult.Success -> { pendingDelete = null; pendingDeleteIndex = -1 }
                    is ApiResult.Error -> {
                        // فشل الخادم بعد المهلة: نعيد العنصر ونبلّغ.
                        restorePendingDelete()
                        state = FeedState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e // تراجع — لا استعادة
                Log.e("FeedViewModel", "delete failed: ${e.message}", e)
                restorePendingDelete()
                state = FeedState.Error(e.message ?: "تعذر الحذف")
            }
        }
    }

    /** تراجع المستخدم داخل المهلة: إلغاء طلب الحذف وإعادة العنصر لموضعه. */
    fun undoDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        restorePendingDelete()
        state = FeedState.Message("تم التراجع عن الحذف")
    }

    private fun restorePendingDelete() {
        val backup = pendingDelete ?: return
        val idx = pendingDeleteIndex
        if (posts.none { it.id == backup.id }) {
            if (idx in 0..posts.size) posts.add(idx, backup) else posts.add(0, backup)
        }
        pendingDelete = null
        pendingDeleteIndex = -1
    }

    fun hide(post: Post) = viewModelScope.launch {
        when (val result = api.hide(post.id)) {
            is ApiResult.Success -> posts.remove(post)
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun mute(post: Post) = viewModelScope.launch {
        when (val result = api.mute(post.authorRedId)) {
            is ApiResult.Success -> { posts.removeAll { it.authorRedId == post.authorRedId }; state = FeedState.Message("تم كتم @${post.authorUsername}") }
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun report(post: Post, reason: String = "OTHER") = viewModelScope.launch {
        when (val result = api.report(post.id, reason)) {
            is ApiResult.Success -> state = FeedState.Message("تم الإبلاغ")
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun closeThread() { threadPosts.clear(); threadState = ThreadState.Idle }

    // حارس التحديث المتزامن: سحبات متتالية سريعة كانت تتداخل (طلبان load
    // متوازيان — الأقدم قد يمحو نتيجة الأحدث). الأخير يلغي السابق.
    private var refreshJob: Job? = null

    fun refresh(): Job {
        refreshJob?.cancel()
        val job = load(scope)
        refreshJob = job
        return job
    }

    // === Drafts (encrypted, async, per-scope) ===
    /** فشل آخر حفظ مسودة — تراقبه الواجهة لعرض "فشل حفظ المسودة". */
    val draftSaveFailed = drafts.saveFailed

    /** حفظ مسودة فوراً (async, encrypted) */
    fun saveDraft(text: String) = drafts.save(text, scope)

    /** تحميل مسودة محفوظة (suspend) */
    suspend fun loadDraft(): String? = drafts.load(scope)

    /** حذف المسودة الحالية (بعد النشر أو الإلغاء) */
    fun discardDraft() = drafts.delete(scope)

    /** حذف كل المسودات (sign-out flow) */
    fun clearAllDrafts() = drafts.clearAll()

    private fun replace(post: Post) {
        posts.indexOfFirst { it.id == post.id }.takeIf { it >= 0 }?.let { posts[it] = post }
        state = FeedState.Ready
    }

    companion object {
        /** نافذة التراجع عن الحذف (5s — مهلة Snackbar). */
        const val UNDO_WINDOW_MS = 5_000L
    }
}

sealed interface ThreadState {
    data object Idle : ThreadState
    data object Loading : ThreadState
    data object Publishing : ThreadState
    data object Ready : ThreadState
    data class Error(val message: String) : ThreadState
}

sealed interface FeedState {
    data object Loading : FeedState
    data object Ready : FeedState
    data object Publishing : FeedState
    data class Message(val text: String) : FeedState
    data class Error(val message: String) : FeedState
}
