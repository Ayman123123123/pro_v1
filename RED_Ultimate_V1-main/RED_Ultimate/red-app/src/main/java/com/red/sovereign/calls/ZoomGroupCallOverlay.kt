package com.red.sovereign.calls

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val ZoomBlue = Color(0xFF2AABEE)
private val ZoomDark = Color(0xFF0B1426)

@Composable
fun ZoomGroupCallOverlay() {
    val state = ZoomRuntime.state
    if (state is ZoomUiState.Idle || state is ZoomUiState.Ended) return
    BackHandler { }
    if (ZoomRuntime.isMinimized && state is ZoomUiState.Active) {
        ZoomMinimizedBar(state); return
    }
    Dialog(onDismissRequest={}, properties=DialogProperties(usePlatformDefaultWidth=false, dismissOnBackPress=false, dismissOnClickOutside=false)){
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1426), Color(0xFF02070E))))){
            when(state){
                is ZoomUiState.Incoming -> ZoomIncomingPanel(state)
                is ZoomUiState.WaitingRoom -> ZoomWaitingPanel(state)
                is ZoomUiState.Ringing -> ZoomRingingPanel(state)
                is ZoomUiState.Active -> ZoomActivePanel(state)
                else->{}
            }
        }
    }
}

@Composable
private fun ZoomMinimizedBar(state: ZoomUiState.Active){
    val context = LocalContext.current
    val joined = state.members.count{it.status==ZoomMemberStatus.JOINED}+1
    androidx.compose.ui.window.Popup(alignment=Alignment.BottomEnd, offset=androidx.compose.ui.unit.IntOffset(24,-160)){
        Surface(shape=RoundedCornerShape(16.dp), color=Color(0xFF0F172A).copy(0.96f), border=androidx.compose.foundation.BorderStroke(1.dp, ZoomBlue.copy(0.5f)), shadowElevation=8.dp, modifier=Modifier.width(180.dp).clickable{ ZoomRuntime.isMinimized=false}){
            Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(6.dp)){
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(8.dp).clip(CircleShape).background(ZoomBlue))
                    Text("اجتماع Zoom", color=Color.White, fontSize=12.sp, fontWeight=FontWeight.Bold)
                }
                Text("$joined مشارك · ${ZoomRuntime.meetingTitle}", color=Color.White.copy(0.7f), fontSize=10.sp, maxLines=1)
                if(state.isVideo && ZoomRuntime.localVideo!=null && ZoomRuntime.eglContext!=null){
                    val egl=ZoomRuntime.eglContext!!; val track=ZoomRuntime.localVideo!!
                    androidx.compose.runtime.key(track, egl){
                        var r: SurfaceViewRenderer? by remember{mutableStateOf(null)}
                        AndroidView(factory={ctx-> SurfaceViewRenderer(ctx).apply{ init(egl,null); setMirror(true); setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL); setEnableHardwareScaler(true); r=this; track.addSink(this)}}, update={v-> if(r==v) track.addSink(v)}, onRelease={v-> track.removeSink(v); v.release()}, modifier=Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(10.dp)))
                        DisposableEffect(track, r){ onDispose{ r?.let{track.removeSink(it)}}}
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.08f)).clickable{ ZoomGroupCallService.action(context, ZoomGroupCallService.ACTION_TOGGLE_MIC)}.padding(vertical=6.dp), contentAlignment=Alignment.Center){ Icon(if(ZoomRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint=Color.White, modifier=Modifier.size(16.dp)) }
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFE53935)).clickable{ ZoomGroupCallService.end(context); ZoomRuntime.isMinimized=false}.padding(horizontal=14.dp, vertical=6.dp), contentAlignment=Alignment.Center){ Icon(Icons.Default.CallEnd, null, tint=Color.White, modifier=Modifier.size(16.dp))}
                }
            }
        }
    }
}

