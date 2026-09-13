package com.red.sovereign.calls

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

sealed interface PstnCallState {
    data object Idle : PstnCallState
    data class Preparing(val number: String) : PstnCallState
    data class Ringing(val number: String) : PstnCallState
    data class Active(val number: String, val startedAt: Long) : PstnCallState
    data class Failed(val number: String, val message: String) : PstnCallState
    data class Ended(val number: String, val durationMs: Long) : PstnCallState
}

object PstnCallRuntime {
    var state: PstnCallState by mutableStateOf(PstnCallState.Idle)
    var muted: Boolean by mutableStateOf(false)
    var speaker: Boolean by mutableStateOf(true)
}

object PstnCallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun start(context: Context, suppliedNumber: String) {}
    fun hangup(context: Context) {}
    fun mute(enabled: Boolean) { PstnCallRuntime.muted = enabled }
    fun setSpeaker(on: Boolean) { PstnCallRuntime.speaker = on }
}

@Composable
fun PstnCallOverlay() {
    Box {}
}
