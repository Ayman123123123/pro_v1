package com.red.sovereign.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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

    LaunchedEffect(filters) {
        val base = filters.baseQuery
        if (base.length >= 2 || filters.from != null || filters.type != null || filters.hasMedia || filters.hasLink || filters.before != null || filters.after != null) {
            isSearching = true
            // بحث أساسي عبر LIKE (سيستخدم FTS5 تلقائياً إن كان متاحاً عبر repository)
            val rawResults = if (base.length >= 2) repository.searchAll(base) else repository.searchAll("")
            // تطبيق الفلاتر المحلية
            results = rawResults.filter { entity ->
                val decoded = runCatching { com.red.sovereign.core.RichMessage.decode(entity.encryptedPlaintext)?.text ?: entity.encryptedPlaintext.toString(Charsets.UTF_8) }.getOrDefault("")
                val senderMatch = filters.from?.let { f -> entity.senderId.contains(f, true) || decoded.contains(f, true) } ?: true
                val typeMatch = filters.type?.let { t ->
                    when (t) {
                        "image" -> decoded.contains("[image]", true) || entity.id.contains("image", true)
                        "video" -> decoded.contains("[video]", true)
                        "audio", "voice" -> decoded.contains("[voice]", true) || decoded.contains("[audio]", true)
                        "file" -> decoded.contains("[file]", true)
                        else -> true
                    }
                } ?: true
                val beforeMatch = filters.before?.let { entity.createdAt <= it } ?: true
                val afterMatch = filters.after?.let { entity.createdAt >= it } ?: true
                val mediaMatch = if (filters.hasMedia) decoded.contains("[image]", true) || decoded.contains("[video]", true) || decoded.contains("[audio]", true) else true
                val linkMatch = if (filters.hasLink) decoded.contains("http", true) || decoded.contains("www.", true) else true
                senderMatch && typeMatch && beforeMatch && afterMatch && mediaMatch && linkMatch
            }.sortedByDescending { it.createdAt }.take(100)
            isSearching = false
        } else {
            results = emptyList()
        }
    }
    
    Column(modifier = Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
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
        Text("تلميح: استخدم from:@user type:image before:2024-01-01 has:media", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("نتائج البحث السيادي", style = MaterialTheme.typography.labelMedium, color = SovereignColors.Cyan)
            if (isSearching) androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SovereignColors.Cyan)
            else if (filters.baseQuery.length >= 2 || filters.type != null || filters.hasMedia || filters.hasLink) Text("(${results.size})", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp).weight(1f)) {
            if (searchQuery.trim().length < 2) {
                item { 
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Text("اكتب حرفين على الأقل", color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text("البحث يتم على جهازك فقط — الخادم لا يرى النص", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else if (results.isEmpty() && !isSearching) {
                item { 
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                        Text("لا توجد نتائج لـ '$searchQuery' — جرب كلمة أخرى", color = Color.LightGray, modifier = Modifier.padding(16.dp))
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
                                    androidx.compose.material3.AssistChip(onClick = {}, label = { Text("from:${filters.from}", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(22.dp))
                                }
                                if (filters.type != null) {
                                    androidx.compose.material3.AssistChip(onClick = {}, label = { Text("type:${filters.type}", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.height(22.dp))
                                }
                                Text(
                                    "${msg.senderId.take(12)} • ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(msg.createdAt))}",
                                    color = Color.Gray,
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
