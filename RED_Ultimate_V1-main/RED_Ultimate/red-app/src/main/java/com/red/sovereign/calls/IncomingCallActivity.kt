package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * شاشة واردة كاملة لكل أنواع المكالمات — من شاشة القفل.
 */
class IncomingCallActivity : ComponentActivity() {
    private val viewModel: IncomingCallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        bindIntent(intent)
        setContent {
            IncomingCallScreen(viewModel = viewModel, onFinish = { finish() })
        }
        lifecycleScope.launch {
            delay(CallRingPolicy.UNANSWERED_TIMEOUT_MS)
            if (!viewModel.isHandled) {
                viewModel.decline()
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindIntent(intent)
    }

    private fun bindIntent(intent: Intent?) {
        val callType = intent?.getStringExtra(EXTRA_CALL_TYPE) ?: CALL_TYPE_CONFERENCE
        viewModel.callType = callType
        when (callType) {
            CALL_TYPE_PSTN -> {
                viewModel.callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
                viewModel.peer = intent?.getStringExtra(EXTRA_PEER).orEmpty()
                viewModel.mode = "PSTN"
                viewModel.inviter = viewModel.peer
            }
            CALL_TYPE_1TO1 -> {
                viewModel.callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
                viewModel.peer = intent?.getStringExtra(EXTRA_PEER).orEmpty()
                viewModel.mode = intent?.getStringExtra(EXTRA_MODE) ?: "VOICE"
                viewModel.inviter = intent?.getStringExtra(EXTRA_INVITER).orEmpty().ifBlank { viewModel.peer }
            }
            CALL_TYPE_GROUP -> {
                viewModel.groupCallId = intent?.getStringExtra(GroupCallService.EXTRA_GROUP_CALL_ID).orEmpty()
                viewModel.myUserId = intent?.getStringExtra(GroupCallService.EXTRA_MY_USER_ID).orEmpty()
                viewModel.peer = intent?.getStringExtra(GroupCallService.EXTRA_HOST_NAME).orEmpty()
                viewModel.mode = if (intent?.getBooleanExtra(GroupCallService.EXTRA_IS_VIDEO, false) == true) "VIDEO" else "VOICE"
                viewModel.inviter = viewModel.peer
            }
            CALL_TYPE_LIVESTREAM -> {
                viewModel.streamId = intent?.getStringExtra(LiveStreamService.EXTRA_STREAM_ID).orEmpty()
                viewModel.userId = intent?.getStringExtra(LiveStreamService.EXTRA_USER_ID).orEmpty()
                viewModel.peer = intent?.getStringExtra(LiveStreamService.EXTRA_BROADCASTER_NAME).orEmpty()
                viewModel.mode = "VIDEO"
                viewModel.inviter = viewModel.peer
            }
            else -> {
                viewModel.roomId = intent?.getStringExtra(ConferenceService.EXTRA_ROOM_ID).orEmpty()
                viewModel.userId = intent?.getStringExtra(ConferenceService.EXTRA_USER_ID).orEmpty()
                viewModel.inviter = intent?.getStringExtra(ConferenceService.EXTRA_INVITER).orEmpty()
                viewModel.video = intent?.getBooleanExtra(ConferenceService.EXTRA_VIDEO, false) ?: false
                viewModel.mode = if (viewModel.video) "VIDEO" else "VOICE"
            }
        }
    }

    companion object {
        const val EXTRA_CALL_TYPE = "call_type"
        const val CALL_TYPE_1TO1 = "1to1"
        const val CALL_TYPE_PSTN = "pstn"
        const val CALL_TYPE_GROUP = "group"
        const val CALL_TYPE_CONFERENCE = "conference"
        const val CALL_TYPE_LIVESTREAM = "livestream"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_PEER = "peer"
        const val EXTRA_MODE = "mode"
        const val EXTRA_INVITER = "inviter"

        /** إطلاق شاشة رنين PSTN (مكالمة على شريحة المالك عبر DINSTAR). */
        fun launchPstn(context: Context, callId: String, peer: String) {
            context.startActivity(
                Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_CALL_TYPE, CALL_TYPE_PSTN)
                    putExtra(EXTRA_CALL_ID, callId)
                    putExtra(EXTRA_PEER, peer)
                    putExtra(EXTRA_MODE, "PSTN")
                }
            )
        }

