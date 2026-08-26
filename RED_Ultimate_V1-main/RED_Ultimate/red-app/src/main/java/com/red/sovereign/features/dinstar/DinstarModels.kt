package com.red.sovereign.features.dinstar

import androidx.compose.ui.graphics.Color

//
// Ù†Ù…Ø§Ø°Ø¬ Ø¨ÙˆØ§Ø¨Ø© Dinstar UC2000-VE.
//
// âš ï¸ Ø­Ø§Ù„Ø© Ø§Ù„Ø§Ø³ØªØ¹Ù…Ø§Ù„ (ØªØ­Ù‚Ù‘Ù‚ 2026-08-19): Ø§Ù„Ù…Ø³ØªØ¹Ù…ÙŽÙ„ Ø­ÙŠÙ‹Ù‘Ø§ Ù…Ù† Ù‡Ø°Ø§ Ø§Ù„Ù…Ù„Ù Ù‡Ùˆ
// [YemenOperator] ÙˆØ­Ø¯Ù‡ â€” ÙŠØ¹ØªÙ…Ø¯ Ø¹Ù„ÙŠÙ‡ `calls/YemeniOperatorDetector.kt`
// Ùˆ`ui/components/SovereignUiComponents.kt` ÙÙŠ 19 Ù…ÙˆØ¶Ø¹Ù‹Ø§.
//
// Ø£Ù…Ø§ Ø¨Ù‚ÙŠÙ‘Ø© Ø§Ù„Ù†Ù…Ø§Ø°Ø¬ ([DinstarPort]ØŒ [DinstarGatewayStatus]ØŒ
// [DinstarFleetStatus]ØŒ [DinstarCdr]ØŒ [DinstarStatistics]ØŒ
// [DinstarIncomingSms]ØŒ [DinstarDeviceStatus]ØŒ [DinstarCommandResult])
// ÙÙƒØ§Ù† Ù…Ø³ØªÙ‡Ù„ÙƒÙ‡Ø§ Ø§Ù„ÙˆØ­ÙŠØ¯ `DinstarViewModel`ØŒ ÙˆÙ‚Ø¯ Ø£ÙØ±Ø´Ù: ÙƒØ§Ù† ÙŠØ³ØªØ¯Ø¹ÙŠ Ø£Ø­Ø¯
// Ø¹Ø´Ø± Ù…Ø³Ø§Ø±Ù‹Ø§ ØªØ­Øª `/api/admin/*` Ù…Ù† ØªØ·Ø¨ÙŠÙ‚ Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù… Ø§Ù„Ø¹Ø§Ø¯ÙŠØŒ ÙˆÙƒÙ„Ù‡Ø§
// ØªØªØ·Ù„Ø¨ Ø¯ÙˆØ± ADMIN ÙÙŠ `SecurityConfig` ÙØªØ±Ø¯Ù‘ 403. Ø¥Ø¯Ø§Ø±Ø© Ø£Ø³Ø·ÙˆÙ„ Ø§Ù„Ø¨ÙˆØ§Ø¨Ø§Øª
// Ù…ÙƒØ§Ù†Ù‡Ø§ Ù„ÙˆØ­Ø© Ø§Ù„Ø¥Ø¯Ø§Ø±Ø© Ù„Ø§ Ø¬Ù‡Ø§Ø² Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù….
//
// Ø£ÙØ¨Ù‚ÙŠØª Ù‡Ø°Ù‡ Ø§Ù„Ù†Ù…Ø§Ø°Ø¬ Ù„Ø£Ù†Ù‡Ø§ ØªØµÙ Ø¹Ù‚Ø¯ API Ø§Ù„Ø¨ÙˆØ§Ø¨Ø© Ø§Ù„ÙØ¹Ù„ÙŠ ÙƒÙ…Ø§ ÙˆØ«Ù‘Ù‚ØªÙ‡
// Â«Dinstar GSM Gateway HTTP APIÂ»ØŒ ÙÙ‡ÙŠ Ù…Ø±Ø¬Ø¹ ØµØ­ÙŠØ­ Ù„Ø£ÙŠ ÙˆØµÙ„Ù Ù‚Ø§Ø¯Ù… ÙˆÙ„Ø§
// ØªÙØ¯Ø®Ù„ Ø³Ù„ÙˆÙƒÙ‹Ø§ ÙÙŠ Ø§Ù„ØªØ·Ø¨ÙŠÙ‚ Ù…Ø§ Ø¯Ø§Ù…Øª ØºÙŠØ± Ù…Ø³ØªØ¯Ø¹Ø§Ø©.
//

