package com.red.sovereign.features.dinstar

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box

@Composable
fun DinstarSmsScreen(viewModel: DinstarViewModel, onBack: () -> Unit) {
    Box {}
}

internal fun gatewaySegmentCount(text: String, encoding: String): Int {
    if (text.isEmpty()) return 0
    val ucs2 = when (encoding.uppercase()) {
        "UCS2" -> true
        "GSM7BIT" -> false
        else -> text.any { it.code > 0x7F }
    }
    val single = if (ucs2) 70 else 160
    val multi = if (ucs2) 67 else 153
    return if (text.length <= single) 1 else 1 + (text.length - single + multi - 1) / multi
}
