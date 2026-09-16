package com.red.sovereign.features.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.red.sovereign.ui.MESSAGE_SEARCH_DEBOUNCE_MS
import com.red.sovereign.ui.MESSAGE_SEARCH_MIN_LENGTH
import com.red.sovereign.ui.theme.SovereignColors

/**
 * 🔍 YOUNES Global Search — البحث السيادي الشامل المتقدم
 * يدعم فلاتر: from:@user type:image|video|audio|file|voice before:2024-01-01 after:2024-01-01 has:media|link
 * مع تمييز snippet وإبراز الكلمة المطابقة وترتيب bm25 (عبر FTS5)
 */
data class AdvancedFilters(
    val from: String? = null,
    val type: String? = null,
    val before: Long? = null,
    val after: Long? = null,
    val hasMedia: Boolean = false,
    val hasLink: Boolean = false,
    val baseQuery: String = ""
)

fun parseAdvancedQuery(raw: String): AdvancedFilters {
    var q = raw
    var from: String? = null
    var type: String? = null
    var before: Long? = null
    var after: Long? = null
    var hasMedia = false
    var hasLink = false
    fun extract(prefix: String, regex: Regex, handler: (String)->Unit) {
        regex.find(q)?.let { m ->
            handler(m.groupValues[1])
            q = q.replace(m.value, " ").trim()
        }
    }
    extract("from", Regex("""from:\s*([^\s]+)""")) { from = it.removePrefix("@") }
    extract("type", Regex("""type:\s*([^\s]+)""")) { type = it.lowercase() }
    extract("before", Regex("""before:\s*([0-9]{4}-[0-9]{2}-[0-9]{2})""")) {
        runCatching { before = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time }
    }
    extract("after", Regex("""after:\s*([0-9]{4}-[0-9]{2}-[0-9]{2})""")) {
        runCatching { after = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time }
    }
    if (Regex("""has:\s*media""").containsMatchIn(q)) { hasMedia = true; q = q.replace(Regex("""has:\s*media"""), " ") }
    if (Regex("""has:\s*link""").containsMatchIn(q)) { hasLink = true; q = q.replace(Regex("""has:\s*link"""), " ") }
    return AdvancedFilters(from, type, before, after, hasMedia, hasLink, q.trim().replace(Regex("\\s+"), " "))
}

/**
 * تطبيع عربي بسيط للبحث (P0-C 2026-09-12): إزالة تشكيل + توحيد أ إ آ ٱ ⇒ ا
 * + ة ⇒ ه + ى ⇒ ي — يُستدعى قبل MATCH (FTS) وقبل LIKE معًا حتى يتطابق
 * تمثيل الاستعلام مع تمثيل الفهرس (نفس قواعد `FtsSearchManager.normalizeArabic`).
 *
 * نسخة محلية مقصودة (لا تفويض مباشر فقط) حتى تبقى سياسة التطبيع مرئية
 * عند نقطة الاستعلام وتُطبَّق على الطرفين صراحةً.
 */
private fun normalizeArabicQuery(raw: String): String {
    var s = raw.replace(Regex("[\u064B-\u0652\u0670\u0640]"), "")
    s = s.replace(Regex("[\u0622\u0623\u0625\u0671]"), "\u0627")
    s = s.replace('\u0649', '\u064A').replace('\u0629', '\u0647')
    return s
}

/** هل الكيان وسيط؟ — المصدر الوحيد: عمود `messageType` (مفهرس). */
private fun isMediaType(messageType: String): Boolean =
    messageType.equals("IMAGE", true) || messageType.equals("VIDEO", true) ||
        messageType.equals("AUDIO", true) || messageType.equals("VOICE", true) ||
        messageType.equals("FILE", true) || messageType.equals("STICKER", true)

