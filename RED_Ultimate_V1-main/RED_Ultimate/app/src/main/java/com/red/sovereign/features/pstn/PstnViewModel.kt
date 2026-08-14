package com.red.sovereign.features.pstn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.features.calls.data.CallRepository
import com.red.sovereign.features.calls.data.CallResult
import com.red.sovereign.features.calls.data.PstnCallResponseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── State ─────────────────────────────────────────────────────────────────

enum class PstnCallStatus { IDLE, DIALING, RINGING, CONNECTED, ENDED, ERROR }

data class PstnUiState(
    val status: PstnCallStatus = PstnCallStatus.IDLE,
    val activeCallId: String? = null,
    val number: String = "",
    val operatorName: String = "",
    val operatorColor: Long = 0xFFFF5722,
    val slot: Int = -1,
    val durationSeconds: Long = 0L,
    val usedToday: Int = 0,
    val dailyLimit: Int = 0,
    val errorMessage: String? = null,
    val isPstnEnabled: Boolean = true
)

// ─── PstnViewModel ─────────────────────────────────────────────────────────

/**
 * ViewModel للمكالمات الخطية (PSTN/GSM عبر Dinstar).
 *
 * يستدعي [CallRepository.dialPstn] → يحصل على callId → يُتيح إنهاء المكالمة
 * عبر [CallRepository.hangupPstn] مع تحرير المنفذ في Load Balancer.
 */
@HiltViewModel
class PstnViewModel @Inject constructor(
    private val callRepository: CallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PstnUiState())
    val uiState: StateFlow<PstnUiState> = _uiState.asStateFlow()

    private var durationJob: kotlinx.coroutines.Job? = null
    private var callStartTime: Long = 0L

    // ── Dialing ───────────────────────────────────────────────────────

    fun dialPstn(number: String, slot: Int = 0) {
        if (_uiState.value.status != PstnCallStatus.IDLE) return
        val normalized = number.filter { it.isDigit() }
        if (normalized.length < 7) {
            _uiState.update { it.copy(errorMessage = "رقم الهاتف قصير جداً") }
            return
        }

        val opInfo = YemeniOperatorDetector.getOperatorInfo(normalized)
        _uiState.update {
            it.copy(
                status = PstnCallStatus.DIALING,
                number = normalized,
                operatorName = opInfo.name,
                operatorColor = opInfo.brandColor.value,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = callRepository.dialPstn(normalized)
            when (result) {
                is CallResult.Success -> handleDialSuccess(result.data)
                is CallResult.Error -> handleDialError(result.message)
            }
        }
    }

    // Alias used by CallOrchestrator
    fun makePstnCall(number: String) = dialPstn(number)

    private fun handleDialSuccess(response: PstnCallResponseDto) {
        _uiState.update {
            it.copy(
                status = PstnCallStatus.RINGING,
                activeCallId = response.callId,
                slot = response.slot,
                usedToday = response.usedToday,
                dailyLimit = response.dailyLimit,
                errorMessage = null
            )
        }
        // Simulate RINGING → CONNECTED transition (real state comes from Asterisk events)
        // In a full integration, DinstarEventListener pushes state via WebSocket
        viewModelScope.launch {
            delay(3_000L)
            if (_uiState.value.status == PstnCallStatus.RINGING) {
                _uiState.update { it.copy(status = PstnCallStatus.CONNECTED) }
                startDurationTimer()
            }
        }
    }

    private fun handleDialError(message: String) {
        val userMessage = when {
            message.contains("limit", ignoreCase = true) -> "وصلت للحد اليومي للمكالمات الخطية"
            message.contains("PSTN access", ignoreCase = true) -> "خدمة المكالمات الخطية غير مفعّلة لحسابك"
            message.contains("No DINSTAR", ignoreCase = true) -> "لا توجد بوابة GSM متاحة حالياً"
            message.contains("approved", ignoreCase = true) -> "الحساب غير مفعّل"
            message.contains("prefix", ignoreCase = true) -> "البادئة غير معروفة — تحقق من الرقم"
            else -> "تعذّر الاتصال: $message"
        }
        _uiState.update {
            it.copy(status = PstnCallStatus.ERROR, errorMessage = userMessage)
        }
        viewModelScope.launch {
            delay(3_000L)
            _uiState.update { it.copy(status = PstnCallStatus.IDLE, errorMessage = null) }
        }
    }

    // ── Hangup ────────────────────────────────────────────────────────

    fun endGsmCall() {
        val callId = _uiState.value.activeCallId ?: run {
            resetState()
            return
        }
        val slot = _uiState.value.slot

        viewModelScope.launch {
            callRepository.hangupPstn(callId, slot)
            resetState()
        }
    }

    // Alias
    fun hangup() = endGsmCall()

    fun hasActiveCall(): Boolean = _uiState.value.status !in listOf(
        PstnCallStatus.IDLE, PstnCallStatus.ERROR, PstnCallStatus.ENDED
    )

    fun getActiveCall(): PstnCallState? {
        val s = _uiState.value
        return if (s.activeCallId != null) {
            PstnCallState(s.activeCallId, s.number, s.operatorName, s.status.name, s.slot)
        } else null
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    // ── Duration Timer ────────────────────────────────────────────────

    private fun startDurationTimer() {
        callStartTime = System.currentTimeMillis()
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val elapsed = (System.currentTimeMillis() - callStartTime) / 1000L
                _uiState.update { it.copy(durationSeconds = elapsed) }
            }
        }
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    private fun resetState() {
        stopDurationTimer()
        _uiState.update {
            PstnUiState(
                status = PstnCallStatus.ENDED,
                durationSeconds = it.durationSeconds // keep for summary
            )
        }
        viewModelScope.launch {
            delay(1_500L)
            _uiState.value = PstnUiState()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDurationTimer()
    }
}

// Compatibility data class
data class PstnCallState(
    val callId: String,
    val number: String,
    val operator: String?,
    val status: String,
    val slot: Int = -1
)
