package com.red.sovereign.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.red.sovereign.ui.theme.AqyalGold

/**
 * توحيد عام (2026-09-10): صورة رمزية واحدة بدل 3 نسخ مكررة
 * (RedDashboard:851 = FeedScreens:563 = MoreScreen:113).
 * تدعم Coil لعرض صورة URL/ملف، وتسقط لنص الحرف الأول عند غياب الصورة.
 *
 * ترقية 2026-09-10 (Stories/Feed):
 * - Coil عبر ImageRequest صريح مع مفاتيح كاش مستقرة + crossfade.
 * - سقوط أنيق لحرف أول عند فشل التحميل (onError) لا فراغ أسود.
 * - GroupAvatar موحد هنا بدل 3 نسخ (RedDashboard:901 / FeedScreens:843 /
 *   SovereignGroupSystem:286) مع إصلاح LaunchedEffect(avatarUrl) بلا إلغاء.
 */

/** مفتاح كاش مستقر لأي موديل (URL/ملف/objectKey) — يمنع إعادة التحميل عند إعادة التركيب. */
internal fun avatarCacheKey(model: Any?): String? = when (model) {
    null -> null
    is String -> model.ifBlank { null }
    is android.net.Uri -> model.toString().ifBlank { null }
    is java.io.File -> "${model.absolutePath}:${model.lastModified()}"
    else -> model.toString().ifBlank { null }
}

@Composable
fun SovereignAvatar(
    text: String,
    modifier: Modifier = Modifier,
    imageModel: Any? = null,
    size: Dp = 42.dp,
    containerColor: Color = AqyalGold,
    contentColor: Color = Color.Black,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val cacheKey = remember(imageModel) { avatarCacheKey(imageModel) }
    // ImageRequest مستقر عبر remember(model): بلا remember كانت كل إعادة تركيب
    // تبني Builder جديدًا فيُبطل Coil الطلب الجاري ويعيد التحميل (وميض).
    val request = remember(context, imageModel, cacheKey) {
        if (imageModel == null) null else ImageRequest.Builder(context)
            .data(imageModel)
            .apply {
                if (cacheKey != null) {
                    memoryCacheKey(cacheKey)
                    diskCacheKey(cacheKey)
                }
            }
            .crossfade(true)
            .build()
    }
    if (request == null) {
        AvatarFallback(text, modifier, size, containerColor, contentColor)
        return
    }
    var failed by remember(imageModel) { mutableStateOf(false) }
    if (failed) {
        AvatarFallback(text, modifier, size, containerColor, contentColor)
        return
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription ?: text,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = modifier.size(size).clip(CircleShape)
    )
}

@Composable
private fun AvatarFallback(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    containerColor: Color = AqyalGold,
    contentColor: Color = Color.Black
) {
    Box(
        modifier.size(size).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(1).ifBlank { "ي" },
            color = contentColor,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * بديل MaterialTheme للمواضع التي كانت تستخدم primary/onPrimary (FeedScreens).
 */
@Composable
fun SovereignAvatarThemed(
    text: String,
    modifier: Modifier = Modifier,
    imageModel: Any? = null,
    size: Dp = 42.dp
) {
    SovereignAvatar(
        text = text,
        modifier = modifier,
        imageModel = imageModel,
        size = size,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
}

/**
 * ── GroupAvatar الموحد (2026-09-10) ──
 *
 * يدمج النسخ الثلاث (RedDashboard:901 / FeedScreens:843 / SovereignGroupSystem:286).
 *
 * إصلاح LaunchedEffect(avatarUrl) بلا إلغاء:
 * كان المفتاح `avatarUrl` وحده (nullable String). عيبان:
 * 1) مجموعتان بلا صورة تشتركان مفتاح null — إعادة استخدام التركيبة عند
 *    التمرير لا تعيد إطلاق التحميل للمجموعة الجديدة (صورة عالقة/غائبة).
 * 2) تبدّل المجموعة مع ثبات الرابط لا يعيد التحميل؛ وتبدّل الرابط لا يلغي
 *    طلب المجموعة السابقة (سباق: صورة قديمة تكتب فوق الجديدة).
 * المفتاح الآن (group.id, group.avatarUrl) فيُلغى السابق تلقائيًا عند أي تبدّل.
 *
 * مسارا العرض:
 * - http(s) مباشر → Coil فورًا (قرص/ذاكرة + crossfade).
 * - objectKey مشفّر → GroupViewModel.loadAvatar (مصدّق عبر MediaApi) مع كاش
 *   ImageBitmap في groups.avatars، والسقوط لحرف أول.
 */
@Composable
fun SovereignGroupAvatar(
    group: com.red.sovereign.groups.Group,
    groups: com.red.sovereign.groups.GroupViewModel,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    themed: Boolean = true
) {
    val url = group.avatarUrl
    // مفتاح مركب: إلغاء تلقائي للطلب السابق عند تبدّل المجموعة أو رابطها.
    LaunchedEffect(group.id, url) { groups.loadAvatar(group) }
    val cached = groups.avatars[group.id]
    when {
        cached != null -> Image(
            bitmap = cached,
            contentDescription = group.name,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape)
        )
        url?.startsWith("http://") == true || url?.startsWith("https://") == true -> {
            if (themed) SovereignAvatarThemed(group.name.take(1), modifier, url, size)
            else SovereignAvatar(group.name.take(1), modifier, url, size)
        }
        else -> {
            if (themed) SovereignAvatarThemed(group.name.take(1), modifier, null, size)
            else SovereignAvatar(group.name.take(1), modifier, null, size)
        }
    }
}

/**
 * أفاتار حالة (Story): حلقة ملوّنة — ذهبية لغير المشاهَدة، رمادية للمشاهَدة —
 * بدل الدائرة الصماء. يعيد استخدام SovereignAvatar/Coil في الداخل.
 */
@Composable
fun SovereignStoryAvatar(
    label: String,
    imageModel: Any? = null,
    viewed: Boolean = false,
    own: Boolean = false,
    size: Dp = 62.dp,
    modifier: Modifier = Modifier
) {
    val ring = when {
        own -> MaterialTheme.colorScheme.primary
        viewed -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        else -> AqyalGold
    }
    Box(
        modifier.size(size).clip(CircleShape).border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        SovereignAvatarThemed(
            text = label.take(1).ifBlank { "ي" },
            imageModel = imageModel,
            size = size - 6.dp
        )
    }
}