data class DinstarPort(
    val index: Int,
    val radioType: String = "GSM",
    val registrationState: String = "UNREGISTERED",
    val callState: String = "IDLE",
    /**
     * Ø§Ù„Ù†Ø³Ø¨Ø© Ø§Ù„Ù…Ø¦ÙˆÙŠØ© Ù„Ù„Ø¹Ø±Ø¶. **Ù‚Ø§Ø¨Ù„Ø© Ù„Ø£Ù† ØªÙƒÙˆÙ† null**: Ø­ÙŠÙ† ØªÙØ¨Ù„Ù‘Øº Ø§Ù„Ø¨ÙˆØ§Ø¨Ø©
     * Ù‚Ø±Ø§Ø¡Ø© Â«ØºÙŠØ± Ù‚Ø§Ø¨Ù„Ø© Ù„Ù„ÙƒØ´ÙÂ» Ù„Ø§ ÙŠÙˆØ¬Ø¯ Ù‚ÙŠØ§Ø³ Ø£ØµÙ„Ù‹Ø§ØŒ ÙˆØ¹Ø±Ø¶ ØµÙØ± Ø£Ùˆ Ù…Ø¦Ø©
     * ÙƒÙ„Ø§Ù‡Ù…Ø§ ÙƒØ°Ø¨. Ø§Ù„ØµÙØ± Ø§Ù„Ø³Ø§Ø¨Ù‚ ÙƒØ§Ù† ÙŠÙØ®ÙÙŠ Ø§Ù„ÙØ±Ù‚ Ø¨ÙŠÙ† Â«Ø¥Ø´Ø§Ø±Ø© Ù…Ø¹Ø¯ÙˆÙ…Ø©Â»
     * ÙˆÂ«Ù„Ø§ Ù‚ÙŠØ§Ø³Â».
     */
    val signalPercent: Int? = null,
    /** Ø§Ù„Ù‚ÙˆØ© Ø§Ù„ÙØ¹Ù„ÙŠØ© Ø¨Ø§Ù„Ù€ dBmØŒ Ø£Ùˆ null Ø¹Ù†Ø¯ ØªØ¹Ø°Ù‘Ø± Ø§Ù„Ù‚ÙŠØ§Ø³. */
    val signalDbm: Int? = null,
    /** Ø§Ù„Ù‚Ø±Ø§Ø¡Ø© Ø§Ù„Ø®Ø§Ù… Ù…Ù† `AT+CSQ`. Ø§Ù„Ù‚ÙŠÙ…Ø© 99 ØªØ¹Ù†ÙŠ Â«ØºÙŠØ± Ù‚Ø§Ø¨Ù„Ø© Ù„Ù„ÙƒØ´ÙÂ». */
    val signalRaw: Int? = null,
    /** Ù‡Ù„ Ø§Ù„Ø¥Ø´Ø§Ø±Ø© ÙƒØ§ÙÙŠØ© Ù„Ø­Ù…Ù„ Ù…ÙƒØ§Ù„Ù…Ø© (â€Žâ‰¥ -100 dBm)ØŸ */
    val signalUsable: Boolean = false,
    val gprsState: String = "DETACH",
    val operatorName: String = "ØºÙŠØ± Ù…Ø¹Ø±ÙˆÙ",
    val numberMasked: String? = null,
    val imsiMasked: String? = null,
    val iccidMasked: String? = null,
    val simType: YemenOperator = YemenOperator.UNKNOWN
) {
    /**
     * Ø¬Ø§Ù‡Ø² Ù„Ø­Ù…Ù„ Ù…ÙƒØ§Ù„Ù…Ø©.
     *
     * ÙƒØ§Ù† Ø§Ù„Ø´Ø±Ø· `signalPercent >= 20` Ù…Ø­Ø³ÙˆØ¨Ù‹Ø§ Ù…Ù† Ù†Ø³Ø¨Ø© Ù…ØºÙ„ÙˆØ·Ø©: Ø§Ù„Ù‚Ø±Ø§Ø¡Ø©
     * 99 (Ù„Ø§ Ø´Ø¨ÙƒØ©) ÙƒØ§Ù†Øª ØªÙÙ‚ØµØ± Ø¹Ù„Ù‰ 31 ÙØªÙÙ†ØªØ¬ 100%ØŒ ÙØªØ¨Ø¯Ùˆ Ø§Ù„Ø´Ø±ÙŠØ­Ø© Ø§Ù„Ù…ÙŠØªØ©
     * Ø§Ù„Ø£ÙØ¶Ù„. Ø§Ù„Ø´Ø±Ø· Ø§Ù„Ø¢Ù† ÙŠØ¹ØªÙ…Ø¯ Ù‚ÙŠØ§Ø³Ù‹Ø§ ÙØ¹Ù„ÙŠÙ‹Ø§ Ø¨Ø§Ù„Ù€ dBm.
     */
    val isAvailable: Boolean
        get() = registrationState == "REGISTERED" && callState == "IDLE" && signalUsable

    /** Ù…Ø³Ø¬Ù‘Ù„Ø© Ø¹Ù„Ù‰ Ø§Ù„Ø´Ø¨ÙƒØ© Ù„ÙƒÙ† Ø¨Ù„Ø§ Ø¥Ø´Ø§Ø±Ø© ØµØ§Ù„Ø­Ø© â€” Ø­Ø§Ù„Ø© ØªØ³ØªØ­Ù‚ Ø§Ù„ØªÙ†Ø¨ÙŠÙ‡. */
    val isRegisteredButUnusable: Boolean
        get() = registrationState == "REGISTERED" && !signalUsable

    val statusDescriptionAr: String
        get() = when {
            callState == "ACTIVE" -> "ÙÙŠ Ù…ÙƒØ§Ù„Ù…Ø©"
            callState == "RINGING" -> "ÙŠØ±Ù†"
            registrationState != "REGISTERED" -> "ØºÙŠØ± Ù…Ø³Ø¬Ù„"
            signalDbm == null -> "Ù„Ø§ ÙŠÙˆØ¬Ø¯ Ù‚ÙŠØ§Ø³ Ø¥Ø´Ø§Ø±Ø©"
            signalDbm < -100 -> "Ø¥Ø´Ø§Ø±Ø© ØºÙŠØ± ÙƒØ§ÙÙŠØ©"
            signalDbm < -95 -> "Ø¥Ø´Ø§Ø±Ø© Ø¶Ø¹ÙŠÙØ©"
            else -> "Ø¬Ø§Ù‡Ø²"
        }

    /** Ù†Øµ Ø§Ù„Ù‚ÙˆØ© Ù„Ù„Ø¹Ø±Ø¶ â€” Ù„Ø§ ÙŠØ®ØªÙ„Ù‚ Ø±Ù‚Ù…Ù‹Ø§ Ø­ÙŠÙ† Ù„Ø§ ÙŠÙˆØ¬Ø¯ Ù‚ÙŠØ§Ø³. */
    val signalLabelAr: String
        get() = signalDbm?.let { "$it dBm" } ?: "â€”"
}

