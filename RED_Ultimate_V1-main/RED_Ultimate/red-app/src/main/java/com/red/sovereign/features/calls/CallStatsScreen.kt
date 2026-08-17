package com.red.sovereign.features.calls

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.red.sovereign.calls.CallHistoryViewModel
import com.red.sovereign.calls.formatCallDuration
import com.red.sovereign.ui.components.SovereignGlassCard
import com.red.sovereign.ui.components.SovereignNeonButton
import com.red.sovereign.ui.components.SovereignStatusBadge
import com.red.sovereign.ui.theme.CairoFamily
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.SovereignGradients
import com.red.sovereign.ui.theme.TajawalFamily

/**
 * 📊 Call Statistics & Analytics Dashboard — تحليلات وإحصائيات المكالمات الفاخرة
 */
@Composable
fun CallStatsScreen(
    viewModel: CallHistoryViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val stats = remember(viewModel.calls.size) { viewModel.getStats() }

    fun shareCsv() {
        val file = viewModel.exportCsvFile(context) ?: return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "سجل مكالمات RED السيادي")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "مشاركة سجل المكالمات CSV"))
    }

    Scaffold(
        containerColor = SovereignColors.Obsidian,
        topBar = {
            Surface(
                color = SovereignColors.ObsidianDeep,
                tonalElevation = 6.dp,
                modifier = Modifier.border(0.dp, Color.Transparent, RoundedCornerShape(0.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SovereignColors.SurfaceCard)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "تحليلات وإحصائيات الاتصال",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontFamily = CairoFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "سجل الأمان والرؤى الاستراتيجية",
                                color = SovereignColors.EmeraldNeon,
                                fontSize = 12.sp,
                                fontFamily = TajawalFamily
                            )
                        }
                    }

                    IconButton(
                        onClick = { shareCsv() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SovereignColors.Gold.copy(alpha = 0.2f))
                            .border(1.dp, SovereignColors.GoldNeon.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "تصدير CSV",
                            tint = SovereignColors.GoldNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // شارة الحالة
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SovereignStatusBadge(
                        label = "لوحة البيانات التحليلية الحية ⚡",
                        glowColor = SovereignColors.EmeraldNeon
                    )
                }
            }

            // الكروت الرئيسية العلوية (إجمالي المكالمات والدقائق)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.PhoneInTalk,
                        title = "إجمالي المكالمات",
                        value = "${stats.totalCalls}",
                        subtitle = "${stats.answeredCalls} مستلمة · ${stats.missedCalls} فائتة",
                        accentColor = SovereignColors.CyanNeon
                    )

                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Timer,
                        title = "وقت التحدث",
                        value = stats.totalDurationSeconds.formatCallDuration().ifEmpty { "0 ثانية" },
                        subtitle = "معدل اتصال مشفر",
                        accentColor = SovereignColors.GoldNeon
                    )
                }
            }

            // معدل النجاح %
            item {
                SovereignGlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SovereignColors.Emerald.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = SovereignColors.EmeraldNeon,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "معدل نجاح واستقرار الاتصال",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${stats.successRate}%",
                                color = SovereignColors.EmeraldNeon,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { stats.successRate / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = SovereignColors.EmeraldNeon,
                            trackColor = SovereignColors.SurfaceCard
                        )
                    }
                }
            }

            // توزيع أنواع المكالمات (فيديو، صوت، DINSTAR GSM)
            item {
                Text(
                    text = "توزيع القنوات والمسارات",
                    color = Color.White.copy(0.85f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = CairoFamily,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChannelMiniCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Videocam,
                        label = "مرئية",
                        count = stats.videoCallsCount,
                        color = Color(0xFFA78BFA)
                    )
                    ChannelMiniCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Mic,
                        label = "صوتية",
                        count = stats.voiceCallsCount,
                        color = SovereignColors.EmeraldNeon
                    )
                    ChannelMiniCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.CellTower,
                        label = "DINSTAR",
                        count = stats.dinstarCallsCount,
                        color = SovereignColors.GoldNeon
                    )
                }
            }

            // جهة الاتصال الأكثر تواصلاً وساعة الذروة
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SovereignGlassCard(modifier = Modifier.weight(1f)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Star, null, tint = SovereignColors.GoldNeon, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("الأكثر تواصلاً", color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stats.topPeer?.first ?: "لا يوجد بعد",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (stats.topPeer != null) {
                                Text(
                                    text = "${stats.topPeer.second} مكالمة",
                                    color = SovereignColors.GoldNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    SovereignGlassCard(modifier = Modifier.weight(1f)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Schedule, null, tint = SovereignColors.CyanNeon, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ساعة الذروة", color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (stats.peakHour != null) "${stats.peakHour}:00" else "متوازن",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "أعلى نشاط اتصالي",
                                color = SovereignColors.CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // زر تصدير التقرير الكامل
            item {
                Spacer(Modifier.height(10.dp))
                SovereignNeonButton(
                    text = "مشاركة وتصدير التقرير الكامل (CSV)",
                    icon = Icons.Rounded.FileDownload,
                    gradient = SovereignGradients.dinstar,
                    onClick = { shareCsv() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color
) {
    SovereignGlassCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = Color.White.copy(0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(0.55f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun ChannelMiniCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    count: Int,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SovereignColors.SurfaceCard)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text("$count", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = Color.White.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
