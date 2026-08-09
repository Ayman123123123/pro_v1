package com.red.sovereign.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🎨 YOUNES Sovereign Theme System - Core definitions for legendary components
 */

object SovereignColors {
    val Gold = Color(0xFFF59E0B)
    val GoldLight = Color(0xFFFBBF24)
    val GoldDark = Color(0xFFD97706)
    val Cyan = Color(0xFF38BDF8)
    val CyanDark = Color(0xFF0284C7)
    val Obsidian = Color(0xFF030712)
    val Navy = Color(0xFF0F172A)
    val SurfaceNavy = Color(0xFF1E293B)
    val Danger = Color(0xFFEF4444)
    val DangerDark = Color(0xFFB91C1C)
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val DinstarGold = Color(0xFFF4B400)
    val VoipBlue = Color(0xFF1E88E5)
    val SpacePurple = Color(0xFF8E24AA)
    val LiveRed = Color(0xFFE53935)
}

object SovereignGradients {
    val gold = Brush.horizontalGradient(listOf(SovereignColors.Gold, SovereignColors.GoldLight))
    val cyan = Brush.horizontalGradient(listOf(SovereignColors.CyanDark, SovereignColors.Cyan))
    val danger = Brush.horizontalGradient(listOf(SovereignColors.DangerDark, SovereignColors.Danger))
    val royal = Brush.horizontalGradient(listOf(SovereignColors.Navy, SovereignColors.Cyan))
    val dinstar = Brush.horizontalGradient(listOf(SovereignColors.GoldDark, SovereignColors.DinstarGold))
    val live = Brush.horizontalGradient(listOf(Color(0xFFB71C1C), SovereignColors.LiveRed))
    val space = Brush.horizontalGradient(listOf(Color(0xFF4A148C), SovereignColors.SpacePurple))
}