/**
 * Ù…Ø´ØºÙ„Ùˆ Ø§Ù„Ù‡Ø§ØªÙ Ø§Ù„Ù…Ø­Ù…ÙˆÙ„ ÙÙŠ Ø§Ù„ÙŠÙ…Ù†.
 *
 * **ØªØµØ­ÙŠØ­ Ø§Ù„Ø¨Ø§Ø¯Ø¦Ø§Øª:** ÙƒØ§Ù†Øª Ø§Ù„Ø®Ø±ÙŠØ·Ø© Ø§Ù„Ø³Ø§Ø¨Ù‚Ø© ØªÙ†Ø³Ø¨ `77x` Ø¥Ù„Ù‰ Ø³Ø¨Ø£ÙÙˆÙ†
 * Ùˆ`73x` Ø¥Ù„Ù‰ ÙŠÙ…Ù† Ù…ÙˆØ¨Ø§ÙŠÙ„ Ùˆ`71x` Ø¥Ù„Ù‰ MTN â€” ÙˆÙƒÙ„Ù‡Ø§ Ù…Ø¹ÙƒÙˆØ³Ø©. Ø§Ù„Ø¨Ø§Ø¯Ø¦Ø© Ø§Ù„ØµØ­ÙŠØ­Ø©
 * ØªÙØ­Ø¯ÙŽÙ‘Ø¯ Ø¨Ø£ÙˆÙ„ Ø±Ù‚Ù…ÙŠÙ† Ø¨Ø¹Ø¯ `+967` Ø­Ø³Ø¨ Ø®Ø·Ø© Ø§Ù„ØªØ±Ù‚ÙŠÙ… Ø§Ù„ÙŠÙ…Ù†ÙŠØ©:
 *
 * | Ø§Ù„Ø¨Ø§Ø¯Ø¦Ø© | Ø§Ù„Ù…Ø´ØºÙ„ |
 * |---------|--------|
 * | 71 | Ø³Ø¨Ø£ÙÙˆÙ† |
 * | 73 | ÙŠÙˆ (ÙƒØ§Ù†Øª MTN Ø­ØªÙ‰ Ø¥Ø¹Ø§Ø¯Ø© Ø§Ù„ØªØ³Ù…ÙŠØ© ÙÙŠ 2021) |
 * | 77ØŒ 78 | ÙŠÙ…Ù† Ù…ÙˆØ¨Ø§ÙŠÙ„ |
 * | 70 | ÙˆØ§ÙŠ (ÙƒØ§Ù†Øª HiTel) |
 *
 * Ø§Ù„Ø®Ø·Ø£ Ù„Ù… ÙŠÙƒÙ† ØªØ¬Ù…ÙŠÙ„ÙŠÙ‹Ø§: `fromNumber` ØªÙØ³ØªØ®Ø¯Ù… Ù„Ø¹Ø±Ø¶ Ù…Ø´ØºÙ„ Ø§Ù„Ù…ØªØµÙ„ ÙˆÙ„Ù…Ø·Ø§Ø¨Ù‚Ø©
 * Ø§Ù„Ø´Ø±ÙŠØ­Ø©ØŒ ÙÙƒØ§Ù† Ø§Ù„Ø§Ø®ØªÙŠØ§Ø± ÙŠÙ‚Ø¹ Ø¹Ù„Ù‰ Ø´Ø±ÙŠØ­Ø© Ø´Ø¨ÙƒØ© Ø£Ø®Ø±Ù‰ ÙˆØªÙØ­ØªØ³Ø¨ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø©
 * Ø¨ØªØ¹Ø±ÙØ© Ø®Ø§Ø±Ø¬ Ø§Ù„Ø´Ø¨ÙƒØ©.
 */
