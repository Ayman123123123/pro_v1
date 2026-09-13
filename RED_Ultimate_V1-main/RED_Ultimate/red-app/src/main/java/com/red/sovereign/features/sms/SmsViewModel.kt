package com.red.sovereign.features.sms

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.calls.PstnErrorLocalizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 📨 ViewModel موحّد لميزة SMS: المحادثات والدردشة والإرسال والحذف والبحث،
 * مع تحديث فوري عبر WebSocket `/ws/pstn` واستقصاء خفيف كل 30 ثانية.
 *
 * التوحيد (2026-09-05): كانت هناك نسختان متنافستان — `features.sms` بالبحث
 * والمقبس المُعاد الاتصال، و`sms` بتوطين الأخطاء وشارة الإجمالي وإيقاف
 * الاستقصاء عند مغادرة الشاشة. هذه النسخة تجمع الأقوى من الاثنتين:
 * - [PstnEventSocket] بالتراجع الأسّي وتحديث التوكن وتثبيت الشهادة (من `features.sms`).
 * - [PstnErrorLocalizer] لتوطين رموز الخادم عربياً (من `sms`).
 * - [totalUnread] لشارة التنقل، و[stopPolling] كي لا يبقى استقصاء في الخلفية.
 * - [SMS_STATUS] يُصلح الرسالة المعنية بالمعرّف بدل إعادة جلب المحادثة كاملة.
 */
class SmsViewModel(application: Application) : AndroidViewModel(application) {
    private val tokens = TokenStore(application)
    private val api = SmsApi(tokens)

    var conversations by mutableStateOf<List<SmsConversationDto>>(emptyList())
        private set
    var chatMessages by mutableStateOf<List<SmsMessageDto>>(emptyList())
        private set
    var chatNumber by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var connected by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set

    private val socket = PstnEventSocket(tokens, onEnvelope = ::handleEvent, onState = { connected = it })
    private var pollJob: Job? = null

    /** إجمالي غير المقروء لكل المحادثات — لشارة التنقل. */
    val totalUnread: Int get() = conversations.sumOf { it.unreadCount }

    /** تقدم الحذف الجماعي 0..1 — null عند عدم وجود حذف جارٍ (لمؤشر التقدم). */
    var deleteProgress by mutableStateOf<Float?>(null)
        private set

    /** لقطة آخر محادثة محذوفة — لزر Undo في Snackbar (استعادة محلية فورية). */
    var lastDeletedNumber by mutableStateOf<String?>(null)
        private set
    private var lastDeletedSnapshot: DeletedSnapshot? = null

    private data class DeletedSnapshot(
        val number: String,
        val conversations: List<SmsConversationDto>,
        val chatMessages: List<SmsMessageDto>,
        val chatNumber: String?,
        val messages: List<SmsMessageDto>
    )

    val filteredConversations: List<SmsConversationDto>
        get() {
            val q = searchQuery.trim()
            return if (q.isBlank()) conversations
            else conversations.filter {
                it.number.contains(q, ignoreCase = true) || it.lastText.contains(q, ignoreCase = true)
            }
        }

    fun onSearchChange(q: String) { searchQuery = q }