        fun launch1to1(context: Context, callId: String, peer: String, mode: String, inviter: String = peer) {
            context.startActivity(
                Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_CALL_TYPE, CALL_TYPE_1TO1)
                    putExtra(EXTRA_CALL_ID, callId)
                    putExtra(EXTRA_PEER, peer)
                    putExtra(EXTRA_MODE, mode)
                    putExtra(EXTRA_INVITER, inviter)
                }
            )
        }

        fun launchGroup(context: Context, groupCallId: String, myUserId: String, hostName: String, isVideo: Boolean) {
            context.startActivity(
                Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_CALL_TYPE, CALL_TYPE_GROUP)
                    putExtra(GroupCallService.EXTRA_GROUP_CALL_ID, groupCallId)
                    putExtra(GroupCallService.EXTRA_MY_USER_ID, myUserId)
                    putExtra(GroupCallService.EXTRA_HOST_NAME, hostName)
                    putExtra(GroupCallService.EXTRA_IS_VIDEO, isVideo)
                }
            )
        }

        fun launchConference(context: Context, roomId: String, userId: String, inviter: String, video: Boolean) {
            context.startActivity(
                Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_CALL_TYPE, CALL_TYPE_CONFERENCE)
                    putExtra(ConferenceService.EXTRA_ROOM_ID, roomId)
                    putExtra(ConferenceService.EXTRA_USER_ID, userId)
                    putExtra(ConferenceService.EXTRA_INVITER, inviter)
                    putExtra(ConferenceService.EXTRA_VIDEO, video)
                }
            )
        }

        fun launchLive(context: Context, streamId: String, userId: String, broadcasterName: String) {
            context.startActivity(
                Intent(context, IncomingCallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_CALL_TYPE, CALL_TYPE_LIVESTREAM)
                    putExtra(LiveStreamService.EXTRA_STREAM_ID, streamId)
                    putExtra(LiveStreamService.EXTRA_USER_ID, userId)
                    putExtra(LiveStreamService.EXTRA_BROADCASTER_NAME, broadcasterName)
                }
            )
        }
    }
}

class IncomingCallViewModel(application: android.app.Application) : AndroidViewModel(application) {
    var callType = IncomingCallActivity.CALL_TYPE_CONFERENCE
    var inviter = ""
    var mode = "VOICE"
    var isHandled = false
    var callId = ""
    var peer = ""
    var groupCallId = ""
    var myUserId = ""
    var roomId = ""
    var userId = ""
    var video = false
    var streamId = ""
    /** هل تم قبول مكالمة PSTN وينتظر انتقالها للحالة النشطة */
    var pstnAccepting by androidx.compose.runtime.mutableStateOf(false)

    fun accept(withVideo: Boolean) {
        isHandled = true
        val app = getApplication<android.app.Application>()
        when (callType) {
            // مكالمة PSTN واردة على شريحة المالك: القبول يمر عبر منسق /ws/pstn
            // (PSTN_ACCEPT → AMI Redirect) ثم المستمع المسجَّل يجيب بـ 200 OK.
            IncomingCallActivity.CALL_TYPE_PSTN -> {
                pstnAccepting = true
                val ok = PstnIncomingCallCoordinator.active?.acceptIncoming() ?: false
                if (!ok) {
                    // فشل إرسال PSTN_ACCEPT (WebSocket مقطوع) — أبلغ المستخدم
                    android.util.Log.w("PstnIncoming", "PSTN_ACCEPT failed, coordinator not active")
                    pstnAccepting = false
                }
                // لا ننهي الـ Activity هنا — ننتظر انتقال PstnWebRtcManager إلى ACTIVE
                // عبر مراقبة الحالة في الـ Composable (LaunchedEffect)
            }
            IncomingCallActivity.CALL_TYPE_1TO1 -> {
                YounesCallService.accept(app, cameraOn = withVideo && mode == "VIDEO", micOn = true)
            }
            IncomingCallActivity.CALL_TYPE_GROUP -> {
                val uid = myUserId.ifBlank { TokenStore(app).redId.orEmpty() }
                GroupCallService.accept(app, groupCallId, uid, mode == "VIDEO" || withVideo)
            }
            IncomingCallActivity.CALL_TYPE_LIVESTREAM -> {
                val uid = userId.ifBlank { TokenStore(app).redId.orEmpty() }
                LiveStreamService.start(app, streamId, uid, isBroadcaster = false)
            }
            else -> {
                val uid = userId.ifBlank { TokenStore(app).redId.orEmpty() }
                ConferenceService.join(app, roomId, uid, video = withVideo && (video || mode == "VIDEO"), asHost = false)
            }
        }
    }

