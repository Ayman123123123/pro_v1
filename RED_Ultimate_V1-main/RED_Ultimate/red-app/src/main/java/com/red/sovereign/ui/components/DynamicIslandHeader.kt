package com.red.sovereign.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.SovereignColors

/**
 * 🏝️ DynamicIslandHeader — الكبسولة الديناميكية العائمة (Soft Twilight 2026)
 *
 * تعرض ملخصاً فخماً عائماً في أعلى الشاشات للمكالمة النشطة، التسجيل الصوتي،
 * أو حالة اتصال الشبكة بأسلوب مريح للعين دون حجب الواجهة.
 */
@Composable
fun DynamicIslandHeader(
    isVisible: Boolean,
    title: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Rounded.Call,
    iconTint: Color = SovereignColors.Emerald,
    onClick: () -> Unit = {},
    contentDescription: String? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    val widthState by animateDpAsState(
        targetValue = if (isExpanded) 280.dp else 190.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "island_width"
    )
    // وصف ديناميكي حسب الحالة: مكالمة / تسجيل / شبكة — يُمرَّر صراحةً عند الحاجة.
    val resolvedDesc = contentDescription ?: when (icon) {
        Icons.Rounded.Call -> "مكالمة نشطة: $title"
        Icons.Rounded.Mic -> "تسجيل جارٍ: $title"
        Icons.Rounded.Wifi -> "حالة الشبكة: $title"
        else -> title
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .width(widthState)
                    .height(38.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, SovereignColors.GlassBorder, RoundedCornerShape(20.dp))
                    .clickable {
                        isExpanded = !isExpanded
                        onClick()
                    },
                color = SovereignColors.SurfaceNavy.copy(alpha = 0.92f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(iconTint)
                            .semantics { this.contentDescription = resolvedDesc }
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = resolvedDesc,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (subtitle != null && isExpanded) {
                        Text(
                            text = "· $subtitle",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
