package com.red.sovereign.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * نظام ثيم يونس السيادي — التعريفات الأساسية للمكوّنات الفاخرة
 * (أوبسيديان داكن + زجاج ضبابي + حواف نيون).
 *
 * ملاحظة معمارية: هذا الملف هو **المصدر الوحيد** لألوان `SovereignColors`
 * وتدرجات `SovereignGradients`. لا تُعرِّف ألوانًا موازية في ملفات المكوّنات.
 */

object SovereignColors {
    // ── الذهب الإمبراطوري والسيادة اليمنية ──────────────────────────────────
    val Gold = Color(0xFFF59E0B)
    val GoldLight = Color(0xFFFBBF24)
    val GoldDark = Color(0xFFD97706)
    val GoldNeon = Color(0xFFFFD700)
    val DinstarGold = Color(0xFFF4B400)

    // ── الزمرد السيبراني والأمان / التشفير التام E2EE ───────────────────────
    val Emerald = Color(0xFF10B981)
    val EmeraldDark = Color(0xFF059669)
    val EmeraldNeon = Color(0xFF00E676)
    val Success = Color(0xFF10B981)

    // ── السماوي الكهربائي وإشارات WebRTC Mesh ───────────────────────────────
    val Cyan = Color(0xFF38BDF8)
    val CyanDark = Color(0xFF0284C7)
    val CyanNeon = Color(0xFF00E5FF)
    val VoipBlue = Color(0xFF1E88E5)

    // ── الأوبسيديان العميق والأسطح الأكريليكية ──────────────────────────────
    val Obsidian = Color(0xFF030712)
    val ObsidianDeep = Color(0xFF060A12)
    val Navy = Color(0xFF0F172A)
    val SurfaceNavy = Color(0xFF1E293B)
    val SurfaceCard = Color(0xFF151F32)
    val SurfaceDialog = Color(0xFF1A263D)

    // ── وهج الياقوت والتنبيهات / إنهاء المكالمة / البث المباشر ──────────────
    val Danger = Color(0xFFEF4444)
    val DangerDark = Color(0xFFB91C1C)
    val RubyNeon = Color(0xFFFF1744)
    val LiveRed = Color(0xFFE53935)
    val Warning = Color(0xFFF59E0B)

    // ── بنفسج الفضاء ومؤتمرات SFU ───────────────────────────────────────────
    val SpacePurple = Color(0xFF8E24AA)
    val PurpleNeon = Color(0xFFC084FC)

    // ── طبقات الزجاج الضبابي (Glassmorphism) ────────────────────────────────
    val GlassBg = Color(0x1F1E293B)
    val GlassBorder = Color(0x3394A3B8)
    val GlassHighlight = Color(0x22FFFFFF)
}

object SovereignGradients {
    val gold = Brush.horizontalGradient(
        listOf(SovereignColors.GoldDark, SovereignColors.Gold, SovereignColors.GoldLight)
    )
    val emerald = Brush.horizontalGradient(
        listOf(SovereignColors.EmeraldDark, SovereignColors.Emerald, SovereignColors.EmeraldNeon)
    )
    val cyan = Brush.horizontalGradient(
        listOf(SovereignColors.CyanDark, SovereignColors.Cyan, SovereignColors.CyanNeon)
    )
    val danger = Brush.horizontalGradient(
        listOf(SovereignColors.DangerDark, SovereignColors.Danger, SovereignColors.RubyNeon)
    )
    val royal = Brush.horizontalGradient(
        listOf(SovereignColors.Navy, SovereignColors.CyanDark, SovereignColors.Cyan)
    )
    val dinstar = Brush.horizontalGradient(
        listOf(SovereignColors.GoldDark, SovereignColors.DinstarGold, SovereignColors.GoldLight)
    )
    val live = Brush.horizontalGradient(
        listOf(Color(0xFFB71C1C), SovereignColors.LiveRed, SovereignColors.RubyNeon)
    )
    val space = Brush.horizontalGradient(
        listOf(Color(0xFF4A148C), SovereignColors.SpacePurple, SovereignColors.PurpleNeon)
    )

    val glassCard = Brush.linearGradient(
        listOf(
            Color(0x2E1E293B),
            Color(0x140F172A)
        )
    )

    val neonBorderGold = Brush.linearGradient(
        listOf(
            SovereignColors.Gold.copy(alpha = 0.8f),
            SovereignColors.Gold.copy(alpha = 0.2f),
            SovereignColors.GoldLight.copy(alpha = 0.8f)
        )
    )

    val neonBorderEmerald = Brush.linearGradient(
        listOf(
            SovereignColors.Emerald.copy(alpha = 0.8f),
            SovereignColors.Emerald.copy(alpha = 0.2f),
            SovereignColors.EmeraldNeon.copy(alpha = 0.8f)
        )
    )

    val neonBorderCyan = Brush.linearGradient(
        listOf(
            SovereignColors.Cyan.copy(alpha = 0.8f),
            SovereignColors.Cyan.copy(alpha = 0.2f),
            SovereignColors.CyanNeon.copy(alpha = 0.8f)
        )
    )
}