enum class YemenOperator(
    val arabicName: String,
    val englishName: String,
    /** Ø§Ù„Ø¨Ø§Ø¯Ø¦Ø§Øª Ø§Ù„Ù…ÙƒÙˆÙ‘Ù†Ø© Ù…Ù† Ø±Ù‚Ù…ÙŠÙ† Ø¨Ø¹Ø¯ Ø±Ù…Ø² Ø§Ù„Ø¯ÙˆÙ„Ø©. */
    val prefixes: Set<String>,
    val color: Color
) {
    /**
     * `71` Ø§Ù„Ù†Ø·Ø§Ù‚ Ø§Ù„Ø£ØµÙ„ÙŠ (ØµÙ†Ø¹Ø§Ø¡ ÙˆØ¹Ù…ÙˆÙ… Ø§Ù„Ø¨Ù„Ø§Ø¯ØŒ ÙˆÙ…Ù†Ù‡ `718` Ø¹Ø¯Ù† Ø§Ù„Ù‚Ø¯ÙŠÙ…).
     * `722` Ù†Ø·Ø§Ù‚ Ø¹Ø¯Ù† Ù„Ù„Ø¬ÙŠÙ„ Ø§Ù„Ø±Ø§Ø¨Ø¹ (VoLTE) â€” Ø£ÙØ·Ù„Ù‚ Ù…Ø³ØªÙ‚Ù„Ù‹Ù‘Ø§ Ù„Ø§ Ø§Ù…ØªØ¯Ø§Ø¯Ù‹Ø§
     * Ù„Ù€`71`ØŒ ÙÙŠØ¬Ø¨ Ø°ÙƒØ±Ù‡ ØµØ±Ø§Ø­Ø©Ù‹ ÙˆØ¥Ù„Ø§ Ù‚ÙØ±Ø¦ `72` ÙˆØ³Ù‚Ø· ÙÙŠ Â«ØºÙŠØ± Ù…Ø¹Ø±ÙˆÙÂ».
     */
    SABAFON("Ø³Ø¨Ø£ÙÙˆÙ†", "Sabafon", setOf("71", "722"), Color(0xFFE53935)),
    YOU("ÙŠÙˆ", "YOU", setOf("73"), Color(0xFFFFB300)),
    YEMEN_MOBILE("ÙŠÙ…Ù† Ù…ÙˆØ¨Ø§ÙŠÙ„", "YemenMobile", setOf("77", "78"), Color(0xFF43A047)),
    Y_TELECOM("ÙˆØ§ÙŠ", "YTelecom", setOf("70"), Color(0xFF1E88E5)),
    /**
     * Ù„ÙŠØ³ Ù…Ø´ØºÙ‘Ù„Ù‹Ø§ Ø¨Ù„ ØºÙŠØ§Ø¨ ØªØ¹Ø±ÙÙ‘ÙØŒ ÙÙ„ÙˆÙ†Ù‡ Ù…Ø­Ø§ÙŠØ¯ Ù„Ø§ Ù‡ÙˆÙŠØ© ØªØ¬Ø§Ø±ÙŠØ© Ù„Ù‡.
     * `9AAEBB` Ù‡Ùˆ Ù„ÙˆÙ† Ø§Ù„Ù†Øµ Ø§Ù„Ø«Ø§Ù†ÙˆÙŠ ÙÙŠ Ù„ÙˆØ­Ø© Ø§Ù„ØªØ·Ø¨ÙŠÙ‚ (`RedTheme.kt`):
     * Ø§Ù„Ø±Ù…Ø§Ø¯ÙŠ Ø§Ù„Ø³Ø§Ø¨Ù‚ `757575` ÙƒØ§Ù† ÙŠØ¨Ù„Øº 4.15:1 Ø¹Ù„Ù‰ Ø§Ù„Ø®Ù„ÙÙŠØ© â€” Ø¯ÙˆÙ† Ø­Ø¯Ù‘
     * AA â€” Ø¨ÙŠÙ†Ù…Ø§ Ù‡Ø°Ø§ ÙŠØ¨Ù„Øº 8.34:1. ÙˆØ£Ù„ÙˆØ§Ù† Ø§Ù„Ù…Ø´ØºÙ‘Ù„ÙŠÙ† Ø§Ù„Ø£Ø±Ø¨Ø¹Ø© Ø£Ø¹Ù„Ø§Ù‡
     * Ø£Ù„ÙˆØ§Ù† Ø¹Ù„Ø§Ù…Ø§Øª ØªØ¬Ø§Ø±ÙŠØ© ÙØªØ¨Ù‚Ù‰ ÙƒÙ…Ø§ Ù‡ÙŠØŒ ÙˆÙƒÙ„Ù‡Ø§ ØªØªØ¬Ø§ÙˆØ² 4.5:1.
     */
    UNKNOWN("ØºÙŠØ± Ù…Ø¹Ø±ÙˆÙ", "Unknown", setOf(), Color(0xFF9AAEBB));

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
            // Ø§Ù„Ø£Ø·ÙˆÙ„ Ø£ÙˆÙ„Ù‹Ø§: 722 (Ø³Ø¨Ø£ÙÙˆÙ† Ø¹Ø¯Ù† 4G) ÙŠØ³Ø¨Ù‚ 72 ÙˆØ¥Ù„Ø§ Ø³Ù‚Ø· ÙÙŠ UNKNOWN
            if (local.length >= 3) {
                val three = fromPrefix(local.substring(0, 3))
                if (three != UNKNOWN) return three
            }
            return if (local.length >= 2) fromPrefix(local.substring(0, 2)) else UNKNOWN
        }

        fun fromApiOperatorName(name: String?): YemenOperator {
            if (name.isNullOrBlank()) return UNKNOWN
            return when {
                name.contains("Sabafon", ignoreCase = true) || name.contains("Ø³Ø¨Ø£ÙÙˆÙ†") -> SABAFON
                // MTN Ø§Ù„ÙŠÙ…Ù† ØµØ§Ø±Øª YOU ÙÙŠ 2021 â€” Ø§Ù„Ø§Ø³Ù…Ø§Ù† Ù„Ù…Ø´ØºÙ„ ÙˆØ§Ø­Ø¯
                name.contains("YOU", ignoreCase = true) || name.contains("MTN", ignoreCase = true) ||
                    name.contains("Yemeni Omani", ignoreCase = true) || name.contains("ÙŠÙˆ") -> YOU
                name.contains("Yemen", ignoreCase = true) && name.contains("Mobile", ignoreCase = true) -> YEMEN_MOBILE
                name.contains("ÙŠÙ…Ù† Ù…ÙˆØ¨Ø§ÙŠÙ„") -> YEMEN_MOBILE
                // HiTel Ø£ÙØ¹ÙŠØ¯ ØªØ³Ù…ÙŠØªÙ‡Ø§ Y Telecom
                name.contains("HiTel", ignoreCase = true) || name.contains("Y Telecom", ignoreCase = true) ||
                    name.contains("ÙˆØ§ÙŠ") -> Y_TELECOM
                else -> UNKNOWN
            }
        }
    }
}

