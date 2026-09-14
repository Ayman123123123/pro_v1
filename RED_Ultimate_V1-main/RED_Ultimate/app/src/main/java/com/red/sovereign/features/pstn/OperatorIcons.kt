package com.red.sovereign.features.pstn

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.SignalCellular4Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.WifiCalling
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * أيقونات مزودي الخدمة اليمنيين (Task 9).
 *
 * البديل المعتمد من TASK_LIST: Material Icons مع ألوان مميزة لكل مزود
 * (بدل Vector Drawable ثقيلة). الدائرة الملونة بالحرف الأول تُعرض في
 * DialPadScreen و RecentCallRow و IncomingPstnCallScreen عبر
 * OperatorInfo.brandColor + iconLetter — وهذا الملف يوحّد الأيقونة
 * لكل مشغل كي لا تتشتت بين الشاشات.
 */
object OperatorIcons {
    fun iconFor(key: String): ImageVector = when (key) {
        "YemenMobile" -> Icons.Rounded.SignalCellular4Bar
        "Sabafon" -> Icons.Rounded.SimCard
        "YOU" -> Icons.Rounded.WifiCalling
        "YTelecom" -> Icons.Rounded.SignalCellularAlt
        "Yemen4G" -> Icons.Rounded.CellTower
        else -> Icons.Rounded.SimCard
    }

    fun colorFor(key: String): Color = when (key) {
        "YemenMobile" -> Color(0xFFE31E24)
        "Sabafon" -> Color(0xFFFDB913)
        "YOU" -> Color(0xFFFFF200)
        "YTelecom" -> Color(0xFF00A1E4)
        "Yemen4G" -> Color(0xFF009688)
        else -> Color(0xFF616161)
    }

    /** كل المشغلين المعروضين في الـ UI بنفس ترتيب TASK_LIST. */
    fun allKeys(): List<String> = listOf("YemenMobile", "Sabafon", "YOU", "YTelecom", "Yemen4G")
}
