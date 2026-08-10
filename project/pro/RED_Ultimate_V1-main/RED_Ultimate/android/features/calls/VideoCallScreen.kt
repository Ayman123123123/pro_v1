package com.red.sovereign.features.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer

@Composable
fun VideoCallScreen(
    remoteName: String,
    voipEngine: VoipEngine,
    onEndCall: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. Remote Video (Background)
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(it).apply {
                    init(voipEngine.getEglContext(), null)
                    setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Local Preview (Floating)
        Surface(
            modifier = Modifier.size(120.dp, 180.dp).align(Alignment.TopEnd).padding(16.dp),
            color = Color.DarkGray,
            shape = MaterialTheme.shapes.medium
        ) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(it).apply {
                        init(voipEngine.getEglContext(), null)
                        setMirror(true)
                    }
                }
            )
        }

        // 3. Call Controls
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = {}) { Icon(Icons.Default.Mic, null, tint = Color.White) }
            FloatingActionButton(onClick = onEndCall, containerColor = Color.Red) {
                Icon(Icons.Default.CallEnd, null, tint = Color.White)
            }
            IconButton(onClick = {}) { Icon(Icons.Default.Videocam, null, tint = Color.White) }
        }
    }
}
