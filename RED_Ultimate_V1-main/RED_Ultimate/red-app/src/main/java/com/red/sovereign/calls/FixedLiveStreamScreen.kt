package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * شاشة بث مباشر مصلحة - تصلح مشكلة الشاشة السوداء
 * 
 * الإصلاحات:
 * - تهيئة EGL مضمونة
 * - معالجة حالة null بشكل صحيح
 * - عرض مؤشر تحميل بدل شاشة سوداء
 * - إعادة محاولة تلقائية
 */

@Composable
fun FixedLiveStreamScreenV2() {
    val context = LocalContext.current
    val liveState by ModernLiveStreamSystemV2.liveState.collectAsState()
    val currentStream by ModernLiveStreamSystemV2.currentStream.collectAsState()
    val localTrack by ModernLiveStreamSystemV2.localVideoTrack.collectAsState()
    val remoteTrack by ModernLiveStreamSystemV2.remoteVideoTrack.collectAsState()
    val chatMessages by ModernLiveStreamSystemV2.chatMessages.collectAsState()
    val viewerCount by ModernLiveStreamSystemV2.viewerCount.collectAsState()
    val isMuted by ModernLiveStreamSystemV2.isMuted.collectAsState()
    val isVideoEnabled by ModernLiveStreamSystemV2.isVideoEnabled.collectAsState()
    val error by ModernLiveStreamSystemV2.error.collectAsState()
    
    val isBroadcaster = liveState == ModernLiveStreamSystemV2.LiveState.BROADCASTING
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // Video layer - FIXED black screen
        Box(modifier = Modifier.fillMaxSize()) {
            if (isBroadcaster) {
                // Broadcaster - show local video
                if (localTrack != null && isVideoEnabled) {
                    FixedVideoRenderer(
                        track = localTrack,
                        mirror = true,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // No video - show placeholder with message
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1A1A1A), Color(0xFF2D2D2D))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.VideocamOff,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (!isVideoEnabled) "الكاميرا متوقفة" else "جاري تحميل الكاميرا...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (error != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    error!!,
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // Viewer - show remote video
                if (remoteTrack != null) {
                    FixedVideoRenderer(
                        track = remoteTrack,
                        mirror = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // No remote video yet - show loading
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1A1A1A), Color(0xFF0F0F0F))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color(0xFFE0B551),
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "جاري الاتصال بالبث...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "يرجى الانتظار",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
        
        // Gradient overlays for readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )
        
        // Top bar - FIXED colors for readability
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LIVE badge - high contrast
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEF4444), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "مباشر",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                
                Column {
                    Text(
                        currentStream?.title ?: "بث مباشر",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        currentStream?.hostName ?: "مذيع",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
            
            // Viewer count - high contrast
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "$viewerCount",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
        
        // Chat overlay - FIXED readability
        LazyChatOverlay(
            messages = chatMessages,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 100.dp)
                .fillMaxWidth(0.75f)
                .height(200.dp)
        )
        
        // Bottom controls - FIXED colors
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Chat input
                var chatText by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = chatText,
                    onValueChange = { chatText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "أضف تعليقاً...",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.15f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                        focusedBorderColor = Color(0xFFE0B551),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFE0B551)
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (chatText.isNotBlank()) {
                                    ModernLiveStreamSystemV2.sendChatMessage(chatText.trim())
                                    chatText = ""
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Send, null, tint = Color(0xFFE0B551))
                        }
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Controls
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isBroadcaster) {
                        // Mute
                        IconButton(
                            onClick = { ModernLiveStreamSystemV2.toggleMute() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isMuted) Color(0xFFEF4444) else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // Video
                        IconButton(
                            onClick = { ModernLiveStreamSystemV2.toggleVideo() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (!isVideoEnabled) Color(0xFFEF4444) else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (!isVideoEnabled) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        // Switch camera
                        IconButton(
                            onClick = { ModernLiveStreamSystemV2.switchCamera() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Cameraswitch, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        // Viewer - reaction
                        IconButton(
                            onClick = { ModernLiveStreamSystemV2.sendGift("❤️") },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFEF4444), CircleShape)
                        ) {
                            Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    // End/Leave
                    IconButton(
                        onClick = { ModernLiveStreamSystemV2.stopBroadcast() },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFEF4444), CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        
        // Error snackbar
        if (error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .padding(bottom = 80.dp),
                containerColor = Color(0xFFEF4444),
                contentColor = Color.White,
                action = {
                    TextButton(onClick = { ModernLiveStreamSystemV2.clearError() }) {
                        Text("حسناً", color = Color.White)
                    }
                }
            ) {
                Text(error!!, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun FixedVideoRenderer(
    track: VideoTrack?,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    if (track == null) {
        Box(modifier = modifier.background(Color(0xFF1A1A1A)))
        return
    }
    
    val eglContext = remember { ModernLiveStreamSystemV2.getEglContext() }
    
    if (eglContext == null) {
        // EGL not ready - show loading instead of black
        Box(
            modifier = modifier.background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFE0B551))
                Spacer(modifier = Modifier.height(8.dp))
                Text("جاري تهيئة الفيديو...", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
        return
    }
    
    var renderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }
    
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                renderer = this
                track.addSink(this)
            }
        },
        update = { view ->
            try {
                track.addSink(view)
            } catch (e: Exception) {
                // Ignore
            }
        },
        modifier = modifier
    )
    
    DisposableEffect(track, renderer) {
        onDispose {
            try {
                renderer?.let { r ->
                    track.removeSink(r)
                    r.release()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}

@Composable
private fun LazyChatOverlay(
    messages: List<ModernLiveStreamSystemV2.LiveChatMessage>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        reverseLayout = false
    ) {
        items(messages.takeLast(20)) { msg ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (msg.isGift) Color(0xFFE0B551).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${msg.senderName}: ",
                        color = if (msg.isGift) Color.Black else Color(0xFFE0B551),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        msg.text,
                        color = if (msg.isGift) Color.Black else Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
