package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * مكونات إعادة الاستخدام لشاشات المكالمات — Call Components
 *
 * يوفر مجموعة من المكونات الجاهزة للاستخدام في شاشات المكالمات المختلفة:
 * - CallActionBnutton: زر إجراء المكالمة (قبول/رفض/كتم/كاميرا)
 * - CallPeerAvatar: صورة/أيقونة الطرف الآخر
 * - CallTimer: مؤقت المكالمة
 * - CallQualityIndicator: مؤشر جودة الشبكة
 * - CallStatusBadge: شارة حالة المكالمة
 */

// ── Call Action Button ────────────────────────────────────────────────────────

/**
 * زر إجراء المكالمة الأساسي
 *
 * @param icon الأيقونة المعروضة
 * @param label النص تحت الزر
 * @param color لون الزر
 * @param onClick حدث النقر
 * @param enabled هل الزر مفعل
 * @param size حجم الزر
 */
@Composable
fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 56.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (enabled) color.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                label,
                tint = if (enabled) color else Color.Gray,
                modifier = Modifier.size(size * 0.5f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (enabled) color.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}

// ── Call Peer Avatar ──────────────────────────────────────────────────────────

/**
 * صورة/أيقونة الطرف الآخر في المكالمة
 *
 * @param peerName اسم الطرف الآخر
 * @param isVideo هل المكالمة فيديو
 * @param isOnline هل الطرف متصل
 * @param size حجم الصورة
 */
@Composable
fun CallPeerAvatar(
    peerName: String,
    isVideo: Boolean = false,
    isOnline: Boolean = true,
    size: Dp = 80.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isOnline)
                    AqyalGold.copy(alpha = 0.3f)
                else
                    Color.Gray.copy(alpha = 0.3f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                if (isVideo) Icons.Default.Videocam else Icons.Default.Person,
                "avatar",
                tint = if (isOnline) AqyalGold else Color.Gray,
                modifier = Modifier.size(size * 0.5f)
            )
            if (!isOnline) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "غير متصل",
                    color = Color.Gray,
                    fontSize = 9.sp
                )
            }
        }
    }
}

// ── Call Timer ────────────────────────────────────────────────────────────────

/**
 * مؤقت المكالمة
 *
 * @param seconds مدة المكالمة بالثواني
 * @param isActive هل المؤقت نشط
 */
@Composable
fun CallTimer(
    seconds: Long,
    isActive: Boolean = true
) {
    val formatted = formatCallDuration(seconds)
    Text(
        text = formatted,
        color = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
        fontSize = 20.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

// ── Call Quality Indicator ────────────────────────────────────────────────────

/**
 * مؤشر جودة الشبكة
 *
 * @param quality جودة الشبكة
 * @param showLabel هل يعرض النص
 */
@Composable
fun CallQualityIndicator(
    quality: NetworkQuality,
    showLabel: Boolean = true
) {
    val (color, label) = when (quality) {
        NetworkQuality.EXCELLENT -> Color(0xFF14C79A) to "ممتاز"
        NetworkQuality.GOOD -> Color(0xFF4D9FE8) to "جيد"
        NetworkQuality.FAIR -> Color(0xFFF0B551) to "متوسط"
        NetworkQuality.POOR -> Color(0xFFF25C5C) to "ضعيف"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        if (showLabel) {
            Spacer(Modifier.width(4.dp))
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Call Status Badge ─────────────────────────────────────────────────────────

/**
 * شارة حالة المكالمة
 *
 * @param status حالة المكالمة
 * @param color لون الشارة
 */
@Composable
fun CallStatusBadge(
    status: String,
    color: Color = AqyalGold
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = status,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ── Call Info Row ─────────────────────────────────────────────────────────────

/**
 * صف معلومات المكالمة
 *
 * @param icon الأيقونة
 * @param label.Label
 * @param value القيمة
 */
@Composable
fun CallInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label + ": ", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Helper Functions ──────────────────────────────────────────────────────────

/**
 * تنسيق المدة للعرض
 */
fun formatCallDuration(seconds: Long): String {
    if (seconds <= 0) return "00:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
