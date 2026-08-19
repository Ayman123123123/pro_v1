package com.red.sovereign.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.red.sovereign.auth.PstnApi
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * غلاف شاشة مكالمة البوابة الغنيّة، يصلها بحالة التشغيل الحقيقية.
 *
 * كانت [Material3ExpressivePstnCallScreen] (710 أسطر) معزولة تماماً لا
 * يستدعيها أحد، لأن مراحل SIP التي تطلبها لم يكن لها مصدر. صار مصدرها
 * الآن إشارة `PSTN_PROGRESS` المشتقّة من أحداث Asterisk، فيعرضها هذا
 * الغلاف عند أي مكالمة بوابة جارية.
 *
 * الإنهاء يستدعي `POST /api/pstn/calls/{callId}/hangup` فعليًّا ليحرّر
 * منفذ GSM على البوابة؛ إغلاق الشاشة وحده كان سيترك المنفذ محجوزاً.
 */
@Composable
fun YounesPstnCallOverlay() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { PstnApi(TokenStore(context)) }
    var hangingUp by remember { mutableStateOf(false) }

    val status = CallRuntime.pstnStatus
    val number = CallRuntime.pstnNumber
    val callId = CallRuntime.pstnCallId

    // المرحلة النهائية تُعرض لحظات ثم تختفي الشاشة تلقائياً — نفس نمط
    // الحالات النهائية في المكالمة المشفَّرة، فلا يبقى المستخدم أمام
    // شاشة ميتة ولا تختفي النتيجة قبل أن يقرأها.
    LaunchedEffect(status) {
        if (status == PstnCallStatus.ENDED) {
            delay(CallUiState.TERMINAL_DISPLAY_MS)
            if (CallRuntime.pstnStatus == PstnCallStatus.ENDED) CallRuntime.clearPstn()
        }
    }

    val hangup: () -> Unit = {
        if (!hangingUp) {
            hangingUp = true
            val id = callId
            if (id.isBlank()) {
                // لم تصل إشارة تحمل المعرّف بعد: لا شيء يُنهى على الخادم
                CallRuntime.clearPstn()
            } else {
                scope.launch {
                    runCatching { api.hangup(id) }
                    CallRuntime.clearPstn()
                }
            }
        }
    }

    Material3ExpressivePstnCallScreen(
        status = status,
        number = number,
        onHangup = hangup,
        onBack = { CallRuntime.clearPstn() }
    )
}
