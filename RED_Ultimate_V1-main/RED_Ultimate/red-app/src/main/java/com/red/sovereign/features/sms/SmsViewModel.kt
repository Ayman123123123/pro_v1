package com.red.sovereign.features.sms

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel لميزة SMS الاحترافية: المحادثات والدردشة والإرسال والحذف
 * والبحث، مع تحديث فوري عبر WebSocket /ws/pstn وتحديث دوري كل 30 ثانية.
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

    val filteredConversations: List<SmsConversationDto>
        get() {
            val q = searchQuery.trim()
            return if (q.isBlank()) conversations
            else conversations.filter { it.number.contains(q, ignoreCase = true) || it.lastText?.contains(q, ignoreCase = true) == true }
        }

    fun onSearchChange(q: String) { searchQuery = q }

    fun start() {
        if (pollJob?.isActive == true) return
        loadConversations()
        socket.connect()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                loadConversations(silent = true)
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        socket.disconnect()
    }

    fun openChat(number: String) {
        chatNumber = number
        loadChat(number)
        viewModelScope.launch { api.markRead(number) }
    }

    fun closeChat() { chatNumber = null; chatMessages = emptyList() }

    fun loadConversations(silent: Boolean = false) {
        if (!silent) loading = true
        viewModelScope.launch {
            when (val r = api.conversations()) {
                is ApiResult.Success -> { conversations = r.value; error = null }
                is ApiResult.Error -> if (!silent) error = r.message ?: "فشل تحميل المحادثات"
            }
            loading = false
        }
    }

    fun loadChat(number: String) {
        viewModelScope.launch {
            when (val r = api.conversation(number)) {
                is ApiResult.Success -> chatMessages = r.value
                is ApiResult.Error -> error = r.message ?: "فشل تحميل الدردشة"
            }
        }
    }

    fun send(text: String) {
        val number = chatNumber ?: return
        if (text.isBlank() || sending) return
        sending = true; error = null
        viewModelScope.launch {
            when (val r = api.send(number, text)) {
                is ApiResult.Success -> {
                    // تحسين فوري — سيُؤكَّد من الخادم بعد التحديث
                    chatMessages = chatMessages + SmsMessageDto(
                        id = r.value.id,
                        number = number,
                        content = text,
                        direction = "OUT",
                        status = r.value.status,
                        createdAt = System.currentTimeMillis() / 1000,
                        read = true
                    )
                    loadConversations(silent = true)
                }
                is ApiResult.Error -> error = r.message ?: "فشل الإرسال"
            }
            sending = false
        }
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch {
            when (val r = api.delete(id)) {
                is ApiResult.Success -> {
                    chatMessages = chatMessages.filterNot { it.id == id }
                    loadConversations(silent = true)
                }
                is ApiResult.Error -> error = r.message ?: "فشل الحذف"
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            when (val r = api.refresh()) {
                is ApiResult.Success -> conversations = r.value
                is ApiResult.Error -> error = r.message ?: "فشل التحديث"
            }
        }
    }

    private fun handleEvent(e: PstnWsEnvelope) {
        when (e.type) {
            "SMS_RECEIVED" -> {
                val number = e.number ?: return
                val time = e.time ?: (System.currentTimeMillis() / 1000)
                // حدِّث الدردشة المفتوحة فورًا
                if (chatNumber == number) {
                    chatMessages = chatMessages + SmsMessageDto(
                        id = e.id ?: "in-${System.nanoTime()}",
                        number = number,
                        content = e.content ?: e.contentText ?: "",
                        direction = "IN",
                        status = "RECEIVED",
                        createdAt = time,
                        read = false
                    )
                }
                loadConversations(silent = true)
            }
            "SMS_STATUS" -> {
                val id = e.id ?: return
                val status = e.status ?: return
                chatMessages = chatMessages.map {
                    if (it.id == id) it.copy(status = status) else it
                }
            }
            else -> Unit
        }
    }
}
