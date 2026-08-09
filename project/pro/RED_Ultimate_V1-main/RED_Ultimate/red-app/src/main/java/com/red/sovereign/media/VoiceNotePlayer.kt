package com.red.sovereign.media

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.red.sovereign.settings.SettingsRuntime

@Composable
fun VoiceNotePlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferredSpeed = SettingsRuntime.current.defaultPlaybackSpeed
    var speed by remember(uri) { mutableFloatStateOf(preferredSpeed) }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true
            )
            setMediaItem(MediaItem.fromUri(uri))
            setPlaybackSpeed(preferredSpeed)
            prepare()
        }
    }
    Column(modifier) {
        AndroidView(
            factory = { viewContext -> PlayerView(viewContext).apply {
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowFastForwardButton(false)
                setShowRewindButton(false)
                this.player = player
            } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().height(68.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            listOf(1f, 1.5f, 2f).forEach { value ->
                AssistChip(
                    onClick = { speed = value; player.setPlaybackSpeed(value) },
                    label = { Text(if (value == 1f) "1×" else "${value}×") },
                    leadingIcon = { if (speed == value) Text("●") }
                )
            }
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
}