data class DinstarGatewayStatus(
    /** Ù…Ø¹Ø±Ù‘Ù Ø§Ù„Ø¨ÙˆØ§Ø¨Ø© ÙÙŠ Ø³Ø¬Ù„ Ø§Ù„Ø£Ø³Ø·ÙˆÙ„ â€” Ù„Ø§Ø²Ù… Ù„Ù„ØªÙ…ÙŠÙŠØ² Ø¨ÙŠÙ† Ø¹Ø¯Ø© Ø£Ø¬Ù‡Ø²Ø©. */
    val gatewayId: String? = null,
    val name: String = "",
    val isOnline: Boolean = false,
    val gatewayIp: String = "",
    /** Ø§Ù„Ø·Ø±Ø§Ø² ÙƒÙ…Ø§ Ù‡Ùˆ Ù…Ø³Ø¬ÙŽÙ‘Ù„ØŒ Ù„Ø§ Ù‚ÙŠÙ…Ø© Ù…Ø«Ø¨ÙŽÙ‘ØªØ©: Ù‚Ø¯ ÙŠÙƒÙˆÙ† 8G Ø£Ùˆ 8T Ø£Ùˆ Ø±Ø¨Ø§Ø¹ÙŠÙ‹Ø§. */
    val model: String = "",
    val firmware: String = "",
    val ports: List<DinstarPort> = emptyList(),
    val lastUpdated: Long = 0L
) {
    val registeredCount: Int get() = ports.count { it.registrationState == "REGISTERED" }
    val activeCallCount: Int get() = ports.count { it.callState == "ACTIVE" }
    val availableCount: Int get() = ports.count { it.isAvailable }

    /** Ù…Ø³Ø¬Ù‘Ù„Ø© Ù„ÙƒÙ† Ø¨Ù„Ø§ Ø¥Ø´Ø§Ø±Ø© ØµØ§Ù„Ø­Ø© â€” Ø§Ù„ÙØ¬ÙˆØ© Ø§Ù„ØªÙŠ ÙƒØ§Ù†Øª Ù…Ø®ÙÙŠØ© Ø®Ù„Ù Ù†Ø³Ø¨Ø© 100%. */
    val registeredButUnusableCount: Int get() = ports.count { it.isRegisteredButUnusable }

    /**
     * Ù…ØªÙˆØ³Ø· Ø§Ù„Ø¥Ø´Ø§Ø±Ø© Ø¨Ø§Ù„Ù€ dBm Ù„Ù„Ù…Ù†Ø§ÙØ° Ø§Ù„ØªÙŠ **Ù„Ù‡Ø§ Ù‚ÙŠØ§Ø³ ÙØ¹Ù„ÙŠ**.
     * Ø§Ù„Ù…Ù†Ø§ÙØ° Ø¨Ù„Ø§ Ù‚ÙŠØ§Ø³ ØªÙØ³ØªØ¨Ø¹Ø¯ Ø¨Ø¯Ù„ Ø§Ø­ØªØ³Ø§Ø¨Ù‡Ø§ ØµÙØ±Ù‹Ø§ (ÙƒØ§Ù† ÙŠØ¬Ø±Ù‘ Ø§Ù„Ù…ØªÙˆØ³Ø·
     * Ø¥Ù„Ù‰ Ø§Ù„Ø£Ø³ÙÙ„) Ø£Ùˆ 100% (ÙƒØ§Ù† ÙŠØ±ÙØ¹Ù‡ ÙƒØ°Ø¨Ù‹Ø§).
     */
    val averageSignalDbm: Int? get() {
        val measured = ports.mapNotNull { it.signalDbm }
        return if (measured.isEmpty()) null else measured.average().toInt()
    }

    /**
     * Ø£ÙØ¶Ù„ Ù…Ù†ÙØ° Ù„Ù„Ù…ÙƒØ§Ù„Ù…Ø©. Ø§Ù„ØªØ±ØªÙŠØ¨ Ø¨Ø§Ù„Ù€ dBm Ù„Ø§ Ø¨Ø§Ù„Ù†Ø³Ø¨Ø©ØŒ Ùˆ`isAvailable`
     * ÙŠØ¶Ù…Ù† Ø§Ø³ØªØ¨Ø¹Ø§Ø¯ Ù…Ø§ Ù„Ø§ ÙŠØ­Ù…Ù„ Ù…ÙƒØ§Ù„Ù…Ø©. Ø§Ù„Ù‚Ø±Ø§Ø± Ø§Ù„Ù†Ù‡Ø§Ø¦ÙŠ Ù„Ù„ØªÙˆØ¬ÙŠÙ‡ ÙŠØªØ®Ø°Ù‡
     * Ø§Ù„Ø®Ø§Ø¯Ù… â€” Ù‡Ø°Ø§ Ù„Ù„Ø¹Ø±Ø¶ ÙˆØ§Ù„ØªØ´Ø®ÙŠØµ ÙÙ‚Ø·.
     */
    val bestPortForCall: DinstarPort? get() =
        ports.filter { it.isAvailable }.maxByOrNull { it.signalDbm ?: Int.MIN_VALUE }
}