@Composable
private fun ZoomActivePanel(state: ZoomUiState.Active){
    val context=LocalContext.current
    var showSheet by remember{mutableStateOf(false)}
    var showRecordConsent by remember{mutableStateOf(false)}
    var isSpeakerView by remember{mutableStateOf(false)}
    if(ZoomRuntime.isScreenSharing){
        Box(Modifier.fillMaxWidth().background(ZoomBlue).padding(8.dp), contentAlignment=Alignment.Center){
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.ScreenShare, null, tint=Color(0xFF002118), modifier=Modifier.size(14.dp))
                Text("أنت تشارك شاشتك", color=Color(0xFF002118), fontSize=12.sp, fontWeight=FontWeight.Bold)
                Text("إيقاف", color=Color(0xFF002118), fontSize=11.sp, modifier=Modifier.clickable{ ZoomGroupCallService.stopScreenShare(context)})
            }
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), verticalArrangement=Arrangement.SpaceBetween){
        Box(Modifier.clickable{ showSheet=true}){ ZoomHeader(state) }
        // 📷 إشعار الكاميرا: فيديو مطلوب لكن المسار فارغ — إعادة محاولة فورية
        if(state.isVideo && !ZoomRuntime.isScreenSharing && ZoomRuntime.localVideo==null && ZoomRuntime.eglContext!=null){
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=4.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE53935).copy(0.15f)).border(1.dp, Color(0xFFE53935).copy(0.4f), RoundedCornerShape(12.dp)).padding(horizontal=12.dp, vertical=8.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.VideocamOff, null, tint=Color(0xFFE53935), modifier=Modifier.size(14.dp))
                    Text("تعذر فتح الكاميرا", color=Color.White.copy(0.9f), fontSize=11.sp)
                }
                Text("إعادة المحاولة", color=ZoomBlue, fontSize=11.sp, fontWeight=FontWeight.Bold, modifier=Modifier.clickable{ ZoomGroupCallService.action(context, ZoomGroupCallService.ACTION_TOGGLE_VIDEO)})
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)){
            if(state.isVideo) ZoomVideoGrid(state, isSpeakerView, {isSpeakerView=!isSpeakerView}) else ZoomVoiceGrid(state)
        }
        ZoomControlIsland(state, isSpeakerView, {isSpeakerView=!isSpeakerView}, {if(ZoomRuntime.isRecording) ZoomGroupCallService.action(context, "STOP_RECORDING") else showRecordConsent=true})
    }
    if(showSheet) ZoomParticipantsSheet(state){ showSheet=false }
    if(showRecordConsent){
        AlertDialog(onDismissRequest={showRecordConsent=false}, title={Text("تسجيل اجتماع Zoom", fontWeight=FontWeight.Bold)}, text={Text("سيُسجَّل الصوت بتشفير AES-GCM.")}, confirmButton={TextButton({showRecordConsent=false; ZoomGroupCallService.action(context, "START_RECORDING")}){Text("موافق", color=ZoomBlue)}}, dismissButton={TextButton({showRecordConsent=false}){Text("إلغاء")}})
    }
}

@Composable
private fun ZoomHeader(state: ZoomUiState.Active){
    val joined=state.members.count{it.status==ZoomMemberStatus.JOINED}+1
    val quality=ZoomRuntime.networkStats.quality
    val qualityColor=when(quality){ NetworkStats.Quality.EXCELLENT, NetworkStats.Quality.GOOD->ZoomBlue; NetworkStats.Quality.FAIR->Color(0xFFFFC107); NetworkStats.Quality.POOR->Color(0xFFE53935); else->Color.Gray }
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=10.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(8.dp).clip(CircleShape).background(qualityColor))
            Column{
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically){
                    Text(ZoomRuntime.meetingTitle.ifBlank{"اجتماع Zoom"}, color=Color.White, fontSize=14.sp, fontWeight=FontWeight.Bold, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f,false))
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(ZoomBlue).padding(horizontal=5.dp, vertical=2.dp)){ Text(ZoomRuntime.meetingId.take(9), color=Color(0xFF002118), fontSize=9.sp, fontWeight=FontWeight.Bold)}
                    if(ZoomRuntime.isScreenSharing) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(ZoomBlue).padding(horizontal=4.dp, vertical=1.dp)){ Text("شاشة", color=Color(0xFF002118), fontSize=9.sp)}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically){
                    Text("Zoom · $joined مشارك", color=Color.White.copy(0.7f), fontSize=11.sp)
                    ZoomElapsedTimer(state.startedAt)
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(0.12f)).padding(horizontal=10.dp, vertical=4.dp)){ Text("$joined", color=Color.White, fontSize=12.sp, fontWeight=FontWeight.Bold)}
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(ZoomBlue.copy(0.18f)).padding(horizontal=8.dp,vertical=4.dp)){ Text("🔒 E2EE", color=ZoomBlue, fontSize=10.sp, fontWeight=FontWeight.Bold)}
        }
    }
}

@Composable
private fun ZoomElapsedTimer(startedAt: Long){
    var elapsed by remember{mutableStateOf(0L)}
    LaunchedEffect(startedAt){ while(true){ elapsed=(System.currentTimeMillis()-startedAt).coerceAtLeast(0L); delay(1000)}}
    val secs=(elapsed/1000)%60; val mins=(elapsed/1000)/60
    Text(String.format("%02d:%02d", mins, secs), color=Color.White.copy(0.85f), fontSize=11.sp)
}

@Composable
private fun ZoomVoiceGrid(state: ZoomUiState.Active){
    val joined=state.members.filter{it.status==ZoomMemberStatus.JOINED}
    val speaker=joined.firstOrNull{!it.isMuted}?.userId
    LazyVerticalGrid(columns=GridCells.Fixed(2), verticalArrangement=Arrangement.spacedBy(16.dp), horizontalArrangement=Arrangement.spacedBy(16.dp), contentPadding=PaddingValues(16.dp), modifier=Modifier.fillMaxSize()){
        item(key="self"){ ZoomAvatarTile("أنت", "أن", ZoomRuntime.isMuted, !ZoomRuntime.isMuted && speaker==null, true)}
        items(joined, key={it.userId}){ m-> ZoomAvatarTile(m.displayName, m.displayName.take(2).uppercase(), m.isMuted, m.userId==speaker, false)}
    }
}

