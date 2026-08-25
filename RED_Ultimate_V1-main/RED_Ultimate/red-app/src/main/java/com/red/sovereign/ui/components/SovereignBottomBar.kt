package com.red.sovereign.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.MainSection
import com.red.sovereign.ui.theme.SovereignColors

/**
 * شريط التنقّل السفلي السيادي — سطح أكريليكي عائم بحواف متدرّجة،
 * يُبرز القسم النشط بتوهّج زمردي وتكبير خفيف.
 */
@Composable
fun SovereignBottomBar(
    currentSection: MainSection,
    onSectionSelected: (MainSection) -> Unit
) {
    val dimens = rememberAdaptiveDimens()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimens.contentHorizontalPadding,
                vertical = if (dimens.category == ScreenSizeCategory.COMPACT) 6.dp else 10.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.bottomBarHeight)
                .clip(RoundedCornerShape(36.dp))
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            SovereignColors.GlassBorder.copy(alpha = 0.5f),
                            SovereignColors.Emerald.copy(alpha = 0.35f),
                            SovereignColors.GlassBorder.copy(alpha = 0.5f)
                        )
                    ),
                    shape = RoundedCornerShape(36.dp)
                ),
            color = SovereignColors.ObsidianDeep.copy(alpha = 0.94f),
            tonalElevation = 10.dp,
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainSection.entries.forEach { item ->
                    val isSelected = currentSection == item

                    val itemColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            SovereignColors.EmeraldNeon
                        } else {
                            Color(0xFF94A3B8)
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "BottomBarColor"
                    )

                    val itemScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.12f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "BottomBarScale"
                    )

                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(0.85f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (isSelected) {
                                    Brush.verticalGradient(
                                        listOf(
                                            SovereignColors.Emerald.copy(alpha = 0.22f),
                                            SovereignColors.EmeraldDark.copy(alpha = 0.08f)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(Color.Transparent, Color.Transparent)
                                    )
                                }
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSectionSelected(item) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.scale(itemScale)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = itemColor,
                                modifier = Modifier.size(
                                    if (isSelected) {
                                        dimens.primaryIconSize + 3.dp
                                    } else {
                                        dimens.primaryIconSize
                                    }
                                )
                            )
                            if (isSelected) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = itemColor,
                                        fontSize = if (dimens.category == ScreenSizeCategory.COMPACT) {
                                            9.5.sp
                                        } else {
                                            10.5.sp
                                        }
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