/**
 * Ø­Ø§Ù„Ø© Ø§Ù„Ø£Ø³Ø·ÙˆÙ„ ÙƒØ§Ù…Ù„Ù‹Ø§ â€” Ø¹Ø¯Ø© Ø¨ÙˆØ§Ø¨Ø§Øª Ù…Ø¹Ù‹Ø§.
 *
 * Ø§Ù„Ù†Ù…ÙˆØ°Ø¬ Ø§Ù„Ø³Ø§Ø¨Ù‚ ÙƒØ§Ù† ÙŠÙØªØ±Ø¶ Ø¨ÙˆØ§Ø¨Ø© ÙˆØ§Ø­Ø¯Ø© (`DinstarGatewayStatus` Ù…ÙØ±Ø¯Ø©)ØŒ
 * ÙÙ„Ù… ÙŠÙƒÙ† Ù…Ù…ÙƒÙ†Ù‹Ø§ Ø¹Ø±Ø¶ Ø¬Ù‡Ø§Ø²ÙŠÙ† Ø£Ùˆ Ù…Ø¹Ø±ÙØ© Ø£ÙŠÙ‘Ù‡Ù…Ø§ Ø­Ù…Ù„ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø©.
 */
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

    /** Â«14 Ù…Ø³Ø¬Ù‘Ù„Ø©ØŒ Ù…Ù†Ù‡Ø§ 10 Ø¬Ø§Ù‡Ø²Ø©Â» â€” Ø§Ù„ÙØ±Ù‚ Ø§Ù„Ø°ÙŠ ÙŠØ­ØªØ§Ø¬Ù‡ Ø§Ù„Ù…Ø³Ø¤ÙˆÙ„. */
    val summaryAr: String
        get() = "$registeredPorts Ø´Ø±ÙŠØ­Ø© Ù…Ø³Ø¬Ù‘Ù„Ø©ØŒ Ù…Ù†Ù‡Ø§ $usablePorts Ø¬Ø§Ù‡Ø²Ø©"
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

