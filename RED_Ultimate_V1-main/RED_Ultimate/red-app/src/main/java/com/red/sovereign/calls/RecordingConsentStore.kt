package com.red.sovereign.calls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-call UI confirmation state for call recording (two-party consent).
 *
 * Hardens [GroupCallService.ACTION_START_RECORDING] / [ConferenceService.ACTION_START_RECORDING]
 * against Intent EXTRA_CONSENT bypass: the Service must check
 * [isGranted] — confirmation granted from the in-call UI dialog — not just
 * `intent.getBooleanExtra(EXTRA_CONSENT)`.
 *
 * Flow:
 * • UI (GroupCallOverlay / RecordingConsentDialog) calls [grant] only after
 *   the user explicitly confirms in the visible consent dialog.
 * • Service checks [isGranted(callId)] before [CallRecordingManager.start].
 * • If false → Service sets [pendingConsentCallId] so the overlay shows the
 *   consent dialog, logs Log.w on the bypass attempt, and does NOT start.
 */
object RecordingConsentStore {
    private val consents = ConcurrentHashMap<String, Boolean>()

    private val _pendingConsent = MutableStateFlow<String?>(null)
    /** callId waiting for in-call UI consent dialog — overlay observes this. */
    val pendingConsent: StateFlow<String?> = _pendingConsent

    /** Compose mirror for overlays that read mutableState directly. */
    var pendingConsentCallId by mutableStateOf<String?>(null)
        private set

    fun grant(callId: String) {
        if (callId.isBlank()) return
        consents[callId] = true
        if (_pendingConsent.value == callId) {
            _pendingConsent.value = null
            pendingConsentCallId = null
        }
        android.util.Log.d("RecordingConsent", "granted call=$callId")
    }

    fun revoke(callId: String) {
        if (callId.isBlank()) return
        consents.remove(callId)
    }

    fun isGranted(callId: String): Boolean =
        callId.isNotBlank() && consents[callId] == true

    /** Service calls this when store consent is missing — triggers UI dialog. */
    fun requestConsent(callId: String) {
        if (callId.isBlank()) return
        _pendingConsent.value = callId
        pendingConsentCallId = callId
    }

    fun clear(callId: String) {
        if (callId.isBlank()) return
        consents.remove(callId)
        if (_pendingConsent.value == callId) {
            _pendingConsent.value = null
            pendingConsentCallId = null
        }
    }
}
