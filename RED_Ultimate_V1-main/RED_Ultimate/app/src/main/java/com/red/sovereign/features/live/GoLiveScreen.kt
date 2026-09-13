package com.red.sovereign.features.live

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

// ════════════════════════════════════════════════════════════
//  GO LIVE SCREEN — شاشة بدء البث المباشر
//  @deprecated واجهة تجريبية خارج مخطط البناء (module app/ غير مُجمّع).
//  المسار القانوني: LiveStreamHubDialog (CallsHubActions) + RedExploreScreen.
// ════════════════════════════════════════════════════════════

@Deprecated(
    "Demo outside build graph — use LiveStreamHubDialog / RedExploreScreen",
    ReplaceWith("com.red.sovereign.ui.LiveStreamHubDialog")
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoLiveScreen(
    onStartLive: (title: String, category: String) -> Unit,
    onCancel: () -> Unit
) {
    var title        by remember { mutableStateOf("") }
    var selectedCat  by remember { mutableStateOf("تقنية") }
    var isPreview    by remember { mutableStateOf(false) }

    val categories = listOf("تقنية", "ألعاب", "موسيقى", "تعليم", "ترفيه", "رياضة", "طبخ", "أعمال")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Black, Color(0xFF0A0010), BgPrimary)))
    ) {
        // Ambient glow
        Box(modifier = Modifier.size(300.dp).blur(100.dp).clip(CircleShape)
            .background(RedDeep.copy(0.3f)).align(Alignment.TopCenter).offset(y = 60.dp))
        Box(modifier = Modifier.size(200.dp).blur(80.dp).clip(CircleShape)
            .background(PurpleDeep.copy(0.25f)).align(Alignment.BottomEnd).offset(x = 40.dp, y = (-80).dp))

        Column(
            modifier            = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, "إلغاء", tint = TextPrimary)
                }
                Text("بث مباشر جديد", color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                // Placeholder for alignment
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(20.dp))

            // Camera preview placeholder
            AnimatedContent(targetState = isPreview) { previewing ->
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            if (previewing)
                                Brush.radialGradient(listOf(PurplePrimary.copy(0.4f), Black))
                            else
                                Brush.radialGradient(listOf(SurfaceMid, SurfaceDark))
                        )
                        .border(
                            if (previewing) 2.dp else 1.dp,
                            if (previewing)
                                Brush.linearGradient(GradientRedPrimary)
                            else
                                Brush.linearGradient(listOf(SurfaceLight, SurfaceLight)),
                            RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewing) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Videocam, null, tint = AqyalCyanGlow, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("معاينة الكاميرا", color = TextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { isPreview = true }
                                .padding(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Videocam, null, tint = TextSecondary, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("اضغط لتفعيل الكاميرا", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Camera controls row
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CameraControlBtn(Icons.Rounded.FlipCameraAndroid, "تبديل") { }
                CameraControlBtn(Icons.Rounded.PhotoFilter, "فلتر") { }
                CameraControlBtn(Icons.Rounded.Mic, "ميكروفون") { }
                CameraControlBtn(Icons.Rounded.Tune, "إعدادات") { }
            }

            Spacer(Modifier.height(28.dp))

            // Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(SurfaceDark)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                // Title field
                Text("عنوان البث", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = title,
                    onValueChange = { if (it.length <= 80) title = it },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("أدخل عنواناً جذاباً...", color = TextTertiary, fontSize = 14.sp) },
                    singleLine    = true,
                    shape         = RoundedCornerShape(14.dp),
                    colors        = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor       = SurfaceMid,
                        focusedBorderColor   = RedBrand,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor          = RedBrand,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary
                    ),
                    trailingIcon = {
                        Text("${title.length}/80", color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
                    }
                )

                Spacer(Modifier.height(20.dp))

                // Category
                Text("الفئة", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                val catRows = categories.chunked(4)
                catRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick  = { selectedCat = cat },
                                label    = { Text(cat, fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RedBrand,
                                    selectedLabelColor     = Color.White,
                                    containerColor         = SurfaceMid,
                                    labelColor             = TextSecondary
                                ),
                                border = null,
                                shape  = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(16.dp))

                // Tips
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RedBrand.copy(alpha = 0.08f))
                        .border(1.dp, RedBrand.copy(0.15f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = GoldenAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "نصيحة: استخدم عنواناً واضحاً وجذاباً يصف محتوى بثك لجذب أكبر عدد من المشاهدين.",
                            color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Go Live button
                GoLiveButton(
                    enabled = title.isNotBlank(),
                    onClick = { onStartLive(title, selectedCat) }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  CAMERA CONTROL BUTTON
// ════════════════════════════════════════════════════════════

@Composable
private fun CameraControlBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceMid),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = TextSecondary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextTertiary, fontSize = 10.sp)
    }
}

// ════════════════════════════════════════════════════════════
//  GO LIVE BUTTON WITH PULSE EFFECT
// ════════════════════════════════════════════════════════════

@Composable
private fun GoLiveButton(enabled: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        1f, if (enabled) 1.05f else 1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse)
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        0.3f, if (enabled) 0.6f else 0.3f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse)
    )

    Box(contentAlignment = Alignment.Center) {
        // Pulse glow
        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(RedBrand.copy(alpha = pulseAlpha))
                    .blur(12.dp)
            )
        }

        Button(
            onClick  = onClick,
            enabled  = enabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = RedBrand,
                contentColor           = Color.White,
                disabledContainerColor = SurfaceLight,
                disabledContentColor   = TextTertiary
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (enabled) Color.White else TextTertiary)
                )
                Text(
                    "ابدأ البث الآن",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Icon(Icons.Rounded.LiveTv, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}
