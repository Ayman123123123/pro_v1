package com.red.sovereign.features.dinstar

import androidx.compose.ui.graphics.Color

data class DinstarPort(
    val index: Int,
    val radioType: String = "GSM",
    val registrationState: String = "UNREGISTERED",
    val callState: String = "IDLE",
    val signalPercent: Int? = null,
    val signalDbm: Int? = null,
    val signalRaw: Int? = null,
    val signalUsable: Boolean = false,
    val gprsState: String = "DETACH",
    val operatorName: String = "غير معروف",
    val numberMasked: String? = null,
    val imsiMasked: String? = null,
    val iccidMasked: String? = null,
    val simType: YemenOperator = YemenOperator.UNKNOWN
) {
    val isAvailable: Boolean
        get() = registrationState == "REGISTERED" && callState == "IDLE" && signalUsable
    val isRegisteredButUnusable: Boolean
        get() = registrationState == "REGISTERED" && !signalUsable
    val statusDescriptionAr: String
        get() = when {
            callState == "ACTIVE" -> "في مكالمة"
            callState == "RINGING" -> "يرن"
            registrationState != "REGISTERED" -> "غير مسجل"
            signalDbm == null -> "لا يوجد قياس إشارة"
            signalDbm < -100 -> "إشارة غير كافية"
            signalDbm < -95 -> "إشارة ضعيفة"
            else -> "جاهز"
        }
    val signalLabelAr: String
        get() = signalDbm?.let { "$it dBm" } ?: "لا قياس"
}

enum class YemenOperator(
    val arabicName: String,
    val englishName: String,
    val prefixes: Set<String>,
    val color: Color
) {
    SABAFON("سبأفون", "Sabafon", setOf("71", "722"), Color(0xFFF25C5C)),
    YOU("يو", "YOU", setOf("73"), Color(0xFFFFC24D)),
    YEMEN_MOBILE("يمن موبايل", "YemenMobile", setOf("77", "78"), Color(0xFF5FD97A)),
    Y_TELECOM("واي", "YTelecom", setOf("70"), Color(0xFF4D9FE8)),
    UNKNOWN("غير معروف", "Unknown", setOf(), Color(0xFF9FB0C2));

    companion object {
        fun fromPrefix(prefix: String): YemenOperator =
            entries.firstOrNull { prefix in it.prefixes } ?: UNKNOWN

        fun fromNumber(number: String): YemenOperator {
            val digits = number.filter { it.isDigit() }
            val local = when {
                digits.startsWith("00967") -> digits.removePrefix("00967")
                digits.startsWith("967") -> digits.removePrefix("967")
                digits.startsWith("0") -> digits.removePrefix("0")
                else -> digits
            }
            if (local.length >= 3) {
                val three = fromPrefix(local.substring(0, 3))
                if (three != UNKNOWN) return three
            }
            return if (local.length >= 2) fromPrefix(local.substring(0, 2)) else UNKNOWN
        }

        fun fromApiOperatorName(name: String?): YemenOperator {
            if (name.isNullOrBlank()) return UNKNOWN
            return when {
                name.contains("Sabafon", ignoreCase = true) || name.contains("سبأفون") -> SABAFON
                name.contains("YOU", ignoreCase = true) || name.contains("MTN", ignoreCase = true) ||
                    name.contains("Yemeni Omani", ignoreCase = true) || name.contains("يو") -> YOU
                name.contains("Yemen", ignoreCase = true) && name.contains("Mobile", ignoreCase = true) -> YEMEN_MOBILE
                name.contains("يمن موبايل") -> YEMEN_MOBILE
                name.contains("HiTel", ignoreCase = true) || name.contains("Y Telecom", ignoreCase = true) ||
                    name.contains("واي") -> Y_TELECOM
                else -> UNKNOWN
            }
        }
    }
}

data class DinstarGatewayStatus(
    val gatewayId: String? = null,
    val name: String = "",
    val isOnline: Boolean = false,
    val gatewayIp: String = "",
    val model: String = "",
    val firmware: String = "",
    val ports: List<DinstarPort> = emptyList(),
    val lastUpdated: Long = 0L
) {
    val registeredCount: Int get() = ports.count { it.registrationState == "REGISTERED" }
    val activeCallCount: Int get() = ports.count { it.callState == "ACTIVE" }
    val availableCount: Int get() = ports.count { it.isAvailable }
    val registeredButUnusableCount: Int get() = ports.count { it.isRegisteredButUnusable }
    val averageSignalDbm: Int? get() {
        val measured = ports.mapNotNull { it.signalDbm }
        return if (measured.isEmpty()) null else measured.average().toInt()
    }
    val bestPortForCall: DinstarPort? get() =
        ports.filter { it.isAvailable }.maxByOrNull { it.signalDbm ?: Int.MIN_VALUE }
}

data class DinstarFleetStatus(
    val gateways: List<DinstarGatewayStatus> = emptyList(),
    val lastUpdated: Long = 0L
) {
    val gatewayCount: Int get() = gateways.size
    val onlineCount: Int get() = gateways.count { it.isOnline }
    val totalPorts: Int get() = gateways.sumOf { it.ports.size }
    val registeredPorts: Int get() = gateways.sumOf { it.registeredCount }
    val usablePorts: Int get() = gateways.sumOf { it.availableCount }
    val activeCalls: Int get() = gateways.sumOf { it.activeCallCount }
    val summaryAr: String
        get() = "$registeredPorts شريحة مسجّلة، منها $usablePorts جاهزة"
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

data class DinstarIncomingSms(
    val gatewayId: String? = null,
    val port: Int = -1,
    val number: String = "",
    val text: String = "",
    val receivedAt: Long = System.currentTimeMillis()
)

data class DinstarSmsResult(
    val taskId: Long? = null,
    val numbers: List<String> = emptyList(),
    val status: String,
    val queueCount: Int? = null,
    val ports: List<Int> = emptyList(),
    val encoding: String = "AUTO",
    val at: Long = System.currentTimeMillis()
) {
    val isSuccess: Boolean get() = status.equals("ACCEPTED", true) || status.equals("SENT_OK", true)
    val successCount: Int get() = if (isSuccess) numbers.size else 0
}

data class DinstarDeviceStatus(
    val cpuUsed: String? = null,
    val memoryTotal: String? = null,
    val memoryUsed: String? = null,
    val memoryFree: String? = null,
    val flashTotal: String? = null,
    val flashUsed: String? = null,
    val flashFree: String? = null,
    val temperature: String? = null,
    val uptime: String? = null
) {
    val isEmpty: Boolean
        get() = listOf(
            cpuUsed, memoryTotal, memoryUsed, memoryFree,
            flashTotal, flashUsed, flashFree, temperature, uptime
        ).all { it.isNullOrBlank() }
}

sealed class DinstarCommandResult {
    data class Success(val message: String, val data: Map<String, Any?> = emptyMap()) : DinstarCommandResult()
    data class Error(val message: String, val code: Int? = null) : DinstarCommandResult()
    data object Loading : DinstarCommandResult()
}

data class DinstarSms(
    val id: String = java.util.UUID.randomUUID().toString(),
    val number: String,
    val content: String,
    val direction: String = "OUT",
    val timestamp: Long = System.currentTimeMillis()
)
