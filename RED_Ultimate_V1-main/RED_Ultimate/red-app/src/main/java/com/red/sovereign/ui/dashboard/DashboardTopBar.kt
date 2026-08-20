package com.red.sovereign.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.red.sovereign.R

/**
 * ترويسة ثابتة للوحة يونس. فصلها عن منطق التنقل يحد من إعادة تركيب الشاشة
 * الكاملة عندما تتغير أزرار البحث أو الإعدادات.
 */
@Composable
internal fun DashboardTopBar(
    redId: String,
    username: String,
    compact: Boolean,
    onSettings: () -> Unit,
    onSearch: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painterResource(R.drawable.younes_icon_master),
                contentDescription = "يونس",
                modifier = Modifier
                    .size(if (compact) 34.dp else 40.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    "يونس • @$username",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    redId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                IconButton(onSearch) {
                    Icon(Icons.Default.Search, "البحث الشامل", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                IconButton(onSettings) {
                    Icon(Icons.Default.Settings, "الإعدادات", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