@Composable
private fun ZoomAvatarTile(label: String, initial: String, isMuted: Boolean, isSpeaking: Boolean, isSelf: Boolean){
    val inf=rememberInfiniteTransition(label="z_$label")
    val pulse by inf.animateFloat(initialValue=1f, targetValue=if(isSpeaking)1.08f else 1f, animationSpec=infiniteRepeatable(tween(700), RepeatMode.Reverse), label="p")
    Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(8.dp)){
        Box(contentAlignment=Alignment.Center, modifier=Modifier.size(96.dp)){
            if(isSpeaking){ Box(Modifier.size(96.dp).scale(pulse).clip(CircleShape).background(ZoomBlue.copy(0.18f))); Box(Modifier.size(86.dp).scale(pulse*0.96f).clip(CircleShape).background(ZoomBlue.copy(0.12f)))}
            Box(Modifier.size(74.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F172A)))).border(if(isSpeaking)2.dp else 1.dp, if(isSpeaking) ZoomBlue else Color.White.copy(0.12f), CircleShape), contentAlignment=Alignment.Center){ Text(initial.uppercase(), color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Bold)}
            if(isMuted) Box(Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(Color(0xFFE53935)).border(2.dp, Color(0xFF02070E), CircleShape), contentAlignment=Alignment.Center){ Icon(Icons.Default.MicOff, null, tint=Color.White, modifier=Modifier.size(14.dp))}
        }
        Text(label.take(14), color=Color.White, fontSize=12.sp, maxLines=1, overflow=TextOverflow.Ellipsis, fontWeight=if(isSelf) FontWeight.Bold else FontWeight.Medium)
        Text(if(isMuted) "مكتوم" else if(isSpeaking) "يتحدث..." else "متصل", color=if(isSpeaking) ZoomBlue else Color.White.copy(0.55f), fontSize=10.sp)
    }
}

@Composable
private fun ZoomVideoGrid(state: ZoomUiState.Active, isSpeakerView: Boolean, onToggleView:()->Unit){
    val remote=ZoomRuntime.remoteVideos
    val local=ZoomRuntime.localVideo
    val joined=state.members.filter{it.status==ZoomMemberStatus.JOINED}
    val speaker=joined.firstOrNull()
    Box(Modifier.fillMaxSize()){
        if(isSpeakerView && speaker!=null){
            Column(Modifier.fillMaxSize()){
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp))){
                    ZoomVideoTile(speaker.displayName, remote[speaker.userId], speaker.isMuted, false, ZoomRuntime.eglContext, true)
                    Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(8.dp)).background(ZoomBlue).padding(horizontal=8.dp, vertical=3.dp)){ Text("يتحدث", color=Color(0xFF002118), fontSize=10.sp, fontWeight=FontWeight.Bold)}
                }
                Row(Modifier.fillMaxWidth().height(110.dp).padding(top=8.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))){ ZoomVideoTile("أنت", local, ZoomRuntime.isMuted, true, ZoomRuntime.eglContext, true)}
                    joined.filter{it.userId!=speaker.userId}.take(3).forEach{ m-> Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))){ ZoomVideoTile(m.displayName, remote[m.userId], m.isMuted, false, ZoomRuntime.eglContext, true)}}
                }
            }
        } else {
            val total=1+joined.size
            val cols=when{ total<=1->1; total<=4->2; total<=9->3; total<=16->4; else->5 }
            LazyVerticalGrid(columns=GridCells.Fixed(cols), verticalArrangement=Arrangement.spacedBy(8.dp), horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxSize()){
                item(key="local"){ ZoomVideoTile("أنت", local, ZoomRuntime.isMuted, true, ZoomRuntime.eglContext, false)}
                items(joined, key={it.userId}){ m-> ZoomVideoTile(m.displayName, remote[m.userId], m.isMuted, false, ZoomRuntime.eglContext, false)}
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(0.45f)).clickable{ onToggleView()}.padding(horizontal=10.dp, vertical=6.dp)){
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically){
                Icon(if(isSpeakerView) Icons.Default.GridView else Icons.Default.Person, null, tint=Color.White, modifier=Modifier.size(14.dp))
                Text(if(isSpeakerView) "الشبكة" else "المتحدث", color=Color.White, fontSize=11.sp)
            }
        }
    }
}

