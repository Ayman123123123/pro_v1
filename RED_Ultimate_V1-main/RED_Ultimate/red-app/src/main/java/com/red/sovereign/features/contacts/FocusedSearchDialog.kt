package com.red.sovereign.features.contacts

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * ════════════════════════════════════════════════════════════════════════
 *  FocusedSearchDialog — بحث كامل الشاشة في جهات الاتصال
 *  - فلترة مُنظَّمة: snapshotFlow + debounce 350ms + distinctUntilChanged
 *  - البحث في: الاسم، username، RED ID (حرفان على الأقل)
 *  - ترتيب حسب: Online أولاً، ثم الأحدث
 * ════════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusedSearchDialog(
    initialQuery: String = "",
    contacts: List<PublicRedProfile> = emptyList(),
    isOnline: (String) -> Boolean = { false },
    onDismiss: () -> Unit,
    onResultClick: (PublicRedProfile) -> Unit
) {
    // المفتاح initialQuery: تغيّر الاستعلام الأولي (إعادة فتح ببحث مختلف)
    // يُعيد تهيئة الحقل بدل تجمّده على أول قيمة رُكّبت بها الشاشة.
    // جودة: مفتاح صريح key1 حتى لا يتجمد الحقل عند إعادة الفتح باستعلام مختلف.
    var query by remember(key1 = initialQuery) { mutableStateOf(initialQuery) }
    // المدخل المُستقر بعد debounce — الفلترة تعمل عليه لا على كل حرف.
    var debouncedQuery by remember(key1 = initialQuery) { mutableStateOf(initialQuery) }
    var isFiltering by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(350)
            .distinctUntilChanged()
            .collectLatest { stable ->
                isFiltering = true
                try {
                    debouncedQuery = stable
                } finally {
                    isFiltering = false
                }
            }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(640.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Search header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = YounesEmerald)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("ابحث بالاسم أو RED ID...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YounesEmerald
                        )
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "إغلاق")
                    }
                }
                HorizontalDivider()

                // Results — فلترة حقيقية على المدخل المستقر (حرفان على الأقل)،
                // مرتبة: المتصلون أولاً ثم الأحدث أبجدياً.
                val results = remember(debouncedQuery, contacts) {
                    val q = debouncedQuery.trim()
                    if (q.length < 2) emptyList()
                    else contacts.filter {
                        it.displayName.contains(q, true) || it.username.contains(q, true) || it.redId.contains(q, true)
                    }.sortedWith(compareByDescending<PublicRedProfile> { isOnline(it.redId) }.thenBy { it.displayName })
                }
                if (query.trim().length < 2) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PersonSearch, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("اكتب حرفين على الأقل للبحث", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (isFiltering || (results.isEmpty() && debouncedQuery.trim() != query.trim())) {
                    // انتظار استقرار المدخل بعد debounce — مؤشر بدل وميض «لا نتائج» كاذب.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = YounesEmerald, modifier = Modifier.size(28.dp))
                    }
                } else if (results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("لا توجد نتائج", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results, key = { it.redId }) { person ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onResultClick(person) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        person.displayName.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(person.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "@${person.username} • ${person.redId}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    null,
                                    tint = Color.Gray
                                )
                            }
                            HorizontalDivider(Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
        }
    }
}
