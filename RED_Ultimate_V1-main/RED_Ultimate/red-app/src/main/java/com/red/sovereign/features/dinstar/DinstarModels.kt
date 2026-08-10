package com.red.sovereign.features.dinstar

import androidx.compose.ui.graphics.Color

/**
 * 🏛️ YOUNES Dinstar UC2000-VE-8G — Data Models
 */

data class DinstarPort(
    val index: Int,
    val radioType: String = "GSM",
    val registrationState: String = "UNREGISTERED",
    val callState: String = "IDLE",
    val signalPercent: Int = 0,
    val signalRaw: Int = 0,
    val gprsState: String = "DETACH",
    val operatorName: String = "غير معروف",
    val numberMasked: String? = null,
    val imsiMasked: String? = null,
    val iccidMasked: String? = null,
    val simType: YemenOperator = YemenOperator.UNKNOWN,
    val isHealthy: Boolean = false
) {
    val isAvailable: Boolean
        get() = registrationState == "REGISTERED" && callState == "IDLE" && signalPercent >= 20

    val statusDescriptionAr: String
        get() = when {
            callState == "ACTIVE" -> "في مكالمة"
            callState == "RINGING" -> "يرن"
            registrationState != "REGISTERED" -> "غير مسجل"
            signalPercent < 10 -> "إشارة ضعيفة جداً"
            signalPercent < 25 -> "إشارة ضعيفة"
            else -> "جاهز"
        }
}

enum class YemenOperator(
    val arabicName: String,
    val englishName: String,
    val prefixes: Set<String>,
    val color: Color
) {
    SABAFON("سبأفون", "Sabafon", setOf("770", "771", "772", "773", "774", "775", "776", "777", "778", "779"), Color(0xFFE53935)),
    MTN("إم تي إن", "MTN Yemen", setOf("710", "711", "712", "713", "714", "715", "716", "717", "718", "719"), Color(0xFFFFB300)),
    YEMEN_MOBILE("يمن موبايل", "YemenMobile", setOf("730", "731", "732", "733", "734", "735", "736", "737", "738", "739"), Color(0xFF43A047)),
    HITEL("هيتل", "HiTel", setOf("700", "701", "702", "703", "704", "705", "706", "707", "708", "709"), Color(0xFF1E88E5)),
    U_YEMEN("يو يمن", "U Yemen", setOf(), Color(0xFFAB47BC)),
    UNKNOWN("غير معروف", "Unknown", setOf(), Color(0xFF757575));

    companion object {
        fun fromPrefix(prefix: String): YemenOperator = entries.firstOrNull { prefix in it.prefixes } ?: UNKNOWN
        fun fromNumber(number: String): YemenOperator {
            val digits = number.filter { it.isDigit() }
            val local = when {
                digits.startsWith("967") -> digits.removePrefix("967")
                digits.startsWith("0") -> digits.removePrefix("0")
                else -> digits
            }
            return if (local.length >= 3) fromPrefix(local.substring(0, 3)) else UNKNOWN
        }
        fun fromApiOperatorName(name: String?): YemenOperator {
            if (name.isNullOrBlank()) return UNKNOWN
            return when {
                name.contains("Sabafon", ignoreCase = true) -> SABAFON
                name.contains("YOU", ignoreCase = true) || name.contains("Yemeni Omani", ignoreCase = true) -> MTN
                name.contains("MTN", ignoreCase = true) -> MTN
                name.contains("Yemen", ignoreCase = true) && name.contains("Mobile", ignoreCase = true) -> YEMEN_MOBILE
                name.contains("HiTel", ignoreCase = true) -> HITEL
                else -> UNKNOWN
            }
        }
    }
}

data class DinstarGatewayStatus(
    val isOnline: Boolean = false,
    val gatewayIp: String = "192.168.11.1",
    val model: String = "UC2000-VE-8G",
    val firmware: String = "",
    val ports: List<DinstarPort> = emptyList(),
    val lastUpdated: Long = 0L
) {
    val registeredCount: Int get() = ports.count { it.registrationState == "REGISTERED" }
    val activeCallCount: Int get() = ports.count { it.callState == "ACTIVE" }
    val availableCount: Int get() = ports.count { it.isAvailable }
    val averageSignal: Int get() {
        val registered = ports.filter { it.registrationState == "REGISTERED" }
        return if (registered.isEmpty()) 0 else registered.map { it.signalPercent }.average().toInt()
    }
    val bestPortForCall: DinstarPort? get() = ports.filter { it.isAvailable }.maxByOrNull { it.signalPercent }
}

data class DinstarCdr(
    val id: String = "",
    val port: Int,
    val phoneNumber: String,
    val direction: String = "outgoing",
    val durationSeconds: Int = 0,
    val startTime: Long = 0L,
    val callState: String = "COMPLETED",
    val costYer: Int = 0
) {
    val operator: YemenOperator get() = YemenOperator.fromNumber(phoneNumber)
}

data class DinstarStatistics(
    val totalCallsToday: Int = 0,
    val totalDurationMinutesToday: Int = 0,
    val totalCostYerToday: Int = 0,
    val callsByOperator: Map<YemenOperator, Int> = emptyMap(),
    val avgSignalAllPorts: Int = 0,
    val successRate: Float = 0f,
    val peakConcurrency: Int = 0
)

sealed class DinstarCommandResult {
    data class Success(val message: String, val data: Map<String, Any?> = emptyMap()) : DinstarCommandResult()
    data class Error(val message: String, val code: Int? = null) : DinstarCommandResult()
    data object Loading : DinstarCommandResult()
}
