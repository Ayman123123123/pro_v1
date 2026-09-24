package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

/**
 * شريط مصغّر موحّد لكل أنواع المكالمات الخمسة:
 * 1:1 وZoom وGroup وConference وLive.
 * يعرض: نوع المكالمة / الاسم / المدة / زر عودة / زر إنهاء.
 * الأولوية مطابقة لـ UnifiedCallOverlays: 1:1 > Zoom > Group > Conference > Live.
 */
private data class MinimizedInfo(
    val typeLabel: String,
    val title: String,
    val startedAt: Long,
    val onReturn: () -> Unit,
    val onEnd: () -> Unit
)

@Composable
fun MinimizedCallBar() {
    val context = LocalContext.current
    val callState = CallRuntime.state
    val zoomState = ZoomRuntime.state
    val groupState = GroupCallRuntime.state
    val confState = ConferenceRuntime.state
    val liveState = LiveStreamRuntime.state

    val info: MinimizedInfo? = when {
        // 1) فردية 1:1
        CallRuntime.isMinimized && (callState is CallUiState.Active || callState is CallUiState.ActiveWithIncoming) -> {
            val active = (callState as? CallUiState.Active)
                ?: (callState as? CallUiState.ActiveWithIncoming)?.active
            if (active == null) null else MinimizedInfo(
                typeLabel = if (active.mode.contains("VIDEO", ignoreCase = true)) "مكالمة فيديو" else "مكالمة صوتية",
                title = active.peer.ifBlank { "يونس" },
                startedAt = active.startedAt,
                onReturn = { CallRuntime.isMinimized = false },
                onEnd = {
                    CallRuntime.isMinimized = false
                    YounesCallService.action(context, YounesCallService.ACTION_END)
                }
            )
        }
        // 2) Zoom
        ZoomRuntime.isMinimized && zoomState is ZoomUiState.Active -> MinimizedInfo(
            typeLabel = "اجتماع Zoom",
            title = zoomState.title.ifBlank { ZoomRuntime.meetingTitle.ifBlank { zoomState.meetingId } },
            startedAt = zoomState.startedAt,
            onReturn = { ZoomRuntime.isMinimized = false },
            onEnd = {
                ZoomRuntime.isMinimized = false
                ZoomGroupCallService.end(context)
            }
        )
        // 3) جماعية
        GroupCallRuntime.isMinimized && groupState is GroupCallUiState.Active -> {
            val joined = groupState.members.count { it.status == GroupCallMemberStatus.JOINED } + 1
            MinimizedInfo(
                typeLabel = if (groupState.isVideo) "فيديو جماعي" else "صوت جماعي",
                title = GroupCallRuntime.activeGroupName.ifBlank { groupState.groupCallId } + " · $joined",
                startedAt = groupState.startedAt,
                onReturn = { GroupCallRuntime.isMinimized = false },
                onEnd = {
                    GroupCallRuntime.isMinimized = false
                    GroupCallService.end(context)
                }
            )
        }
        // 4) مؤتمر/مساحة
        ConferenceRuntime.isMinimized && confState is ConferenceUiState.Active -> MinimizedInfo(
            typeLabel = if (ConferenceRuntime.isVideoEnabled) "مؤتمر فيديو" else "مساحة صوتية",
            title = confState.roomId.take(12).ifBlank { "مؤتمر" },
            startedAt = confState.startedAt,
            onReturn = { ConferenceRuntime.isMinimized = false },
            onEnd = {
                ConferenceRuntime.isMinimized = false
                ConferenceService.leave(context)
            }
        )
        // 5) بث مباشر
        LiveStreamRuntime.isMinimized && liveState is LiveStreamUiState.Active -> MinimizedInfo(
            typeLabel = "بث مباشر",
            title = liveState.streamId.take(12).ifBlank { "بث" },
            startedAt = liveState.startedAt,
            onReturn = { LiveStreamRuntime.isMinimized = false },
            onEnd = {
                LiveStreamRuntime.isMinimized = false
                LiveStreamService.stop(context)
            }
        )
        else -> null
    }
    if (info == null) return

    Popup(
        alignment = Alignment.BottomCenter,
        offset = androidx.compose.ui.unit.IntOffset(0, -120)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.96f),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clickable { info.onReturn() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00C98C))
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(info.typeLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        info.title,
                        color = Color.White.copy(0.75f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (info.startedAt > 0L) CallElapsedTimer(info.startedAt, Color.White.copy(0.7f))
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(0.1f))
                        .clickable { info.onReturn() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.KeyboardArrowUp, null,
                            tint = Color.White, modifier = Modifier.size(16.dp)
                        )
                        Text("عودة", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE53935))
                        .clickable { info.onEnd() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Default.CallEnd, null,
                        tint = Color.White, modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * بانر انتظار للمكالمة الواردة الثانية بدل إخفائها.
 * يُعرض فوق أي واجهة (كاملة أو مصغّرة) عند وجود وارد ثانٍ من نوع مختلف.
 * ملاحظة عرض فقط: القبول/الرفض يتم من الواجهة الكاملة أو الإشعار.
 */
@Composable
fun SecondIncomingBanner() {
    // ActiveWithIncoming تُعرض بانرها داخلياً في YounesCallOverlay — لا تكرار.
    if (CallRuntime.state is CallUiState.ActiveWithIncoming) return

    val callState = CallRuntime.state
    val zoomState = ZoomRuntime.state
    val groupState = GroupCallRuntime.state
    val confState = ConferenceRuntime.state
    val liveState = LiveStreamRuntime.state

    // الواجهة الأساسية المعروضة (نفس أولوية UnifiedCallOverlays).
    val primary: String? = when {
        callState !is CallUiState.Idle -> "one2one"
        zoomState !is ZoomUiState.Idle && zoomState !is ZoomUiState.Ended -> "zoom"
        groupState !is GroupCallUiState.Idle && groupState !is GroupCallUiState.Ended -> "group"
        confState !is ConferenceUiState.Idle -> "conf"
        liveState !is LiveStreamUiState.Idle -> "live"
        else -> null
    }
    if (primary == null) return

    // أول وارد ثانٍ ليس هو الأساسي.
    val second: Pair<String, String>? = when {
        primary != "one2one" && callState is CallUiState.Incoming ->
            "مكالمة فردية" to callState.peer.ifBlank { "يونس" }
        primary != "zoom" && zoomState is ZoomUiState.Incoming ->
            "اجتماع Zoom" to zoomState.hostName.ifBlank { zoomState.meetingId }
        primary != "group" && groupState is GroupCallUiState.IncomingGroup ->
            "مكالمة جماعية" to groupState.hostName.ifBlank { groupState.groupCallId }
        primary != "conf" && confState is ConferenceUiState.Incoming ->
            "مؤتمر" to confState.inviter.ifBlank { confState.roomId }
        primary != "live" && liveState is LiveStreamUiState.Incoming ->
            "بث مباشر" to liveState.broadcasterName.ifBlank { liveState.streamId }
        else -> null
    }
    if (second == null) return

    Popup(
        alignment = Alignment.TopCenter,
        offset = androidx.compose.ui.unit.IntOffset(0, 80)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF3D2E00).copy(alpha = 0.97f),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF5C842))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("مكالمة واردة ثانية · ${second.first}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        second.second,
                        color = Color.White.copy(0.8f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("انتظار", color = Color(0xFFF5C842), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
