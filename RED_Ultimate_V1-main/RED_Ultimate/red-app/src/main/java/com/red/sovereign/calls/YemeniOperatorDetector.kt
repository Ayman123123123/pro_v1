package com.red.sovereign.calls

import androidx.compose.ui.graphics.Color

data class OperatorInfo(
    val name: String,
    val brandColor: Color,
    val technology: String
)

/**
 * كاشف operator يمني — يكتشف شبكة المتصل بناء على بادئة الرقم.
 *
 * أرقام اليمن بعد إزالة +967 / 00967 / 0:
 *  - 7X = موبايل (77 يمن موبايل، 73 سبأفون، 71 Y، 70 YU)
 *  - 2X, 3X, 4X, 5X = هاتف ثابت (المحافظات)
 *  - 1X = هاتف ثابت صنعاء (واتساب)
 *
 * أمثلة اختبار:
 *  - "+967773456789" → "يمن موبايل"
 *  - "733456789"     → "سبأفون"
 *  - "713456789"     → "Y (واي)"
 *  - "703456789"     → "يمن (يو)"
 *  - "1234567"       → "هاتف ثابت (صنعاء)"
 *  - "2345678"       → "هاتف ثابت (عدن)"
 */
object YemeniOperatorDetector {
    fun getOperatorInfo(number: String): OperatorInfo? {
        if (number.isBlank()) return null
        val clean = number.filter { it.isDigit() }
            .removePrefix("00967")
            .removePrefix("+967")
            .removePrefix("967")
            .removePrefix("0")
        if (clean.isEmpty()) return null

        return when {
            // موبايل: prefix 7X
            clean.startsWith("77") || clean.startsWith("78") -> OperatorInfo("يمن موبايل", Color(0xFFE31E24), "CDMA/4G/5G")
            clean.startsWith("73") -> OperatorInfo("سبأفون", Color(0xFFFDB913), "GSM/3G/4G")
            clean.startsWith("71") -> OperatorInfo("Y (واي)", Color(0xFF00A1E4), "GSM/4G")
            clean.startsWith("70") -> OperatorInfo("يمن (يو)", Color(0xFFFFF200), "GSM/4G")
            clean.startsWith("10") -> OperatorInfo("يمن 4G", Color(0xFF009688), "LTE")
            // هاتف ثابت: prefix 1X-5X
            clean.startsWith("1") -> OperatorInfo("هاتف ثابت (صنعاء)", Color(0xFF607D8B), "PSTN")
            clean.startsWith("2") -> OperatorInfo("هاتف ثابت (عدن)", Color(0xFF455A64), "PSTN")
            clean.startsWith("3") -> OperatorInfo("هاتف ثابت (الحديدة)", Color(0xFF455A64), "PSTN")
            clean.startsWith("4") -> OperatorInfo("هاتف ثابت (تعز)", Color(0xFF455A64), "PSTN")
            clean.startsWith("5") -> OperatorInfo("هاتف ثابت (إب)", Color(0xFF455A64), "PSTN")
            else -> null
        }
    }
}
