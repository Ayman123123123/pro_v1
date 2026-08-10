package com.red.features.pstn

import android.util.Log
import com.red.features.dinstar.DinstarViewModel
import com.red.features.dinstar.YemenOperator

/**
 * System B: PSTN / DINSTAR GATEWAY
 * محرك المكالمات الخطية عبر بوابة Dinstar UC2000-VE-8G
 * 
 * التدفق: التطبيق → الباكند → Asterisk AMI → PJSIP → DINSTAR
 * STRICT ISOLATION: No WebRTC or VoIP imports allowed here.
 */
class PstnEngine(
    val gateway: String,
    val protocol: String,
    private val dinstarViewModel: DinstarViewModel? = null
) {

    companion object {
        private const val TAG = "RED.PstnEngine"
    }

    fun connectToDinstar() {
        Log.i(TAG, "Connecting to Dinstar SIM Gateway via Asterisk SIP...")
        dinstarViewModel?.discoverGateway()
    }

    /**
     * إجراء مكالمة GSM عبر Dinstar
     * @param phoneNumber الرقم اليمني
     * @param port المنفذ المختار (إذا null، يُختار تلقائياً)
     * @return معلومات المكالمة أو null إذا فشل
     */
    fun makeGsmCall(phoneNumber: String, port: Int? = null): GsmCallInfo? {
        val optimalPort = port ?: dinstarViewModel?.selectOptimalPort(phoneNumber)?.index ?: 0
        val operator = YemenOperator.fromNumber(phoneNumber)
        
        Log.i(TAG, "Initiating PSTN call to $phoneNumber via port $optimalPort (${operator.arabicName})")
        
        // في الإنتاج: الطلب يذهب للباكند POST /api/pstn/dial
        // الباكند يتعامل مع Asterisk AMI → PJSIP → DINSTAR
        
        return GsmCallInfo(
            slotIndex = optimalPort,
            number = phoneNumber,
            operator = operator.arabicName,
            operatorEnglish = operator.englishName,
            status = "DIALING"
        )
    }

    data class GsmCallInfo(
        val slotIndex: Int,
        val number: String,
        val operator: String,
        val operatorEnglish: String,
        val status: String // IDLE, DIALING, RINGING, CONNECTED, ENDED
    )
}
