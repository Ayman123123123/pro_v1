package com.red.sovereign.calls

import androidx.compose.ui.graphics.Color

data class OperatorInfo(
    val name: String,
    val brandColor: Color,
    val technology: String
)

object YemeniOperatorDetector {
    fun getOperatorInfo(number: String): OperatorInfo? {
        val clean = number.filter { it.isDigit() }.removePrefix("00967").removePrefix("+967").removePrefix("0")
        if (clean.length < 2) return null
        
        return when (clean.take(2)) {
            "77" -> OperatorInfo("يمن موبايل", Color(0xFFE31E24), "CDMA/4G/5G")
            "73" -> OperatorInfo("سبأفون", Color(0xFFFDB913), "GSM/3G/4G")
            "71" -> OperatorInfo("Y (واي)", Color(0xFF00A1E4), "GSM/4G")
            "70" -> OperatorInfo("يمن (يو)", Color(0xFFFFF200), "GSM/4G")
            "1" -> OperatorInfo("هاتف ثابت (صنعاء)", Color(0xFF607D8B), "PSTN")
            else -> null
        }
    }
}
