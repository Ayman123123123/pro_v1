package com.red.sovereign.calls

import androidx.compose.ui.graphics.Color
import com.red.sovereign.features.dinstar.YemenOperator

data class OperatorInfo(
    val name: String,
    val brandColor: Color,
    val technology: String,
    val apiName: String = "",
    val isMobile: Boolean = true
)

object YemeniOperatorDetector {
    fun getOperatorInfo(number: String): OperatorInfo? = null
}
