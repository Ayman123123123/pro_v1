package com.red.sovereign.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.launch

/**
 * منتقي الملصقات السيادي — حزم محلية + شبكة ملصقات + تثبيت.
 * @param onPickSticker يُستدعى باختيار ملصق (mediaKey + emoji) لإرساله.
 */
@Composable
fun StickerPicker(
    tokens: TokenStore,
    onPickSticker: (StickerDto) -> Unit
) {
    val scope = rememberCoroutineScope()
    val api = remember { StickerApi(AuthorizedApiClient(tokens)) }

    var installedPacks by remember { mutableStateOf<List<StickerPackDto>>(emptyList()) }
    var availablePacks by remember { mutableStateOf<List<StickerPackDto>>(emptyList()) }
    var selectedPackId by remember { mutableStateOf<String?>(null) }
    var stickers by remember { mutableStateOf<List<StickerDto>>(emptyList()) }
    var loadingPacks by remember { mutableStateOf(true) }
    var loadingStickers by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // تحميل الحزم المثبّتة والمتاحة عند البداية
    LaunchedEffect(Unit) {
        loadingPacks = true
        val installed = api.getInstalledPacks()
        val available = api.getPublishedPacks()
        if (installed is ApiResult.Success) installedPacks = installed.value
        if (available is ApiResult.Success) availablePacks = available.value
        if (installed is ApiResult.Error && available is ApiResult.Error) {
            error = "تعذّر تحميل الملصقات"
        }
        // اختر أول حزمة مثبّتة تلقائياً
        selectedPackId = installedPacks.firstOrNull()?.id ?: availablePacks.firstOrNull()?.id
        loadingPacks = false
    }

    // تحميل ملصقات الحزمة المحددة
    LaunchedEffect(selectedPackId) {
        val packId = selectedPackId ?: return@LaunchedEffect
        loadingStickers = true
        when (val r = api.getStickersInPack(packId)) {
            is ApiResult.Success -> { stickers = r.value; error = null }
            is ApiResult.Error -> error = "تعذّر تحميل ملصقات الحزمة"
        }
        loadingStickers = false
    }

    Column(Modifier.fillMaxWidth().height(280.dp)) {
        // شريط الحزم (مثبّتة + متاحة)
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // الحزم المثبّتة
            items(installedPacks, key = { it.id }) { pack ->
                StickerPackTab(
                    pack = pack,
                    isSelected = pack.id == selectedPackId,
                    isInstalled = true,
                    onClick = { selectedPackId = pack.id }
                )
            }
            // الحزم المتاحة غير المثبّتة (زر تثبيت)
            items(availablePacks.filter { it.id !in installedPacks.map { p -> p.id } }, key = { it.id }) { pack ->
                StickerPackTab(
                    pack = pack,
                    isSelected = pack.id == selectedPackId,
                    isInstalled = false,
                    onClick = { selectedPackId = pack.id },
                    onInstall = {
                        scope.launch { api.install(pack.id); }
                    }
                )
            }
        }

        // شبكة الملصقات
        when {
            loadingPacks || loadingStickers -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AqyalGold, strokeWidth = 2.dp)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            stickers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EmojiEmotions, null, tint = AqyalGold, modifier = Modifier.size(40.dp))
                    Text("لا توجد ملصقات في هذه الحزمة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(stickers, key = { it.id }) { sticker ->
                    StickerCell(sticker) { onPickSticker(sticker) }
                }
            }
        }
    }
}

@Composable
private fun StickerPackTab(
    pack: StickerPackDto,
    isSelected: Boolean,
    isInstalled: Boolean,
    onClick: () -> Unit,
    onInstall: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) YounesEmerald.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(56.dp).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!isInstalled) {
                // أيقونة تثبيت بدلاً من الصورة (الحزمة غير مثبّتة)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Download, null, tint = if (isSelected) YounesEmerald else AqyalCyanGlow, modifier = Modifier.size(18.dp))
                    Text(pack.name.take(6), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            } else {
                Text(pack.name.take(1), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isSelected) YounesEmerald else AqyalGold)
            }
        }
    }
}

@Composable
private fun StickerCell(sticker: StickerDto, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // عرض الإيموجي المرتبط (preview) — الصورة الفعلية تُحمّل عند الإرسال
        Text(
            sticker.emojiTags.firstOrNull() ?: "🎨",
            fontSize = 26.sp
        )
    }
}
