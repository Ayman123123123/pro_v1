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
    onEndCall: () -> Unit,
    onToggleMic: () -> Unit = {},
    onToggleCamera: () -> Unit = {}
) {
    var isMicOn by remember { mutableStateOf(true) }
    var isCameraOn by remember { mutableStateOf(true) }

    val remoteRenderer = remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val localRenderer = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(voipEngine.getEglContext(), null)
                    setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    remoteRenderer.value = this
                }
            },
            update = { renderer ->
                remoteRenderer.value = renderer
            },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            modifier = Modifier.size(120.dp, 180.dp).align(Alignment.TopEnd).padding(16.dp),
            color = Color.DarkGray,
            shape = MaterialTheme.shapes.medium
        ) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(voipEngine.getEglContext(), null)
                        setMirror(true)
                        localRenderer.value = this
                    }
                },
                update = { renderer ->
                    localRenderer.value = renderer
                }
            )
        }

        Text(
            text = remoteName,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
        )

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilledIconButton(
                onClick = {
                    isMicOn = !isMicOn
                    onToggleMic()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isMicOn) Color.DarkGray else Color.Red
                )
            ) {
                Icon(
                    if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                    null,
                    tint = Color.White
                )
            }
            FloatingActionButton(onClick = onEndCall, containerColor = Color.Red) {
                Icon(Icons.Default.CallEnd, null, tint = Color.White)
            }
            FilledIconButton(
                onClick = {
                    isCameraOn = !isCameraOn
                    onToggleCamera()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isCameraOn) Color.DarkGray else Color.Red
                )
            ) {
                Icon(
                    if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    null,
                    tint = Color.White
                )
            }
        }
    }
}
