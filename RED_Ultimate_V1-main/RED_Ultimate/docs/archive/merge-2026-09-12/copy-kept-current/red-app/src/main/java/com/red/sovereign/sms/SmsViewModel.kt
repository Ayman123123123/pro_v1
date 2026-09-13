package com.red.sovereign.sms

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.calls.YemeniOperatorDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 📨 YOUNES SMS ViewModel — حالة رسائل الهاتف اليمني.
 *
 * - [conversations] قائمة المحادثات مرتبة زمنياً من الخادم.
 * - [thread] رسائل المحادثة المفتوحة حالياً.
 * - [polling] تحديث خفيف كل 30 ثانية طالما الشاشة ظاهرة — يُوقف عند الإخفاء
 *   ([stopPolling]) كي لا يبقى استقصاء في الخلفية يستهلك بطارية/شبكة.
 */
class SmsViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SmsApi(TokenStore(application))

    val conversations = mutableStateListOf<SmsConversation>()
    val thread = mutableStateListOf<SmsMessageDto>()

    var loading by mutableStateOf(false); private set
    var threadLoading by mutableStateOf(false); private set
    var sending by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var openNumber by mutableStateOf<String?>(null); private set

    private var pollJob: Job? = null
    private var events: PstnEventsListener? = null

    /** إجمالي غير المقروء لكل المحادثات — للشارة في التنقل. */
    val totalUnread: Int get() = conversations.sumOf { it.unreadCount }

    fun load(showSpinner: Boolean = true) = viewModelScope.launch {
        if (showSpinner) loading = true
        error = null
        when (val result = api.conversations()) {
            is ApiResult.Success -> {
                conversations.clear()
                conversations.addAll(result.value.sortedByDescending { it.lastTime })
            }
            is ApiResult.Error -> error = localize(result.message)
        }
        if (showSpinner) loading = false
    }

    /**
     * زر التحديث اليدوي — يطلب من الخادم استطلاع وارد الجهاز فوراً
     * (POST /api/sms/refresh) ثم يعرض النتيجة. يُستخدم لجلب الرسائل
     * الجديدة دون انتظار دورة الاستقصاء الدورية.
     */
    fun refreshFromDevice() = viewModelScope.launch {
        loading = true; error = null
        when (val result = api.refresh()) {
            is ApiResult.Success -> {
                conversations.clear()
                conversations.addAll(result.value.sortedByDescending { it.lastTime })
            }
            is ApiResult.Error -> error = localize(result.message)
        }
        loading = false
        // المحادثة المفتوحة قد تكون استقبلت رسائل — نحدّثها كذلك
        openNumber?.let { loadThread(it, showSpinner = false) }
    }

    fun openThread(number: String) {
        openNumber = number
        loadThread(number, showSpinner = true)
        // فتح المحادثة يعلم الوارد كمقروء ويُصفّر شارتها محلياً
        conversations.replaceAll {
            if (it.number == number) it.copy(unreadCount = 0) else it
        }
        viewModelScope.launch {
            api.markRead(number)
            // إعادة جلب المحادثات كي تُحدَّث شارة الإجمالي من الخادم
            load(showSpinner = false)
        }
    }

    fun closeThread() {
        openNumber = null
        thread.clear()
    }

    private fun loadThread(number: String, showSpinner: Boolean) = viewModelScope.launch {
        if (showSpinner) threadLoading = true
        when (val result = api.conversation(number)) {
            is ApiResult.Success -> {
                thread.clear()
                thread.addAll(result.value)
            }
            is ApiResult.Error -> error = localize(result.message)
        }
        if (showSpinner) threadLoading = false
    }

    fun send(number: String, text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        sending = true; error = null
        when (val result = api.send(number, text.trim())) {
            is ApiResult.Success -> loadThread(number, showSpinner = false)
            is ApiResult.Error -> error = localize(result.message)
        }
        sending = false
    }

    fun delete(message: SmsMessageDto) = viewModelScope.launch {
        when (api.delete(message.id)) {
            is ApiResult.Success -> {
                if (openNumber != null) loadThread(openNumber!!, showSpinner = false)
                load(showSpinner = false)
            }
            is ApiResult.Error -> error = localize("لا يمكن حذف الرسالة")
        }
    }

    /**
     * حذف محادثة كاملة من شاشة القائمة (ضغط طويل) — الخادم يحذف برسالة
     * واحدة، فنجلب سجل الرقم ثم نحذف أحدث رسالة فيه (آخر رسالة هي التي
     * تُظهر المحادثة في القائمة).
     */
    fun deleteConversation(number: String) = viewModelScope.launch {
        val messages = when (val result = api.conversation(number)) {
            is ApiResult.Success -> result.value
            is ApiResult.Error -> {
                error = localize(result.message)
                return@launch
            }
        }
        messages.lastOrNull()?.let { api.delete(it.id) }
        conversations.removeAll { it.number == number }
        load(showSpinner = false)
    }

    /** بدء الاستقصاء الخفيف + الاستماع الحي لأحداث الوارد — عند دخول شاشة SMS. */
    fun startPolling() {
        if (events == null) {
            events = PstnEventsListener(getApplication(),
                onSmsReceived = { _, _ ->
                    // حدث وارد جديد من الخادم — تحديث فوري بلا انتظار الاستقصاء
                    viewModelScope.launch {
                        if (openNumber != null) loadThread(openNumber!!, showSpinner = false)
                        else load(showSpinner = false)
                    }
                },
                onSmsStatus = { _, _ ->
                    // تغيّرت حالة تسليم (DELIVERED/FAILED) — تحديث الفقاعات
                    viewModelScope.launch {
                        if (openNumber != null) loadThread(openNumber!!, showSpinner = false)
                    }
                }
            ).also { it.start() }
        }
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                if (openNumber != null) loadThread(openNumber!!, showSpinner = false)
                else load(showSpinner = false)
            }
        }
    }

    /** إيقاف الاستقصاء والاستماع — عند مغادرة شاشة SMS. */
    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        events?.stop()
        events = null
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private fun localize(raw: String?): String = when (raw) {
        null -> "تعذر الاتصال بالخادم"
        "NETWORK_ERROR" -> "تعذر الاتصال بالخادم — تحقق من الشبكة"
        "UNAUTHENTICATED" -> "انتهت الجلسة — أعد تسجيل الدخول"
        else -> runCatching { com.red.sovereign.calls.PstnErrorLocalizer.arabic(raw) }.getOrDefault(raw)
    }
}

private inline fun <T> MutableList<T>.replaceAll(transform: (T) -> T) {
    for (i in indices) this[i] = transform(this[i])
}

/** اسم المشغل العربي لرقم يمني — يعيد الاعتماد على كاشف المشغلين الحالي. */
internal fun smsOperatorLabel(number: String, fallback: String): String {
    if (fallback.isNotBlank() && fallback != "غير معروف") return fallback
    return YemeniOperatorDetector.getOperatorInfo(number)?.name ?: fallback.ifBlank { "غير معروف" }
}