@Composable
private fun ZoomControlIsland(state: ZoomUiState.Active, isSpeakerView: Boolean, onToggleSpeakerView:()->Unit, onRecordClick:()->Unit){
    val context=LocalContext.current
    val screenLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){ res-> if(res.resultCode==Activity.RESULT_OK && res.data!=null) ZoomGroupCallService.startScreenShare(context, res.data!!)}
    Column(Modifier.fillMaxWidth().padding(top=10.dp, bottom=18.dp, start=16.dp, end=16.dp), verticalArrangement=Arrangement.spacedBy(12.dp), horizontalAlignment=Alignment.CenterHorizontally){
        if(ZoomRuntime.isHost && state.members.any{it.status==ZoomMemberStatus.JOINED}){
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E293B)).border(1.dp, ZoomBlue.copy(0.2f), RoundedCornerShape(12.dp)).padding(horizontal=14.dp, vertical=8.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically){ Icon(Icons.Default.AdminPanelSettings, null, tint=ZoomBlue, modifier=Modifier.size(14.dp)); Text("المضيف", color=ZoomBlue, fontSize=11.sp, fontWeight=FontWeight.Bold)}
                Text("كتم الكل", color=Color.White.copy(0.85f), fontSize=11.sp, modifier=Modifier.clickable{ ZoomGroupCallService.action(context, ZoomGroupCallService.ACTION_MUTE_ALL)})
            }
        }
        Box(Modifier.clip(RoundedCornerShape(32.dp)).background(Color(0xFF1A2332).copy(0.92f)).border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(32.dp)).padding(horizontal=12.dp, vertical=10.dp)){
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp), verticalAlignment=Alignment.CenterVertically){
                ZoomIslandBtn(if(ZoomRuntime.isMuted) Icons.Default.MicOff else Icons.Default.Mic, if(ZoomRuntime.isMuted) Color(0xFFE53935) else Color.White.copy(0.14f)){ ZoomGroupCallService.action(context, ZoomGroupCallService.ACTION_TOGGLE_MIC)}
                ZoomIslandBtn(Icons.Default.KeyboardArrowDown, Color.White.copy(0.14f)){ ZoomRuntime.isMinimized=true}
                if(state.isVideo){
                    ZoomIslandBtn(if(ZoomRuntime.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, if(!ZoomRuntime.isVideoEnabled) Color(0xFFE53935) else Color.White.copy(0.14f)){ ZoomGroupCallService.action(context, ZoomGroupCallService.ACTION_TOGGLE_VIDEO)}
                    ZoomIslandBtn(Icons.Default.Cameraswitch, Color.White.copy(0.14f)){ ZoomGroupCallService.action(context, ZoomGroupCallService.ACTION_SWITCH_CAMERA)}
                    ZoomIslandBtn(Icons.Default.ScreenShare, if(ZoomRuntime.isScreenSharing) ZoomBlue else Color.White.copy(0.14f), if(ZoomRuntime.isScreenSharing) Color(0xFF002118) else Color.White){
                        if(ZoomRuntime.isScreenSharing) ZoomGroupCallService.stopScreenShare(context)
                        else {
                            val mgr=context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                            val intent=if(android.os.Build.VERSION.SDK_INT>=34) mgr.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()) else mgr.createScreenCaptureIntent()
                            screenLauncher.launch(intent)
                        }
                    }
                    ZoomIslandBtn(if(ZoomRuntime.isHandRaised) Icons.Default.BackHand else Icons.Default.PanTool, if(ZoomRuntime.isHandRaised) ZoomBlue else Color.White.copy(0.14f)){ ZoomGroupCallService.action(context, ZoomGroupCallService.ACTION_RAISE_HAND)}
                } else {
                    ZoomIslandBtn(Icons.Default.VolumeUp, Color.White.copy(0.14f)){ ZoomGroupCallService.action(context, ZoomGroupCallService.ACTION_TOGGLE_SPEAKER)}
                }
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFE53935)).clickable{ ZoomGroupCallService.end(context)}, contentAlignment=Alignment.Center){ Icon(Icons.Default.CallEnd, null, tint=Color.White, modifier=Modifier.size(26.dp))}
            }
        }
        Text("اجتماع Zoom · ${ZoomRuntime.meetingId} · مشفّر", color=Color.White.copy(0.45f), fontSize=10.sp)
    }
}

@Composable private fun ZoomIslandBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, tint: Color=Color.White, onClick:()->Unit){
    Box(Modifier.size(48.dp).clip(CircleShape).background(bg).clickable(onClick=onClick), contentAlignment=Alignment.Center){ Icon(icon, null, tint=tint, modifier=Modifier.size(22.dp))}
}

