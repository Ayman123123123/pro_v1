package com.red.sovereign.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.red.sovereign.settings.SettingsRuntime

/**
 * مقاسات شاشة يونس — هاتف ضيق، عادي، لوحي.
 * لا ثيم فاتح. فقط مسافات وعرض الفقاعة حتى لا تنكسر الواجهة على 5 بوصات أو جهاز عريض.
 */
data class WindowLayout(
    val widthDp: Int,
    val heightDp: Int,
    val isCompactWidth: Boolean,
    val isCompactHeight: Boolean,
    val isWide: Boolean,
    val pagePadding: Dp,
    val bubbleMax: Dp,
    val compactChrome: Boolean,
) {
    companion object {
        @Composable
        fun current(): WindowLayout {
            val config = LocalConfiguration.current
            val w = config.screenWidthDp
            val h = config.screenHeightDp
            val compactW = w < 360
            val compactH = h < 640
            val wide = w >= 600
            val userCompact = SettingsRuntime.current.compactMode
            return WindowLayout(
                widthDp = w,
                heightDp = h,
                isCompactWidth = compactW,
                isCompactHeight = compactH,
                isWide = wide,
                pagePadding = when {
                    wide -> 28.dp
                    compactW -> 10.dp
                    else -> 14.dp
                },
                bubbleMax = (w * 0.82f).dp.coerceIn(220.dp, if (wide) 440.dp else 340.dp),
                compactChrome = userCompact || compactH || compactW,
            )
        }
    }
}
