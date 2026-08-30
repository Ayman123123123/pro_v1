package com.red.sovereign.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.red.sovereign.ui.screens.ActiveCallScreen

/**
 * CallScreens — Screen Registry for All Call Types
 *
 * This file acts as a central registry for all call-related screens.
 * Each screen is mapped to its corresponding state/route.
 */

/**
 * Main call screen router — dispatches to the correct overlay based on call state
 */
@Composable
fun CallScreens(
    onDismiss: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onInviteClick: () -> Unit = {}
) {
    when (val state = CallRuntime.state) {
        is com.red.sovereign.calls.CallUiState.Idle -> Unit
        else -> ActiveCallScreen(
            onDismiss = onDismiss,
            onSettingsClick = onSettingsClick,
            onHistoryClick = onHistoryClick,
            onInviteClick = onInviteClick
        )
    }
}

/**
 * Group call screens router
 */
@Composable
fun GroupCallScreens(
    onBack: () -> Unit = {}
) {
    when (val state = GroupCallRuntime.state) {
        is com.red.sovereign.calls.GroupCallUiState.Idle -> Unit
        else -> GroupCallOverlay(onBack = onBack)
    }
}

/**
 * Zoom call screens router
 */
@Composable
fun ZoomCallScreens(
    onBack: () -> Unit = {}
) {
    when (val state = ZoomRuntime.state) {
        is com.red.sovereign.calls.ZoomUiState.Idle -> Unit
        else -> ZoomGroupCallOverlay(onBack = onBack)
    }
}

/**
 * Conference screens router
 */
@Composable
fun ConferenceScreens(
    onBack: () -> Unit = {}
) {
    when (val state = ConferenceRuntime.state) {
        is com.red.sovereign.calls.ConferenceUiState.Idle -> Unit
        else -> YounesConferenceOverlay(onBack = onBack)
    }
}

/**
 * Live stream screens router
 */
@Composable
fun LiveStreamScreens(
    onBack: () -> Unit = {}
) {
    when (val state = LiveStreamRuntime.state) {
        is com.red.sovereign.calls.LiveStreamUiState.Idle -> Unit
        else -> YounesLiveStreamOverlay(onBack = onBack)
    }
}

/**
 * PSTN call screens router
 */
@Composable
fun PstnCallScreens(
    onBack: () -> Unit = {}
) {
    when (val status = CallRuntime.pstnStatus) {
        is PstnCallStatus.IDLE -> Unit
        else -> PstnCallOverlay(onBack = onBack)
    }
}

/**
 * Unified call screens — shows the appropriate overlay for the active call type
 */
@Composable
fun UnifiedCallScreens(
    onDismiss: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // Group call has priority
            GroupCallRuntime.state !is com.red.sovereign.calls.GroupCallUiState.Idle -> {
                GroupCallOverlay()
            }
            // Zoom call
            ZoomRuntime.state !is com.red.sovereign.calls.ZoomUiState.Idle -> {
                ZoomGroupCallOverlay()
            }
            // Conference
            ConferenceRuntime.state !is com.red.sovereign.calls.ConferenceUiState.Idle -> {
                YounesConferenceOverlay()
            }
            // Live stream
            LiveStreamRuntime.state !is com.red.sovereign.calls.LiveStreamUiState.Idle -> {
                YounesLiveStreamOverlay()
            }
            // PSTN call
            CallRuntime.pstnStatus !is PstnCallStatus.IDLE -> {
                PstnCallOverlay()
            }
            // Regular call
            CallRuntime.state !is com.red.sovereign.calls.CallUiState.Idle -> {
                ActiveCallScreen(onDismiss = onDismiss)
            }
        }
    }
}
