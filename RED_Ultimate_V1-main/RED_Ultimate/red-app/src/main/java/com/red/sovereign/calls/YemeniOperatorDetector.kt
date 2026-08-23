package com.red.sovereign.calls

import androidx.compose.ui.graphics.Color

data class OperatorInfo(
    val name: String,
    val brandColor: Color,
    val technology: String
)

/**
 * كاشف operator يمني — خطة الترقيم الصحيحة (مطابقة لـ YemenNumberPlan في الباكند):
 *
 *  - 700-709 = واي Y Telecom   (كانت معكوسة سابقاً!)
 *  - 710-719 = سبأفون Sabafon  (كانت تُعرض "Y واي" خطأً)
 *  - 730-739 = يو YOU — MTN سابقاً حتى 2021 (كانت تُعرض "سبأفون" خطأً)
 *  - 770-789 = يمن موبايل Yemen Mobile
 *  - 10x     = يمن 4G (بيانات ثابتة، ليس محمولاً)
 *
 * أمثلة:
 *  - "712064924" → سبأفون
 *  - "773456789" → يمن موبايل
 *  - "733456789" → يو (YOU)
 *  - "703456789" → واي (Y)
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
            clean.startsWith("70") -> OperatorInfo("واي (Y)", Color(0xFF00A1E4), "GSM/4G")
            clean.startsWith("71") -> OperatorInfo("سبأفون", Color(0xFFFDB913), "GSM/3G/4G") // Sabafon Gold/Yellow
            clean.startsWith("73") -> OperatorInfo("يو (YOU)", Color(0xFFFFF200), "GSM/4G") // YOU Yellow
            clean.startsWith("77") || clean.startsWith("78") ->
                OperatorInfo("يمن موبايل", Color(0xFFE31E24), "GSM/4G") // Yemen Mobile Red
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
