package com.red.sovereign.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 📡 ServerConnectionMonitor — مصدر واحد للحقيقة لحالة السيرفر.
 *
 * لماذا وُجد؟ سابقاً كان الانقطاع يظهر كإزعاج (إشعارات متكررة/سلوك غامض)
 * بينما الواجهة لا تعرض بوضوح «السيرفر متصل/طافٍ». الآن:
 * - [RedConnectionService] يحدّث هذا المراقب من أحداث الاتصال الفعلية.
 * - شريط حالة أعلى التطبيق (ServerConnectionBanner) يقرأه مباشرة.
 * - لا Polling من الواجهة ولا حوارات — تدفّق واحد بارد يُستهلك في أي مكان.
 *
 * القيم: null = قيد التحقق بعد الإقلاع · true = متصل · false = غير متصل.
 */
object ServerConnectionMonitor {

    private val _isConnected = MutableStateFlow<Boolean?>(null)

    /** null = غير معروف بعد · true = متصل · false = غير متصل (إعادة محاولة تلقائية جارية). */
    val isConnected: StateFlow<Boolean?> = _isConnected

    /** استُدعي من RedConnectionService عند نجاح اتصال الـWebSocket/الـAPI. */
    fun onConnected() {
        _isConnected.value = true
    }

    /** استُدعي عند بدء محاولة الاتصال — تبقى الحالة كما هي حتى الحسم. */
    fun onConnecting() {
        // لا تغيير: ننتظر النتيجة الفعلية كي لا يومض الشريط بلا داعٍ.
    }

    /** استُدعي عند الانقطاع أو رفض التفويض — الخدمة تعيد المحاولة تلقائياً. */
    fun onDisconnected() {
        _isConnected.value = false
    }
}
