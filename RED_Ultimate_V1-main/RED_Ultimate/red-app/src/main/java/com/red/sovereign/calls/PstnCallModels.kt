package com.red.sovereign.calls

enum class PstnCallStatus {
    IDLE,
    REGISTERING,
    BRIDGING,
    INVITING,
    RINGING,
    EARLY_MEDIA,
    ACTIVE,
    ENDED,
    ERROR
}

data class CallMetrics(
    val jitterMs: Float = 0f,
    val packetLossPercent: Float = 0f,
    val roundTripMs: Float = 0f,
    val errors: List<String> = emptyList(),
    val dailyLimit: Int = 1000,
    val usedToday: Int = 0
)

internal fun formatPstnDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