@Composable
fun RedGlobalSearch(
    onBack: () -> Unit = {},
    onOpenConversation: (senderRedId: String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedFrom by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { com.red.sovereign.core.database.LocalRepository(context) }
    var results by remember { mutableStateOf(emptyList<com.red.sovereign.core.database.LocalHistoryEntity>()) }
    var isSearching by remember { mutableStateOf(false) }

    val filters = remember(searchQuery, selectedType, selectedFrom) {
        val parsed = parseAdvancedQuery(searchQuery)
        parsed.copy(type = selectedType ?: parsed.type, from = selectedFrom ?: parsed.from)
    }

    // بحث مُوحَّد: snapshotFlow + debounce مشترك + distinctUntilChanged + حد أدنى موحد.
    // السياسة واحدة مع حوار «البحث داخل المحادثة» (MESSAGE_SEARCH_* في DashboardSearch):
    // collectLatest تُلغي البحث السابق تلقائياً عند كل حرف جديد (cancelPrevious) —
    // لا استعلام Room ثقيل مع كل ضغطة ولا take(100) إلا على آخر مدخل مستقر.
    // الاستعلام يُطبَّع عربيًا (FtsSearchManager.normalizeArabic) قبل LIKE حتى
    // تطابق «ابراهيم» «إبراهيم» — نفس التطبيع المستعمل في فهرس FTS5.
    LaunchedEffect(repository) {
        snapshotFlow { Triple(searchQuery, selectedType, selectedFrom) }
            .debounce(MESSAGE_SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .map { (raw, type, from) ->
                val parsed = parseAdvancedQuery(raw)
                parsed.copy(type = type ?: parsed.type, from = from ?: parsed.from)
            }
            .filter { f ->
                f.baseQuery.length >= MESSAGE_SEARCH_MIN_LENGTH || f.from != null || f.type != null ||
                    f.hasMedia || f.hasLink || f.before != null || f.after != null
            }
            .collectLatest { f ->
                isSearching = true
                try {
                    val base = f.baseQuery
                    // تطبيع عربي قبل الطرفين (P0-C): MATCH وLIKE معًا — «ابراهيم»
                    // تطابق «إبراهيم»، و«مكتبه» تطابق «مكتبة».
                    val normalized = if (base.length >= MESSAGE_SEARCH_MIN_LENGTH) normalizeArabicQuery(base) else base
                    // بحث أساسي عبر LIKE الموحد (repository.searchAll: تهريب + حد 100).
                    val rawResults = if (base.length >= MESSAGE_SEARCH_MIN_LENGTH) repository.searchAll(normalized) else repository.searchAll("")
                    // FTS أولًا عند توفر الفهرس: نتائج مرتبة بـ bm25 تُدمج فوق LIKE.
                    // يُمرَّر المُطبَّع أيضًا (searchUnified يُطبّع داخليًا مجددًا — idempotent).
                    val ftsIds = if (base.length >= MESSAGE_SEARCH_MIN_LENGTH) {
                        runCatching { repository.searchUnified(context, normalized, 50).map { it.messageId }.toSet() }.getOrDefault(emptySet())
                    } else emptySet()
                    val ordered = if (ftsIds.isNotEmpty()) {
                        rawResults.sortedWith(compareBy({ it.id !in ftsIds }, { -it.createdAt }))
                    } else rawResults
                    // تطبيق الفلاتر المحلية (على الترتيب المدمج FTS أولًا)
                    // P0-C: الفلاتر من عمود `messageType` (مفهرس) — لا مسح نصي
                    // contains("[image]") — كان يفشل مع RICH_TEXT/protobuf والمانيفست JSON.
                    results = ordered.filter { entity ->
                        val decoded = runCatching { com.red.sovereign.core.RichMessage.decode(entity.encryptedPlaintext)?.text ?: entity.encryptedPlaintext.toString(Charsets.UTF_8) }.getOrDefault("")
                        val senderMatch = f.from?.let { from -> entity.senderId.contains(from, true) || decoded.contains(from, true) } ?: true
                        val typeMatch = f.type?.let { t ->
                            when (t) {
                                "image" -> entity.messageType.equals("IMAGE", true)
                                "video" -> entity.messageType.equals("VIDEO", true)
                                "audio", "voice" -> entity.messageType.equals("AUDIO", true) || entity.messageType.equals("VOICE", true)
                                "file" -> entity.messageType.equals("FILE", true)
                                else -> true
                            }
                        } ?: true
                        val beforeMatch = f.before?.let { entity.createdAt <= it } ?: true
                        val afterMatch = f.after?.let { entity.createdAt >= it } ?: true
                        val mediaMatch = if (f.hasMedia) isMediaType(entity.messageType) else true
                        val linkMatch = if (f.hasLink) decoded.contains("http", true) || decoded.contains("www.", true) else true
                        senderMatch && typeMatch && beforeMatch && afterMatch && mediaMatch && linkMatch
                    }.sortedByDescending { it.createdAt }.take(100)
                } finally {
                    isSearching = false
                }
            }
    }

    // تصفير فوري عند مسح المدخل دون انتظار الـ debounce.
    LaunchedEffect(searchQuery, selectedType, selectedFrom) {
        if (parseAdvancedQuery(searchQuery).baseQuery.length < 2 && selectedType == null && selectedFrom == null) {
            results = emptyList()
        }
    }
    
    Column(modifier = Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White) }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("بحث مشفر في رسائلك — FTS5 محلي") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = SovereignColors.Cyan) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SovereignColors.Cyan, unfocusedBorderColor = Color.Gray),
                singleLine = true
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // فلاتر متقدمة — chips تفاعلية
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                FilterChip(selected = selectedType == "image", onClick = { selectedType = if (selectedType == "image") null else "image" }, label = { Text("صور", style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Text("🖼️") })
            }
            item {
                FilterChip(selected = selectedType == "video", onClick = { selectedType = if (selectedType == "video") null else "video" }, label = { Text("فيديو", style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Text("🎥") })
            }
            item {
                FilterChip(selected = selectedType == "voice", onClick = { selectedType = if (selectedType == "voice") null else "voice" }, label = { Text("صوت", style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Text("🎙️") })
            }
            item {
                FilterChip(selected = selectedType == "file", onClick = { selectedType = if (selectedType == "file") null else "file" }, label = { Text("ملفات", style = MaterialTheme.typography.labelSmall) })
            }
            item {
                FilterChip(selected = filters.hasMedia, onClick = { searchQuery = if (filters.hasMedia) searchQuery.replace(Regex("""has:\s*media"""), "").trim() else (searchQuery + " has:media").trim() }, label = { Text("وسائط", style = MaterialTheme.typography.labelSmall) })
            }
            item {
                FilterChip(selected = filters.hasLink, onClick = { searchQuery = if (filters.hasLink) searchQuery.replace(Regex("""has:\s*link"""), "").trim() else (searchQuery + " has:link").trim() }, label = { Text("روابط", style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Text("🔗") })
            }
            item {
                FilterChip(selected = false, onClick = { searchQuery = (searchQuery + " before:${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}").trim() }, label = { Text("قبل اليوم", style = MaterialTheme.typography.labelSmall) })
            }
            if (selectedType != null || selectedFrom != null || filters.hasMedia || filters.hasLink) {
                item {
                    AssistChip(onClick = { selectedType = null; selectedFrom = null; searchQuery = filters.baseQuery }, label = { Text("مسح الفلاتر") })
                }
            }
        }
        Text("تلميح: استخدم from:@user type:image before:2024-01-01 has:media", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("نتائج البحث السيادي", style = MaterialTheme.typography.labelMedium, color = SovereignColors.Cyan)
            if (isSearching) androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SovereignColors.Cyan)
            else if (filters.baseQuery.length >= 2 || filters.type != null || filters.hasMedia || filters.hasLink) Text("(${results.size})", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp).weight(1f)) {
            if (searchQuery.trim().length < 2) {
                item { 
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Text("اكتب حرفين على الأقل", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text("البحث يتم على جهازك فقط — الخادم لا يرى النص", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else if (results.isEmpty() && !isSearching) {
                item { 
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                        Text("لا توجد نتائج لـ '$searchQuery' — جرب كلمة أخرى", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                }
            } else {
                items(results, key = { it.id }) { msg ->
                    val rawText = com.red.sovereign.core.RichMessage.decode(msg.encryptedPlaintext)?.text
                        ?: msg.encryptedPlaintext.toString(Charsets.UTF_8)
                    val base = filters.baseQuery
                    val snippet = if (rawText.length > 120) rawText.take(120) + "…" else rawText
                    val annotated = androidx.compose.ui.text.buildAnnotatedString {
                        if (base.length >= 2 && snippet.contains(base, true)) {
                            val idx = snippet.indexOf(base, ignoreCase = true)
                            append(snippet.substring(0, idx))
                            withStyle(androidx.compose.ui.text.SpanStyle(background = SovereignColors.Cyan.copy(alpha = 0.3f), color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                                append(snippet.substring(idx, idx + base.length))
                            }
                            append(snippet.substring(idx + base.length))
                        } else {
                            append(snippet.take(80))
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenConversation(msg.senderId) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                annotated,
                                maxLines = 2,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (filters.from != null) {
                                    androidx.compose.material3.AssistChip(onClick = { selectedFrom = null; searchQuery = searchQuery.replace(Regex("""from:\s*[^\s]+"""), "").trim().replace(Regex("\\s+"), " ") }, label = { Text("from:${filters.from}", style = MaterialTheme.typography.labelSmall) }, trailingIcon = { Text("✕", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(22.dp))
                                }
                                if (filters.type != null) {
                                    androidx.compose.material3.AssistChip(onClick = { selectedType = null; searchQuery = searchQuery.replace(Regex("""type:\s*[^\s]+"""), "").trim().replace(Regex("\\s+"), " ") }, label = { Text("type:${filters.type}", style = MaterialTheme.typography.labelSmall) }, trailingIcon = { Text("✕", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(22.dp))
                                }
                                Text(
                                    "${msg.senderId.take(12)} • ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(msg.createdAt))}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