/**
 * Ø±Ø³Ø§Ù„Ø© SMS ÙˆØ§Ø±Ø¯Ø© Ø¹Ù„Ù‰ Ø¥Ø­Ø¯Ù‰ Ø´Ø±Ø§Ø¦Ø­ Ø§Ù„Ø¨ÙˆØ§Ø¨Ø©.
 *
 * `port` Ù‡Ùˆ ÙÙ‡Ø±Ø³ Ø§Ù„Ù…Ù†ÙØ° Ø§Ù„Ø°ÙŠ Ø§Ø³ØªÙ‚Ø¨Ù„Ù‡Ø§ â€” ÙŠÙØ¹Ø±ÙŽÙ‘Ù Ø¨Ù€ -1 Ø­ÙŠÙ† Ù„Ø§ ØªØ±Ø³Ù„Ù‡
 * Ø§Ù„Ø¨ÙˆØ§Ø¨Ø©ØŒ ÙÙ„Ø§ ÙŠÙØ®Ù„Ø· Ø¨Ø§Ù„Ù…Ù†ÙØ° 0 Ø§Ù„Ø­Ù‚ÙŠÙ‚ÙŠ.
 */
data class DinstarIncomingSms(
    val port: Int = -1,
    val number: String = "",
    val text: String = "",
    val timestamp: String = ""
)