@Composable
private fun ZoomIncomingPanel(state: ZoomUiState.Incoming){
    val context=LocalContext.current
    val myId=remember{ com.red.sovereign.auth.TokenStore(context).redId.orEmpty() }
    val groupName=ZoomRuntime.meetingTitle.ifBlank{"اجتماع Zoom"}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.SpaceBetween){
        Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(6.dp)){
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(ZoomBlue.copy(0.15f)).border(1.dp, ZoomBlue.copy(0.25f), RoundedCornerShape(20.dp)).padding(horizontal=14.dp, vertical=6.dp)){ Text(if(state.isVideo) "📹 اجتماع Zoom وارد" else "📞 اجتماع Zoom وارد", color=ZoomBlue, fontSize=12.sp, fontWeight=FontWeight.Bold)}
            Text(groupName, color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Black, maxLines=1, overflow=TextOverflow.Ellipsis)
            Text("${state.hostName.ifBlank{state.hostId}} يدعوك", color=Color.White.copy(0.85f), fontSize=14.sp)
            Text("اجتماع: ${state.meetingId}", color=ZoomBlue, fontSize=12.sp, fontWeight=FontWeight.Bold)
            if(state.otherIds.isNotEmpty()) Text("+ ${state.otherIds.size} آخرون", color=Color.White.copy(0.55f), fontSize=12.sp)
        }
        Box(Modifier.size(100.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F172A)))).border(2.dp, ZoomBlue, CircleShape), contentAlignment=Alignment.Center){ Text(groupName.take(2).uppercase().ifBlank{"ZM"}, color=Color.White, fontSize=28.sp, fontWeight=FontWeight.Black)}
        Column(verticalArrangement=Arrangement.spacedBy(12.dp), modifier=Modifier.fillMaxWidth()){
            Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(ZoomBlue).clickable{ ZoomGroupCallService.accept(context, state.meetingId, myId, state.isVideo, state.hostId)}, contentAlignment=Alignment.Center){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically){ Icon(if(state.isVideo) Icons.Default.Videocam else Icons.Default.Call, null, tint=Color(0xFF002118), modifier=Modifier.size(20.dp)); Text("قبول", color=Color(0xFF002118), fontSize=16.sp, fontWeight=FontWeight.Bold)}
            }
            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(0.10f)).border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(28.dp)).clickable{ ZoomGroupCallService.decline(context, state.meetingId)}, contentAlignment=Alignment.Center){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically){ Icon(Icons.Default.CallEnd, null, tint=Color.White, modifier=Modifier.size(18.dp)); Text("رفض", color=Color.White, fontSize=15.sp, fontWeight=FontWeight.Bold)}
            }
        }
    }
}

@Composable private fun ZoomWaitingPanel(state: ZoomUiState.WaitingRoom){
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center){
        Icon(Icons.Default.HourglassEmpty, null, tint=ZoomBlue, modifier=Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("في انتظار المضيف", color=Color.White, fontSize=18.sp, fontWeight=FontWeight.Bold)
        Text("اجتماع: ${state.meetingId}", color=ZoomBlue, fontSize=13.sp)
        Text("سيتم إدخالك عند قبول المضيف", color=Color.White.copy(0.6f), fontSize=12.sp)
    }
}

@Composable private fun ZoomRingingPanel(state: ZoomUiState.Ringing){
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.SpaceBetween){
        Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(4.dp)){
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF0F2A1D)).padding(horizontal=12.dp, vertical=4.dp)){ Text(if(state.isVideo) "📹 اجتماع Zoom" else "📞 اجتماع Zoom", color=ZoomBlue, fontSize=12.sp, fontWeight=FontWeight.Bold)}
            Text("ترن ${state.members.size} أشخاص", color=Color.White, fontSize=16.sp, fontWeight=FontWeight.Bold)
            Text("اجتماع: ${state.meetingId}", color=ZoomBlue, fontSize=11.sp)
        }
        LazyVerticalGrid(columns=GridCells.Fixed(3), verticalArrangement=Arrangement.spacedBy(14.dp), horizontalArrangement=Arrangement.spacedBy(14.dp), modifier=Modifier.weight(1f).padding(vertical=12.dp)){
            items(state.members, key={it.userId}){ m-> ZoomMemberTile(m)}
        }
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFE53935)).clickable{ ZoomGroupCallService.end(context)}, contentAlignment=Alignment.Center){ Icon(Icons.Default.CallEnd, null, tint=Color.White, modifier=Modifier.size(28.dp))}
    }
}

