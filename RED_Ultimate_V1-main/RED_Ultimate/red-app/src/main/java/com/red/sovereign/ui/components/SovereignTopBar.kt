package com.red.sovereign.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.R
import com.red.sovereign.ui.theme.SovereignColors

/**
 * الشريط العلوي السيادي — رأس زجاجي يعرض الهوية وحالة التشفير التام
 * والإجراءات السريعة (بحث، إعدادات).
 */
@Composable
fun SovereignTopBar(
    redId: String,
    username: String,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onProfileClick: () -> Unit = {},
    isEncrypted: Boolean = true
) {
    val dimens = rememberAdaptiveDimens()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimens.contentHorizontalPadding,
                vertical = dimens.headerVerticalPadding
            )
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        SovereignColors.GlassBorder.copy(alpha = 0.4f),
                        SovereignColors.Gold.copy(alpha = 0.35f),
                        SovereignColors.GlassBorder.copy(alpha = 0.4f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            ),
        color = SovereignColors.ObsidianDeep.copy(alpha = 0.90f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // الصورة الشخصية داخل حلقة ذهبية
            Box(
                modifier = Modifier
                    .size(dimens.avatarSize)
                    .clip(CircleShape)
                    .border(1.5.dp, SovereignColors.Gold, CircleShape)
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.younes_icon_master),
                    contentDescription = "الملف الشخصي",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.width(11.dp))

            // بيانات الحساب والهوية السيادية
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onProfileClick)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = username.ifBlank { "يونس السيادي" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = dimens.titleFontSize
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "موثَّق سياديًّا",
                        tint = SovereignColors.GoldNeon,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    // مؤشّر الأمان والتشفير التام بين الطرفين
                    if (isEncrypted) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SovereignColors.Emerald.copy(alpha = 0.15f))
                                .border(
                                    width = 0.8.dp,
                                    color = SovereignColors.EmeraldNeon.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = SovereignColors.EmeraldNeon,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "مشفَّر E2EE",
                                    color = SovereignColors.EmeraldNeon,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Spacer(Modifier.width(7.dp))
                    }

                    Text(
                        text = redId,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SovereignColors.Cyan.copy(alpha = 0.85f),
                            fontSize = dimens.subtitleFontSize,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // أزرار البحث والإعدادات السريعة
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SovereignColors.SurfaceCard)
                        .border(1.dp, SovereignColors.GlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "البحث الشامل",
                        tint = Color.White,
                        modifier = Modifier.size(dimens.secondaryIconSize)
                    )
                }

                IconButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SovereignColors.SurfaceCard)
                        .border(1.dp, SovereignColors.GlassBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "الإعدادات",
                        tint = Color.White,
                        modifier = Modifier.size(dimens.secondaryIconSize)
                    )
                }
            }
        }
    }
}