    /** بدء الجلسة الحيّة: جلب فوري + مقبس + استقصاء احتياطي. */
    fun start() {
        // الجلب الفوري خارج شرط pollJob: العودة لنفس الشاشة يجب أن تحدّث القائمة.
        loadConversations()
        socket.connect()
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                loadConversations(silent = true)
                chatNumber?.let { loadChat(it, silent = true) }
            }
        }
    }

    /** إيقاف الاستقصاء والمقبس عند مغادرة شاشة SMS — لا استهلاك في الخلفية. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        socket.disconnect()
    }

    override fun onCleared() {
        pollJob?.cancel()
        pollJob = null
        socket.shutdown()
        super.onCleared()
    }

    fun openChat(number: String) {
        chatNumber = number
        loadChat(number)
        // تصفير الشارة محلياً فوراً ثم تأكيدها من الخادم
        conversations = conversations.map { if (it.number == number) it.copy(unreadCount = 0) else it }
        viewModelScope.launch {
            api.markRead(number)
            loadConversations(silent = true)
        }
    }

    fun closeChat() { chatNumber = null; chatMessages = emptyList() }

    fun loadConversations(silent: Boolean = false) {
        if (!silent) loading = true
        viewModelScope.launch {
            when (val r = api.conversations()) {
                is ApiResult.Success -> {
                    conversations = r.value.sortedByDescending { it.lastTime }
                    error = null
                }
                is ApiResult.Error -> if (!silent) error = localize(r.message)
            }
            if (!silent) loading = false
        }
    }

    fun loadChat(number: String, silent: Boolean = false) {
        viewModelScope.launch {
            when (val r = api.conversation(number)) {
                is ApiResult.Success -> chatMessages = r.value
                is ApiResult.Error -> if (!silent) error = localize(r.message)
            }
        }
    }

    fun send(text: String) {
        val number = chatNumber ?: return
        if (text.isBlank() || sending) return
        sending = true; error = null
        viewModelScope.launch {
            when (val r = api.send(number, text.trim())) {
                is ApiResult.Success -> {
                    // صدى محلي فوري — يُستبدل بالحقيقة عند أول تحديث من الخادم
                    chatMessages = chatMessages + SmsMessageDto(
                        id = r.value.id,
                        number = number,
                        content = text.trim(),
                        direction = "OUT",
                        status = r.value.status,
                        createdAt = System.currentTimeMillis() / 1000,
                        isRead = true
                    )
                    loadConversations(silent = true)
                }
                is ApiResult.Error -> error = localize(r.message)
            }
            sending = false
        }
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch {
            when (api.delete(id)) {
                is ApiResult.Success -> {
                    chatMessages = chatMessages.filterNot { it.id == id }
                    loadConversations(silent = true)
                }
                is ApiResult.Error -> error = "تعذر حذف الرسالة"
            }
        }
    }

    /**
     * حذف محادثة كاملة (ضغط طويل في القائمة).
     *
     * المسار الأول: DELETE /api/sms/conversation/{number} الذري في
     * ([com.red.server.sms.SmsController]) — طلب واحد transacted.
     * السقوط: 404 (خادم قديم بلا المسار) → الـ chunked القديم المتوازي
     * (20 رسالة/دفعة عبر async) مع مؤشر تقدم [deleteProgress].
     * في الحالتين لقطة تراجع [undoDeleteConversation] + إخفاء متفائل فوري.
     */
    fun deleteConversation(number: String) {
        viewModelScope.launch {
            val messages = when (val r = api.conversation(number)) {
                is ApiResult.Success -> r.value
                is ApiResult.Error -> {
                    error = localize(r.message)
                    return@launch
                }
            }
            if (messages.isEmpty()) {
                conversations = conversations.filterNot { it.number == number }
                if (chatNumber == number) closeChat()
                return@launch
            }
            // لقطة التراجع قبل أي حذف — يعرضها الـ UI في Snackbar مع Undo.
            lastDeletedSnapshot = DeletedSnapshot(
                number = number,
                conversations = conversations.toList(),
                chatMessages = chatMessages.toList(),
                chatNumber = chatNumber,
                messages = messages.toList()
            )
            lastDeletedNumber = number
            // إخفاء متفائل فوري — يُستعاد عبر Undo إن طُلب.
            conversations = conversations.filterNot { it.number == number }
            if (chatNumber == number) closeChat()

            deleteProgress = 0f
            error = null
            // ── المحاولة الذرية أولاً ──
            when (val bulk = api.deleteConversation(number)) {
                is ApiResult.Success -> {
                    deleteProgress = 1f
                    deleteProgress = null
                    loadConversations(silent = true)
                    return@launch
                }
                is ApiResult.Error -> {
                    // 404 = خادم قديم بلا المسار، null = شبكة متذبذبة:
                    // نسقط للـ chunked أدناه. أي خطأ آخر (ملكية/تحقق)
                    // حقيقي — نُبقي الإخفاء المتفائل + Undo ولا نُطلق وابل
                    // الـ N طلبات عبثاً.
                    if (bulk.code != 404 && bulk.code != null) {
                        deleteProgress = null
                        error = localize(bulk.message)
                        loadConversations(silent = true)
                        return@launch
                    }
                }
            }
            // ── السقوط chunked (خادم قديم / شبكة متذبذبة) ──
            val done = AtomicInteger(0)
            val total = messages.size
            val failed = coroutineScope {
                messages.chunked(20).map { chunk ->
                    async {
                        var chunkFailed = 0
                        for (msg in chunk) {
                            if (api.delete(msg.id) is ApiResult.Error) chunkFailed++
                            deleteProgress = done.incrementAndGet().toFloat() / total
                        }
                        chunkFailed
                    }
                }.awaitAll().sum()
            }
            deleteProgress = null
            if (failed > 0) error = "تعذر حذف $failed من $total رسالة"
            loadConversations(silent = true)
        }
    }

    /**
     * تراجع محلي فوري عن آخر حذف جماعي — يعيد الصف والرسائل للواجهة.
     * ملاحظة: الرسائل المحذوفة من الخادم فعلاً لا تُستعاد (لا سلة مهملات
     * في backend) — الـ Undo يصلح الإخفاء المتفائل والفشل الجزئي فقط.
     * امسح اللقطة بعد انتهاء مهلة Snackbar عبر [consumeDeletedSnapshot].
     */
    fun undoDeleteConversation() {
        val snap = lastDeletedSnapshot ?: return
        conversations = snap.conversations
        if (snap.chatNumber != null) {
            chatNumber = snap.chatNumber
            chatMessages = snap.chatMessages
        }
        lastDeletedSnapshot = null
        lastDeletedNumber = null
        deleteProgress = null
    }

    /** تُستدعى عند انتهاء مهلة Snackbar دون تراجع — تمنع Undo متأخراً. */
    fun consumeDeletedSnapshot() {
        lastDeletedSnapshot = null
        lastDeletedNumber = null
    }

    /** زر التحديث اليدوي — يطلب من الخادم استطلاع وارد الجهاز فوراً. */
    fun refresh() {
        loading = true; error = null
        viewModelScope.launch {
            when (val r = api.refresh()) {
                is ApiResult.Success -> conversations = r.value.sortedByDescending { it.lastTime }
                is ApiResult.Error -> error = localize(r.message)
            }
            loading = false
            chatNumber?.let { loadChat(it, silent = true) }
        }
    }

    private fun handleEvent(e: PstnWsEnvelope) {
        when (e.type) {
            "SMS_RECEIVED" -> {
                val number = e.number ?: return
                val time = e.time ?: (System.currentTimeMillis() / 1000)
                if (chatNumber == number) {
                    val id = e.id ?: "in-${System.nanoTime()}"
                    // لا تكرّر الرسالة إن سبق وصولها عبر الاستقصاء
                    if (chatMessages.none { it.id == id }) {
                        chatMessages = chatMessages + SmsMessageDto(
                            id = id,
                            number = number,
                            content = e.content ?: e.contentText ?: "",
                            direction = "IN",
                            status = "RECEIVED",
                            createdAt = time,
                            isRead = false
                        )
                    }
                }
                loadConversations(silent = true)
            }
            "SMS_STATUS" -> {
                val id = e.id ?: return
                val status = e.status ?: return
                chatMessages = chatMessages.map { if (it.id == id) it.copy(status = status) else it }
                loadConversations(silent = true)
            }
            else -> Unit
        }
    }

    /** توطين رموز الخادم عربياً — لا يرى المستخدم NETWORK_ERROR خاماً. */
    private fun localize(raw: String?): String =
        runCatching { PstnErrorLocalizer.arabic(raw) }.getOrDefault(raw ?: "تعذر الاتصال بالخادم")

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
    }
}