@Composable private fun ZoomMemberTile(member: ZoomMember){
    val inf=rememberInfiniteTransition(label="z_${member.userId}")
    val pulse by inf.animateFloat(initialValue=1f, targetValue=if(member.status==ZoomMemberStatus.RINGING)1.10f else 1f, animationSpec=infiniteRepeatable(tween(650), RepeatMode.Reverse), label="p")
    val (border, label, col)=when(member.status){
        ZoomMemberStatus.RINGING-> Triple(ZoomBlue, "يرن...", Color(0xFFFFC107))
        ZoomMemberStatus.JOINED-> Triple(ZoomBlue, "انضم ✓", ZoomBlue)
        ZoomMemberStatus.DECLINED-> Triple(Color(0xFFE53935), "رفض", Color(0xFFE53935))
        ZoomMemberStatus.NO_ANSWER-> Triple(Color(0xFF6B7280), "لم يرد", Color(0xFF6B7280))
        ZoomMemberStatus.LEFT-> Triple(Color(0xFF6B7280), "غادر", Color(0xFF6B7280))
        ZoomMemberStatus.BUSY-> Triple(Color(0xFFFB8C00), "مشغول", Color(0xFFFB8C00))
        ZoomMemberStatus.WAITING-> Triple(Color(0xFFA78BFA), "انتظار", Color(0xFFA78BFA))
    }
    Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(6.dp)){
        Box(Modifier.size(68.dp).scale(pulse).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F172A)))).border(2.dp, border, CircleShape), contentAlignment=Alignment.Center){ Text(member.displayName.take(2).uppercase(), color=Color.White, fontSize=18.sp, fontWeight=FontWeight.Bold)}
        Text(member.displayName.take(10), color=Color.White, fontSize=11.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(col.copy(0.15f)).padding(horizontal=7.dp, vertical=2.dp)){ Text(label, color=col, fontSize=10.sp, fontWeight=FontWeight.Bold)}
        if(member.isHandRaised) Text("✋ رافع يده", color=ZoomBlue, fontSize=9.sp)
    }
}