    fun decline() {
        isHandled = true
        val app = getApplication<android.app.Application>()
        when (callType) {
            IncomingCallActivity.CALL_TYPE_PSTN -> PstnIncomingCallCoordinator.active?.rejectIncoming()
            IncomingCallActivity.CALL_TYPE_1TO1 -> YounesCallService.action(app, YounesCallService.ACTION_REJECT)
            IncomingCallActivity.CALL_TYPE_GROUP -> GroupCallService.decline(app, groupCallId)
            IncomingCallActivity.CALL_TYPE_LIVESTREAM -> LiveStreamService.stop(app)
            else -> ConferenceService.leave(app)
        }
    }
}

@Composable
fun IncomingCallScreen(viewModel: IncomingCallViewModel, onFinish: () -> Unit) {
    val inviterName = remember(viewModel.inviter) { viewModel.inviter.ifBlank { viewModel.peer }.take(12) }
    var showVideoToggle by remember { mutableStateOf(viewModel.mode == "VIDEO" || viewModel.video) }
    val isVideoCapable = viewModel.mode == "VIDEO" || viewModel.video ||
        viewModel.callType == IncomingCallActivity.CALL_TYPE_LIVESTREAM
    val isPstn = viewModel.callType == IncomingCallActivity.CALL_TYPE_PSTN

    // لـ PSTN: راقب انتقال المكالمة إلى ACTIVE — عندها أغلق شاشة الرنين واعرض شاشة المكالمة النشطة
    if (isPstn && viewModel.pstnAccepting) {
        val pstnManager = remember { PstnWebRtcManager.incoming(viewModel.getApplication()) }
        val pstnState by pstnManager.stateFlow.collectAsState()
        LaunchedEffect(pstnState) {
            if (pstnState == PstnWebRtcManager.PstnCallState.ACTIVE) {
                viewModel.pstnAccepting = false
                onFinish()
            }
        }
    }

    // شاشة انتظار بعد قبول PSTN (قبل انتقال الصوت)
    if (isPstn && viewModel.pstnAccepting) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF060D1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF00C98C))
                Spacer(Modifier.height(16.dp))
                Text("جاري توصيل المكالمة...", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(inviterName, color = Color.White.copy(0.6f), fontSize = 14.sp)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF060D1A))) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (viewModel.callType) {
                        IncomingCallActivity.CALL_TYPE_1TO1 -> "مكالمة واردة"
                        IncomingCallActivity.CALL_TYPE_GROUP -> "مكالمة جماعية واردة"
                        IncomingCallActivity.CALL_TYPE_LIVESTREAM -> "بث مباشر"
                        else -> "دعوة مؤتمر / مساحة"
                    },
                    color = Color.White.copy(0.6f),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(inviterName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isVideoCapable) "فيديو" else "صوت",
                    color = Color(0xFF00C98C),
                    fontSize = 16.sp
                )
            }

            Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "pulseScale"
                )
                Box(Modifier.size(140.dp).scale(pulse).clip(CircleShape).background(Color(0x3300C98C)))
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF00C98C), Color(0xFF003023)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(inviterName.take(1).uppercase(), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isVideoCapable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("فيديو", color = Color.White.copy(0.7f), fontSize = 14.sp)
                        Switch(
                            checked = showVideoToggle,
                            onCheckedChange = { showVideoToggle = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00C98C),
                                checkedTrackColor = Color(0xFF00C98C).copy(0.4f)
                            )
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { viewModel.decline(); onFinish() }) {
                                Icon(Icons.Default.CallEnd, contentDescription = "رفض", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("رفض", color = Color.White.copy(0.6f), fontSize = 12.sp)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (showVideoToggle && isVideoCapable) Color(0xFF00C98C) else Color(0xFF2196F3)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = {
                                val isPstn = viewModel.callType == IncomingCallActivity.CALL_TYPE_PSTN
                                viewModel.accept(withVideo = showVideoToggle && isVideoCapable)
                                // لـ PSTN: لا نغلق الشاشة فوراً — ننتظر انتقال WebRTC إلى ACTIVE (يُظهر شاشة "جاري التوصيل")
                                if (!isPstn) onFinish()
                            }) {
                                Icon(
                                    if (showVideoToggle && isVideoCapable) Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "قبول",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("قبول", color = Color.White.copy(0.6f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
