package com.red.sovereign.calls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class CallStatus {
    RINGING,
    CONNECTED,
    ENDED
}

@Composable
fun IncomingPstnCallScreen(
    callerNumber: String,
    callStatus: CallStatus,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onHangup: () -> Unit,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit
) {
    var callDuration by remember { mutableIntStateOf(0) }

    LaunchedEffect(callStatus) {
        if (callStatus == CallStatus.CONNECTED) {
            while (true) {
                delay(1000L)
                callDuration++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Text(
                text = when (callStatus) {
                    CallStatus.RINGING -> "Incoming Call"
                    CallStatus.CONNECTED -> "Connected"
                    CallStatus.ENDED -> "Call Ended"
                },
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = callerNumber,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (callStatus == CallStatus.CONNECTED) {
                Text(
                    text = formatDuration(callDuration),
                    color = Color.White,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            } else if (callStatus == CallStatus.RINGING) {
                Text(
                    text = "Ringing...",
                    color = Color.LightGray,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = callStatus == CallStatus.RINGING,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onReject,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "Reject",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Accept",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = callStatus == CallStatus.CONNECTED,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        IconButton(
                            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            onClick = onMuteToggle,
                            tint = if (isMuted) Color.Red else Color.White
                        )

                        IconButton(
                            icon = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (isSpeakerOn) "Speaker Off" else "Speaker On",
                            onClick = onSpeakerToggle,
                            tint = if (isSpeakerOn) Color.Yellow else Color.White
                        )
                    }

                    Button(
                        onClick = onHangup,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "Hang Up",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = callStatus == CallStatus.ENDED,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Call has ended",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}