@Composable
private fun ZoomParticipantsSheet(state: ZoomUiState.Active, onDismiss:()->Unit){
    val joined=state.members.filter{it.status==ZoomMemberStatus.JOINED}
    val context=LocalContext.current
    var showPollCreate by remember{mutableStateOf(false)}
    Dialog(onDismissRequest=onDismiss, properties=DialogProperties(usePlatformDefaultWidth=false)){
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable{onDismiss()}, contentAlignment=Alignment.BottomCenter){
            Surface(shape=RoundedCornerShape(topStart=20.dp, topEnd=20.dp), color=Color(0xFF0F172A), modifier=Modifier.fillMaxWidth().fillMaxHeight(0.72f).clickable(enabled=false){}){
                Column(Modifier.fillMaxSize().padding(16.dp)){
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
                        Column{ Text("المشاركون (${joined.size+1}/100)", color=Color.White, fontSize=16.sp, fontWeight=FontWeight.Bold); Text("اجتماع: ${state.meetingId}", color=ZoomBlue, fontSize=11.sp)}
                        Box(Modifier.clip(CircleShape).background(Color.White.copy(0.1f)).clickable{onDismiss()}.padding(8.dp)){ Icon(Icons.Default.Close, null, tint=Color.White, modifier=Modifier.size(18.dp))}
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.06f)).padding(10.dp)){
                        val q=ZoomRuntime.networkStats.quality; val (col,lab)=when(q){ NetworkStats.Quality.EXCELLENT->Color(0xFF00C98C) to "ممتاز"; NetworkStats.Quality.GOOD->Color(0xFF00C98C) to "جيد"; NetworkStats.Quality.FAIR->Color(0xFFFFC107) to "متوسط"; NetworkStats.Quality.POOR->Color(0xFFE53935) to "ضعيف"; else->Color.Gray to "?"}
                        Box(Modifier.size(8.dp).clip(CircleShape).background(col)); Text("الشبكة: $lab", color=Color.White.copy(0.8f), fontSize=11.sp)
                        if(ZoomRuntime.isScreenSharing) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(ZoomBlue).padding(horizontal=6.dp, vertical=2.dp)){ Text("شاشة", color=Color(0xFF002118), fontSize=10.sp, fontWeight=FontWeight.Bold)}
                        if(ZoomRuntime.isLocked) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)).padding(horizontal=6.dp, vertical=2.dp)){ Text("مقفل", color=Color.White, fontSize=10.sp)}
                    }
                    if(ZoomRuntime.isHost){
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            ZoomSheetBtn(if(ZoomRuntime.isLocked) "إلغاء القفل" else "قفل", if(ZoomRuntime.isLocked) Color(0xFFE53935) else ZoomBlue, Modifier.weight(1f)){ ZoomGroupCallService.toggleLock(context) }
                            ZoomSheetBtn(if(ZoomRuntime.isWaitingRoomEnabled) "إيقاف الانتظار" else "قاعة انتظار", ZoomBlue, Modifier.weight(1f)){ ZoomGroupCallService.toggleWaitingRoom(context) }
                            ZoomSheetBtn("استطلاع", ZoomBlue, Modifier.weight(1f)){ showPollCreate=true }
                            ZoomSheetBtn("غرف", ZoomBlue, Modifier.weight(1f)){ ZoomGroupCallService.createBreakout(context, 3) }
                        }
                    }
                    // استطلاع نشط
                    ZoomRuntime.activePoll?.let{ poll ->
                        Spacer(Modifier.height(10.dp))
                        Surface(shape=RoundedCornerShape(12.dp), color=ZoomBlue.copy(0.12f), border=androidx.compose.foundation.BorderStroke(1.dp, ZoomBlue.copy(0.3f))){
                            Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween){ Text("📊 ${poll.question}", color=Color.White, fontSize=13.sp, fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f)); Text("${poll.votes.size} صوت", color=ZoomBlue, fontSize=11.sp)}
                                poll.options.forEachIndexed{ idx, opt ->
                                    val votes = poll.votes.values.count{ it==idx }
                                    val total = poll.votes.size.coerceAtLeast(1)
                                    val pct = (votes*100)/total
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.06f)).clickable{ ZoomGroupCallService.votePoll(context, poll.id, idx)}.padding(8.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween){
                                        Text(opt, color=Color.White, fontSize=12.sp, modifier=Modifier.weight(1f))
                                        Text("$votes ($pct%)", color=ZoomBlue, fontSize=11.sp)
                                    }
                                }
                            }
                        }
                    }
                    // غرف فرعية
                    if(ZoomRuntime.breakoutRooms.isNotEmpty()){
                        Spacer(Modifier.height(8.dp))
                        Text("غرف فرعية:", color=Color.White.copy(0.7f), fontSize=11.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp), modifier=Modifier.fillMaxWidth()){
                            ZoomRuntime.breakoutRooms.forEach{ room ->
                                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.06f)).padding(8.dp), contentAlignment=Alignment.Center){
                                    Column(horizontalAlignment=Alignment.CenterHorizontally){ Text(room.name, color=Color.White, fontSize=11.sp, fontWeight=FontWeight.Bold); Text("${room.participantIds.size} مشارك", color=Color.White.copy(0.6f), fontSize=10.sp)}
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.weight(1f)){
                        item{ ZoomParticipantRow("أنت (المضيف)", "متصل", ZoomRuntime.isMuted, ZoomRuntime.isVideoEnabled, ZoomRuntime.isHost, ZoomRuntime.isHandRaised)}
                        items(state.members.size){ idx-> val m=state.members[idx]; ZoomParticipantRow(m.displayName, when(m.status){ ZoomMemberStatus.RINGING->"يرن..."; ZoomMemberStatus.JOINED->if(m.isMuted) "متصل · مكتوم" else "متصل"; else->m.status.name}, m.isMuted, m.hasVideo, false, m.isHandRaised)}
                    }
                }
            }
        }
    }
    if(showPollCreate){
        var q by remember{mutableStateOf("")}
        var o1 by remember{mutableStateOf("")}
        var o2 by remember{mutableStateOf("")}
        var o3 by remember{mutableStateOf("")}
        Dialog(onDismissRequest={showPollCreate=false}){
            Surface(shape=RoundedCornerShape(16.dp), color=Color(0xFF1E293B)){
                Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("استطلاع جديد", color=Color.White, fontWeight=FontWeight.Bold)
                    androidx.compose.material3.OutlinedTextField(value=q, onValueChange={q=it}, placeholder={Text("السؤال", color=Color.Gray)}, modifier=Modifier.fillMaxWidth(), singleLine=true, colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White, unfocusedTextColor=Color.White))
                    androidx.compose.material3.OutlinedTextField(value=o1, onValueChange={o1=it}, placeholder={Text("خيار 1", color=Color.Gray)}, modifier=Modifier.fillMaxWidth(), singleLine=true, colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White, unfocusedTextColor=Color.White))
                    androidx.compose.material3.OutlinedTextField(value=o2, onValueChange={o2=it}, placeholder={Text("خيار 2", color=Color.Gray)}, modifier=Modifier.fillMaxWidth(), singleLine=true, colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White, unfocusedTextColor=Color.White))
                    androidx.compose.material3.OutlinedTextField(value=o3, onValueChange={o3=it}, placeholder={Text("خيار 3 (اختياري)", color=Color.Gray)}, modifier=Modifier.fillMaxWidth(), singleLine=true, colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White, unfocusedTextColor=Color.White))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        TextButton(onClick={showPollCreate=false}){ Text("إلغاء", color=Color.White.copy(0.6f))}
                        Button(onClick={
                            val opts=listOf(o1,o2,o3).filter{it.isNotBlank()}
                            if(q.isNotBlank() && opts.size>=2){ ZoomGroupCallService.createPoll(context, q, opts); showPollCreate=false}
                        }, enabled=q.isNotBlank() && listOf(o1,o2).all{it.isNotBlank()}, modifier=Modifier.weight(1f), colors=ButtonDefaults.buttonColors(containerColor=ZoomBlue)){ Text("إنشاء", color=Color.White)}
                    }
                }
            }
        }
    }
}

@Composable private fun ZoomSheetBtn(label:String, col:Color, modifier: Modifier=Modifier, onClick:()->Unit){
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(col.copy(0.15f)).border(1.dp, col.copy(0.4f), RoundedCornerShape(8.dp)).clickable(onClick=onClick).padding(horizontal=8.dp, vertical=6.dp), contentAlignment=Alignment.Center){ Text(label, color=col, fontSize=10.sp, fontWeight=FontWeight.Bold, maxLines=1)}
}

