package com.red.sovereign.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.red.sovereign.ui.screens.ActiveCallScreen

/**
 * سجل شاشات المكالمات لكل نوع — Screen Registry per call type.
 *
 * ## نقطة الدخول الموصولة ليست هذا الملف
 *
 * الواجهة التي تعمل فعلاً هي [UnifiedCallOverlays] في `UnifiedCallOverlays.kt`،
 * ويستدعيها `ui/RedDashboard.kt` مرّتين (سطر 448 للشاشات الفرعية وسطر 553
 * للوحة الرئيسية). أما هذا الملف فلا يشير إليه أي كود في المشروع اليوم؛ يبقى
 * كسجلٍّ لكل نوع على حدة (فردية/جماعية/Zoom/مؤتمر/بث/بوابة) لمن يحتاج عرض
 * نوع واحد دون بقيّة الأنواع، وأسماء دواله محفوظة كما هي حتى لا ينكسر أي
 * استدعاء لاحق.
 *
 * ## لا استدعاءات إغلاق (`onDismiss`/`onBack`)
 *
 * كل الـoverlays الحقيقية بلا معاملات: تقرأ حالتها من الـRuntime المشترك
 * وتُغلق نفسها منه، فلا معنى لتمرير دوال إغلاق إليها. الإغلاق يجري عبر:
 *
 * - `CallRuntime.isMinimized` / `GroupCallRuntime.isMinimized` /
 *   `ZoomRuntime.isMinimized` للتصغير.
 * - `YounesCallService.action(…ACTION_END)` و`GroupCallService.end` و
 *   `ZoomGroupCallService.end` لإنهاء المكالمة (فتصبح الحالة `Idle`/`Ended`).
 * - [CallRuntime.clearPstn] لمكالمة البوابة — يستدعيها
 *   [YounesPstnCallOverlay] نفسه بعد `hangup` وبعد مهلة الحالة النهائية.
 *
 * تمرير `onDismiss` كان يعِد بشيء لا تنفّذه أي شاشة، ولهذا حُذف من كل الدوال
 * أدناه؛ الباقي `modifier` وحده لأن [ActiveCallScreen] هو الوحيد الذي يقبله.
 *
 * ## المكالمة الفردية والبوابة
 *
 * [CallScreens] يعرض المكالمة الفردية عبر [ActiveCallScreen] — نفس ما يستدعيه
 * `YounesCallOverlay(onDismiss)` في `CallRuntime.kt`، بينما [UnifiedCallOverlays]
 * يستدعي `YounesCallOverlay()` الأغنى (`CallOverlay.kt`). هذا الفرق مقصود:
 * [CallScreens] يعرض الشاشة وحدها لمن يضعها داخل تخطيط خاص به.
 *
 * ومكالمة البوابة (RED) لا تظهر في [UnifiedCallOverlays] إطلاقاً، فهذا الملف
 * هو الموضع الوحيد الذي يعرض [YounesPstnCallOverlay] عند وصله.
 */

/**
 * موجّه المكالمة الفردية — يعرض شاشة المكالمة عند وجود حالة غير [CallUiState.Idle].
 */
@Composable
fun CallScreens(modifier: Modifier = Modifier) {
    when (CallRuntime.state) {
        is CallUiState.Idle -> Unit
        else -> ActiveCallScreen(modifier = modifier)
    }
}

/**
 * موجّه المكالمة الجماعية — [GroupCallOverlay] يتولّى وضعه المصغّر داخلياً.
 */
@Composable
fun GroupCallScreens() {
    val state = GroupCallRuntime.state
    if (state is GroupCallUiState.Idle || state is GroupCallUiState.Ended) return
    GroupCallOverlay()
}

/**
 * موجّه اجتماع Zoom — [ZoomGroupCallOverlay] يتولّى وضعه المصغّر داخلياً.
 */
@Composable
fun ZoomCallScreens() {
    val state = ZoomRuntime.state
    if (state is ZoomUiState.Idle || state is ZoomUiState.Ended) return
    ZoomGroupCallOverlay()
}

/**
 * موجّه المؤتمر/المساحة الصوتية — الدعوة الواردة يعرضها الـoverlay نفسه.
 */
@Composable
fun ConferenceScreens() {
    if (ConferenceRuntime.state is ConferenceUiState.Idle) return
    YounesConferenceOverlay()
}

/** موجّه البث المباشر. */
@Composable
fun LiveStreamScreens() {
    if (LiveStreamRuntime.state is LiveStreamUiState.Idle) return
    YounesLiveStreamOverlay()
}

/**
 * موجّه مكالمة البوابة (RED/RED).
 *
 * [PstnCallStatus] تعداد لا تسلسل مغلق، فالمقارنة بـ`!=` لا بـ`is`.
 * الحرس هنا ضروري: [YounesPstnCallOverlay] لا يفحص [PstnCallStatus.IDLE]
 * بنفسه، فاستدعاؤه بلا شرط يرسم شاشة مكالمة وهمية بلا مكالمة.
 */
@Composable
fun PstnCallScreens() {
    if (CallRuntime.pstnStatus == PstnCallStatus.IDLE) return
    YounesPstnCallOverlay()
}

/**
 * موجّه موحّد — واجهة واحدة في كل لحظة.
 *
 * لا يعيد كتابة ترتيب الأولوية: يفوّضه إلى [UnifiedCallOverlays] نفسه فلا توجد
 * نسختان من الترتيب تتباعدان. المضاف هنا شيء واحد: مكالمة البوابة، وهي غير
 * مشمولة هناك إطلاقاً، وتُعرض فقط عندما تكون أنواع المكالمات الخمسة الأخرى
 * خاملة — فلا تتراكم واجهتان معاً.
 */
@Composable
fun UnifiedCallScreens(modifier: Modifier = Modifier) {
    val inAppCallActive = CallRuntime.state !is CallUiState.Idle ||
        ZoomRuntime.state !is ZoomUiState.Idle ||
        GroupCallRuntime.state !is GroupCallUiState.Idle ||
        ConferenceRuntime.state !is ConferenceUiState.Idle ||
        LiveStreamRuntime.state !is LiveStreamUiState.Idle

    Box(modifier = modifier.fillMaxSize()) {
        UnifiedCallOverlays()
        if (!inAppCallActive && CallRuntime.pstnStatus != PstnCallStatus.IDLE) {
            YounesPstnCallOverlay()
        }
    }
}
