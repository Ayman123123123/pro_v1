package com.red.sovereign.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * المضيف العالمي لشاشة مكالمة PSTN النشطة.
 *
 * كان الغائب القاتل: بعد قبول الوارد تنتهي شاشة الرنين ولا يظهر شيء
 * أثناء المكالمة النشطة (لا مؤقت ولا كتم ولا إنهاء). هذا المضيف:
 * - يجمع stateFlow من المثيل النشط (صادر أو وارد)
 * - يعرض Material3ExpressivePstnCallScreen عند ACTIVE/ENDED فقط
 *   (الحالات الانتقالية تبقى لشاشاتها الحالية: DialPad/IncomingActivity)
 * - يوصل الأزرار فعلياً بـ المثيل النشط عبر مرجع companion
 */
@Composable
fun PstnActiveCallHost(
    onDismiss: () -> Unit = {}
) {
    // التقط المرجع عند الدخول فقط — لا يعاد التركيب عند تغيره أثناء المكالمة
    val manager = remember { PstnWebRtcManager.activeUi }
    if (manager == null) return

    val state by manager.stateFlow.collectAsState()
    val scope = rememberCoroutineScope()

    // نعرض الشاشة النشطة في المكالمة الحيّة، **وكذلك في الوسائط المبكرة**:
    // تلك هي اللحظة التي يتكلّم فيها مزوّد الخدمة (رقم غير متاح، لا رصيد،
    // بريد صوتي، قائمة IVR)، والمستخدم يحتاج لوحة الأرقام ليتفاعل معها
    // وزرّ الإنهاء ليقطع. إخفاء الشاشة هنا كان يجعل الردّ الآلي مسموعًا
    // بلا أي تحكّم.
    val showScreen = state == PstnWebRtcManager.PstnCallState.ACTIVE ||
        state == PstnWebRtcManager.PstnCallState.EARLY_MEDIA ||
        state == PstnWebRtcManager.PstnCallState.ENDED
    if (!showScreen) return

    // عند ENDED أخفِ تلقائياً بعد ثانيتين
    LaunchedEffect(state) {
        if (state == PstnWebRtcManager.PstnCallState.ENDED) {
            delay(2000)
            onDismiss()
        }
    }

    Material3ExpressivePstnCallScreen(
        status = when (state) {
            PstnWebRtcManager.PstnCallState.ACTIVE -> PstnCallStatus.ACTIVE
            PstnWebRtcManager.PstnCallState.EARLY_MEDIA -> PstnCallStatus.EARLY_MEDIA
            else -> PstnCallStatus.ENDED
        },
        number = manager.remoteNumber ?: "",
        onMuteToggle = { muted ->
            runCatching { manager.isMuted = muted }
        },
        onSpeakerToggle = { speaker ->
            runCatching { manager.isSpeaker = speaker }
        },
        // لوحة الأرقام تُرسل النغمة فعليًا إلى الشبكة — كانت تكتب في نصّ
        // محلي ولا شيء يخرج، فقوائم المزوّد لم تكن قابلة للاستخدام.
        onDtmfDigit = { digit ->
            runCatching { manager.sendDtmf(digit) }
        },
        onHangup = {
            scope.launch { runCatching { manager.hangup() } }
        },
        onBack = onDismiss
    )
}
