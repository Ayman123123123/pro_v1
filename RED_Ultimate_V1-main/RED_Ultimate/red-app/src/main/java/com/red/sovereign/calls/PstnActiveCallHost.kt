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

    // نعرض الشاشة النشطة فقط في هذه الحالات — لا ازدواج مع شاشات ما قبل الاتصال
    val showScreen = state == PstnWebRtcManager.PstnCallState.ACTIVE ||
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
            else -> PstnCallStatus.ENDED
        },
        number = manager.remoteNumber ?: "",
        onMuteToggle = { muted ->
            runCatching { manager.isMuted = muted }
        },
        onSpeakerToggle = { speaker ->
            runCatching { manager.isSpeaker = speaker }
        },
        onHangup = {
            scope.launch { runCatching { manager.hangup() } }
        },
        onBack = onDismiss
    )
}
