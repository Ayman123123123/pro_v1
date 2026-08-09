package com.red.sovereign.features.pstn

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * RED PSTN ViewModel — manages GSM gateway calls (System B: Dinstar UC2000).
 */
@HiltViewModel
class PstnViewModel @Inject constructor() : ViewModel() {

    private var activeGsmCall: GsmCall? = null

    data class GsmCall(
        val slotIndex: Int,
        val number: String,
        val operator: String?,
        val status: String = "IDLE" // IDLE, DIALING, CONNECTED, ENDED
    )

    fun dialPstn(number: String, slot: Int = 0) {
        activeGsmCall = GsmCall(slot, number, null, "DIALING")
    }

    fun endGsmCall() {
        activeGsmCall = null
    }

    fun getActiveCall(): GsmCall? = activeGsmCall
}
