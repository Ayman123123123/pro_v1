package com.red.sovereign.calls

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.red.sovereign.ui.theme.CairoFamily
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.SovereignGradients

data class SovereignGift(
    val id: String,
    val name: String,
    val emoji: String,
    val costCoins: Int,
    val description: String,
    val glowColor: Color
)

object SovereignGiftsCatalog {
    val ALL = listOf(
        SovereignGift("gift_crown", "تاج الأقيال", "👑", 500, "تكريم ملكي رفيع", SovereignColors.GoldNeon),
        SovereignGift("gift_falcon", "الصقر السبئي", "🦅", 300, "رمز الشموخ والسيادة", SovereignColors.EmeraldNeon),
        SovereignGift("gift_diamond", "ماسة RED", "💎", 200, "بريق التشفير اللامع", SovereignColors.CyanNeon),
        SovereignGift("gift_sword", "سيف حمير", "⚔️", 150, "رمز القوة والعزة", SovereignColors.Gold),
        SovereignGift("gift_rocket", "صاروخ الفضاء", "🚀", 100, "انطلاقة فائقة للبث", SovereignColors.RubyNeon),
        SovereignGift("gift_heart", "قلب الأمان", "💖", 50, "محبة وتقدير فوري", Color(0xFFFF4081))
    )
}

/**
 * 🎁 نافذة إهداء الهدايا الفاخرة للبث المباشر والمساحات
 */
@Composable
fun LiveGiftsSheet(
    onDismiss: () -> Unit,
    onSendGift: (SovereignGift) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(1.2.dp, SovereignColors.GlassBorder, RoundedCornerShape(24.dp)),
            color = SovereignColors.ObsidianDeep.copy(alpha = 0.95f),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(SovereignColors.Gold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎁", fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                text = "متجر الهدايا السيادية",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontFamily = CairoFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ادعم المضيف بمؤثرات تفاعلية حية",
                                color = SovereignColors.GoldNeon,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SovereignColors.SurfaceCard)
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                // Gifts Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(240.dp)
                ) {
                    items(SovereignGiftsCatalog.ALL, key = { it.id }) { gift ->
                        GiftItemCard(gift = gift, onClick = {
                            onSendGift(gift)
                            onDismiss()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun GiftItemCard(
    gift: SovereignGift,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SovereignColors.SurfaceCard)
            .border(1.dp, gift.glowColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = gift.emoji, fontSize = 28.sp)
            Text(
                text = gift.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Diamond,
                    contentDescription = null,
                    tint = SovereignColors.GoldNeon,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "${gift.costCoins}",
                    color = SovereignColors.GoldNeon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
