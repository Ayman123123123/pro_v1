package com.red.feature.pstn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PstnViewModel @Inject constructor(
    private val duminApi: DuminApi,
    private val pstnDao: PstnDao
) : ViewModel() {

    private val _callState = MutableStateFlow<PstnCallState>(PstnCallState.Idle)
    val callState: StateFlow<PstnCallState> = _callState

    private var currentCallId: String? = null

    fun makeCall(number: String) {
        viewModelScope.launch {
            _callState.value = PstnCallState.Dialing(number)
            try {
                // Step 1: Request Asterisk/Dumin to start GSM call
                val response = duminApi.startCall(PstnCallRequest(number, "192.168.1.100")) // Local Dumin IP
                if (response.isSuccessful) {
                    currentCallId = response.body()?.callId
                    startPollingStatus(number)
                } else {
                    _callState.value = PstnCallState.Ended("Gateway Error")
                }
            } catch (e: Exception) {
                _callState.value = PstnCallState.Ended("Connection Failed")
            }
        }
    }

    private fun startPollingStatus(number: String) {
        viewModelScope.launch {
            while (currentCallId != null && _callState.value !is PstnCallState.Ended) {
                val status = duminApi.getCallStatus(currentCallId!!)
                if (status.isSuccessful) {
                    val body = status.body()
                    when (body?.status) {
                        "RINGING" -> _callState.value = PstnCallState.Ringing(number)
                        "ACTIVE" -> _callState.value = PstnCallState.Active(number, body.duration)
                        "ENDED" -> {
                            _callState.value = PstnCallState.Ended("Finished")
                            saveLog(number, body.duration)
                            currentCallId = null
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    fun hangup() {
        viewModelScope.launch {
            currentCallId?.let { duminApi.hangup(it) }
            _callState.value = PstnCallState.Ended("User Hung Up")
            currentCallId = null
        }
    }

    private suspend fun saveLog(number: String, duration: Long) {
        pstnDao.insertLog(PstnCallLog(
            phoneNumber = number,
            timestamp = System.currentTimeMillis(),
            duration = duration,
            direction = "OUTGOING",
            status = "COMPLETED"
        ))
    }
}