@Composable private fun ZoomParticipantRow(name:String, status:String, isMuted:Boolean, isVideo:Boolean, isHost:Boolean, isHand:Boolean){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.06f)).padding(12.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically){
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp), verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF1E3A5F), Color(0xFF0F172A)))), contentAlignment=Alignment.Center){ Text(name.take(2).uppercase().ifBlank{"؟"}, color=Color.White, fontSize=14.sp, fontWeight=FontWeight.Bold)}
            Column{ Row(horizontalArrangement=Arrangement.spacedBy(4.dp), verticalAlignment=Alignment.CenterVertically){ Text(name.take(14), color=Color.White, fontSize=13.sp, fontWeight=FontWeight.SemiBold); if(isHost) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(ZoomBlue).padding(horizontal=4.dp, vertical=1.dp)){ Text("مضيف", color=Color(0xFF002118), fontSize=9.sp)}; if(isHand) Text("✋", fontSize=12.sp)}; Text(status, color=Color.White.copy(0.6f), fontSize=11.sp)}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){ if(isVideo) Icon(Icons.Default.Videocam, null, tint=ZoomBlue, modifier=Modifier.size(16.dp)) else Icon(Icons.Default.VideocamOff, null, tint=Color.Gray, modifier=Modifier.size(16.dp)); Icon(if(isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint=if(isMuted) Color(0xFFE53935) else ZoomBlue, modifier=Modifier.size(16.dp))}
    }
}

@Composable
private fun ZoomVideoTile(label:String, track: VideoTrack?, isMuted:Boolean, isMirror:Boolean, eglContext: org.webrtc.EglBase.Context?, fillBounds:Boolean){
    val modifier=if(fillBounds) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(0.85f)
    androidx.compose.material3.Card(modifier=modifier.border(width=if(isMuted)0.dp else 1.dp, color=if(isMuted) Color.Transparent else ZoomBlue.copy(0.45f), shape=RoundedCornerShape(14.dp)), shape=RoundedCornerShape(14.dp), colors=CardDefaults.cardColors(containerColor=Color(0xFF0F172A)), elevation=CardDefaults.cardElevation(2.dp)){
        Box(Modifier.fillMaxSize()){
            if(track!=null && eglContext!=null){
                androidx.compose.runtime.key(track, eglContext){
                    var r: SurfaceViewRenderer? by remember{mutableStateOf(null)}
                    AndroidView(factory={ctx-> SurfaceViewRenderer(ctx).apply{ init(eglContext,null); setMirror(isMirror); setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL); setEnableHardwareScaler(true); r=this; track.addSink(this)}}, update={v-> if(r==v) track.addSink(v)}, onRelease={v-> track.removeSink(v); v.release()}, modifier=Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
                    DisposableEffect(track, r){ onDispose{ r?.let{track.removeSink(it)}}}
                }
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF162544), Color(0xFF040A14)))), contentAlignment=Alignment.Center){
                    Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(10.dp)){
                        Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF23406A), Color(0xFF0F1E36)))).border(2.dp, Color.White.copy(0.10f), CircleShape), contentAlignment=Alignment.Center){ Text(label.take(2).uppercase().ifBlank{"؟"}, color=Color.White, fontSize=24.sp, fontWeight=FontWeight.Black)}
                        Text(label.take(14), color=Color.White.copy(0.9f), fontSize=12.sp, fontWeight=FontWeight.SemiBold, maxLines=1, overflow=TextOverflow.Ellipsis)
                        if(track!=null && !track.enabled()) Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.12f)).padding(horizontal=8.dp, vertical=3.dp)){ Text("الكاميرا متوقفة", color=Color.White.copy(0.7f), fontSize=9.sp)}
                    }
                }
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.72f)))).padding(8.dp,7.dp), horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically){
                if(isMuted) Box(Modifier.background(Color(0xFFE53935), RoundedCornerShape(6.dp)).padding(4.dp,2.dp)){ Row(horizontalArrangement=Arrangement.spacedBy(3.dp), verticalAlignment=Alignment.CenterVertically){ Icon(Icons.Default.MicOff, null, tint=Color.White, modifier=Modifier.size(10.dp)); Text("مكتوم", color=Color.White, fontSize=9.sp, fontWeight=FontWeight.Bold)} } else Box(Modifier.size(6.dp).clip(CircleShape).background(ZoomBlue))
                Text(label.take(14), color=Color.White, fontSize=11.sp, fontWeight=FontWeight.SemiBold, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f))
            }
            if(track!=null && track.enabled() && eglContext!=null) Box(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(ZoomBlue.copy(0.92f)).padding(6.dp,2.dp)){ Text("● LIVE", color=Color(0xFF002118), fontSize=8.sp, fontWeight=FontWeight.Black)}
        }
    }
}
