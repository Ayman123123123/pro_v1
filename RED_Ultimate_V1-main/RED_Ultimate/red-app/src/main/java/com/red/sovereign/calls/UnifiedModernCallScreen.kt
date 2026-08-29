package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.components.SovereignGlassCard
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesPrimary

/**
 * شاشة المكالمة الموحدة — Liquid Glass 2026
 *
 * تدمج أفضل ما في واتساب (بساطة) وزوم (شبكة) بتصميم زجاجي سائل:
 * - خلفية متدرجة مع عمق
 * - بطاقة زجاجية عائمة للمتحدث
 * - شريط تحكم زجاجي سفلي
 * - مؤشر جودة حي + خلفية افتراضية
 * - تحترم reduceMotion و highContrast
 */
@Composable
fun ModernLiquidGlassCallScreen(
    isVideo: Boolean = false,
    isMuted: Boolean = false,
    isVideoEnabled: Boolean = true,
    onToggleMic: () -> Unit = {},
    onToggleVideo: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onToggleBackground: () -> Unit = {}
) {
    val quality by CallQualityManager.stats.collectAsState()
    val bg = VirtualBackgroundManager.config

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0A0F18),
                        Color(0xFF131C29),
                        Color(0xFF0A0F18)
                    )
                )
            )
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── الرأس: جودة + خلفية ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // مؤشر الجودة
                Surface(
                    color = when (quality.quality) {
                        NetworkQuality.EXCELLENT -> Color(0xFF14C79A)
                        NetworkQuality.GOOD -> Color(0xFF4D9FE8)
                        NetworkQuality.FAIR -> Color(0xFFE0B551)
                        NetworkQuality.POOR -> Color(0xFFF25C5C)
                    }.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "● ${CallQualityManager.labelFor(quality.quality)}  ${quality.bitrateKbps}kbps",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                // خلفية افتراضية
                Surface(
                    color = SovereignColors.GlassBgLiquid,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = when (bg.effect) {
                            VirtualBgEffect.NONE -> "بدون خلفية"
                            VirtualBgEffect.BLUR -> "ضبابي"
                            VirtualBgEffect.BLUR_HEAVY -> "ضبابي كثيف"
                            VirtualBgEffect.SOLID -> "لون ثابت"
                            VirtualBgEffect.IMAGE -> "صورة"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // ── وسط: بطاقة المتحدث الزجاجية ────────────────────────────────
            SovereignGlassCard(
                cornerRadius = 28.dp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // صورة/فيديو وهمية
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(YounesPrimary, SovereignColors.Cyan)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ي", color = Color(0xFF06090F), fontSize = 48.sp, fontWeight = FontWeight.Black)
                    }
                    Text(
                        "مكالمة يونس السيادية",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isVideo) "فيديو • مشفّر E2EE" else "صوت • مشفّر E2EE",
                        color = Color(0xFF9FB0C2),
                        fontSize = 12.sp
                    )
                    if (VirtualBackgroundManager.shouldApplyComposeBlur()) {
                        Text(
                            "خلفية ضبابية مفعلة (${VirtualBackgroundManager.blurRadiusForCompose().toInt()}px)",
                            color = YounesPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ── شريط التحكم الزجاجي السفلي — Liquid Glass عائم ──────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(36.dp)),
                color = SovereignColors.GlassBgLiquid,
                shadowElevation = 16.dp,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleMic,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0xFFF25C5C) else Color(0xFF1B2635))
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "ميكروفون",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onToggleVideo,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (!isVideoEnabled) Color(0xFFF25C5C) else Color(0xFF1B2635))
                    ) {
                        Icon(
                            imageVector = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "كاميرا",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF212E40))
                    ) {
                        Icon(Icons.Default.VolumeUp, "مكبر", tint = Color.White)
                    }
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE03131))
                    ) {
                        Icon(Icons.Default.CallEnd, "إنهاء", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