/**
 * Ù‚ÙŠØ§Ø³Ø§Øª Ø¹ØªØ§Ø¯ Ø§Ù„Ø¨ÙˆØ§Ø¨Ø© Ù†ÙØ³Ù‡Ø§ â€” Ù„Ø§ Ø­Ø§Ù„Ø© Ø§Ù„Ù…Ù†Ø§ÙØ°.
 *
 * Ù…ØµØ¯Ø±Ù‡Ø§ `/api/get_status` Ø¹Ù„Ù‰ Ø§Ù„Ø¬Ù‡Ø§Ø²ØŒ ÙŠÙ…Ø±Ù‘Ø±Ù‡Ø§ Ø§Ù„Ø®Ø§Ø¯Ù… Ø¹Ø¨Ø±
 * `/api/admin/dinstar/device-status` ÙˆÙŠØ­ÙØ¸Ù‡Ø§ ÙÙŠ `dinstar_device_status`.
 *
 * ÙƒÙ„ Ø§Ù„Ø­Ù‚ÙˆÙ„ Ù†ØµÙŠØ© `String?` Ø¹Ù…Ø¯Ù‹Ø§: Ø§Ù„Ø¨ÙˆØ§Ø¨Ø© ØªÙØ±Ø¬Ø¹Ù‡Ø§ Ø¨ÙˆØ­Ø¯Ø§Øª Ù…ÙÙ„Ø­Ù‚Ø©
 * (`"45%"`, `"128MB"`, `"47C"`) ÙˆØªØªÙØ§ÙˆØª Ø¨ÙŠÙ† Ø§Ù„Ø¥ØµØ¯Ø§Ø±Ø§ØªØŒ ÙØªØ­ÙˆÙŠÙ„Ù‡Ø§ Ø¥Ù„Ù‰ Ø£Ø±Ù‚Ø§Ù…
 * Ù‡Ù†Ø§ ÙŠÙÙ‚Ø¯ Ø§Ù„ÙˆØ­Ø¯Ø© ÙˆÙŠÙƒØ³Ø± Ø¹Ù†Ø¯ ØµÙŠØºØ© ØºÙŠØ± Ù…ØªÙˆÙ‚ÙŽÙ‘Ø¹Ø©. Ø§Ù„Ø¹Ø±Ø¶ ÙŠØ¨Ù‚Ù‰ ÙƒÙ…Ø§ Ø£Ø±Ø³Ù„Ù‡ Ø§Ù„Ø¬Ù‡Ø§Ø².
 *
 * Ø§Ø³ØªÙØ¹ÙŠØ¯ Ù‡Ø°Ø§ Ø§Ù„Ù†Ù…ÙˆØ°Ø¬ ÙÙŠ 2026-08-19: ÙƒØ§Ù†Øª Ø§Ù„Ø³Ù„Ø³Ù„Ø© Ù…Ù‚Ø·ÙˆØ¹Ø© Ø¹Ù†Ø¯ Ø§Ù„ØªØ·Ø¨ÙŠÙ‚ â€”
 * Ø§Ù„Ø¬Ù‡Ø§Ø² ÙŠÙÙ†ØªØ¬ Ø§Ù„Ù‚ÙŠØ§Ø³Ø§Øª ÙˆØ§Ù„Ø®Ø§Ø¯Ù… ÙŠØ®Ø²Ù‘Ù†Ù‡Ø§ ÙˆÙ„Ø§ Ø´ÙŠØ¡ ÙŠØ³ØªÙ‡Ù„ÙƒÙ‡Ø§.
 */
data class DinstarDeviceStatus(
    val cpuUsed: String? = null,
    val memoryTotal: String? = null,
    val memoryUsed: String? = null,
    val memoryFree: String? = null,
    val flashTotal: String? = null,
    val flashUsed: String? = null,
    val flashFree: String? = null,
    /** Ø­Ø±Ø§Ø±Ø© Ø§Ù„Ù„ÙˆØ­Ø© â€” Ø§Ù„Ù…Ø¤Ø´Ø± Ø§Ù„Ø£Ø¨ÙƒØ± Ø¹Ù„Ù‰ Ø§Ø®ØªÙ†Ø§Ù‚ Ø­Ø±Ø§Ø±ÙŠ ÙŠØ³Ø¨Ù‚ Ø³Ù‚ÙˆØ· Ø§Ù„Ù…Ù†Ø§ÙØ°. */
    val temperature: String? = null,
    val uptime: String? = null
) {
    /** true Ø­ÙŠÙ† Ù„Ù… ØªØµÙ„ Ø£ÙŠ Ù‚ÙŠÙ…Ø© â€” Ù„Ù„ØªÙ…ÙŠÙŠØ² Ø¨ÙŠÙ† "Ù„Ù… ÙŠÙØ³ØªØ¹Ù„Ù… Ø¨Ø¹Ø¯" Ùˆ"Ø¬Ù‡Ø§Ø² ØµØ§Ù…Øª". */
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

