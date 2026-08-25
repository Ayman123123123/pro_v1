package com.red.sovereign.features.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.media.AttachmentViewModel
import kotlinx.serialization.json.Json
import java.io.File

enum class GallerySortOrder(val label: String) {
    DATE_DESC("الأحدث أولاً"), DATE_ASC("الأقدم أولاً"),
    SIZE_DESC("الأكبر حجماً"), SIZE_ASC("الأصغر حجماً"),
    NAME_ASC("الاسم (أ-ي)"), NAME_DESC("الاسم (ي-أ)")
}

@Composable
fun MediaGalleryScreen(
    title: String,
    messages: List<DecryptedMessage>,
    attachments: AttachmentViewModel,
    onDismiss: () -> Unit,
    onOpenFile: (File, String, String) -> Unit,
    onSaveFile: ((File) -> Unit)? = null,
    onDeleteMessage: ((DecryptedMessage) -> Unit)? = null
) {
    val json = remember { Json { ignoreUnknownKeys = true } }

    var filter by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(GallerySortOrder.DATE_DESC) }
    var gridView by remember { mutableStateOf(true) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showSortDialog by remember { mutableStateOf(false) }
    var pendingManifestForDownload by remember { mutableStateOf<Pair<com.red.sovereign.media.AttachmentManifest, String>?>(null) }

    val all = remember(messages) {
        messages.filter { it.type in setOf("IMAGE", "VIDEO", "FILE", "AUDIO") }
    }
    val filtered = remember(all, filter, query, sort) {
        var list = when (filter) {
            1 -> all.filter { it.type == "IMAGE" }
            2 -> all.filter { it.type == "VIDEO" }
            3 -> all.filter { it.type == "FILE" }
            4 -> all.filter { it.type == "AUDIO" }
            else -> all
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { m -> parseManifest(m, json)?.name?.lowercase()?.contains(q) == true }
        }
        val withMeta = list.map { it to parseManifest(it, json) }
        when (sort) {
            GallerySortOrder.DATE_DESC -> withMeta.sortedByDescending { it.first.timestamp }
            GallerySortOrder.DATE_ASC -> withMeta.sortedBy { it.first.timestamp }
            GallerySortOrder.SIZE_DESC -> withMeta.sortedByDescending { it.second?.size ?: 0 }
            GallerySortOrder.SIZE_ASC -> withMeta.sortedBy { it.second?.size ?: Long.MAX_VALUE }
            GallerySortOrder.NAME_ASC -> withMeta.sortedBy { (it.second?.name ?: "").lowercase() }
            GallerySortOrder.NAME_DESC -> withMeta.sortedByDescending { (it.second?.name ?: "").lowercase() }
        }.map { it.first }
    }

    fun requestDownload(manifest: com.red.sovereign.media.AttachmentManifest, item: DecryptedMessage) {
        pendingManifestForDownload = manifest to item.id
        attachments.download(item.id, kotlinx.serialization.json.Json.encodeToString(com.red.sovereign.media.AttachmentManifest.serializer(), manifest))
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    IconButton({ selectionMode = false; selectedIds = emptySet() }) { Icon(Icons.Default.Close, "إلغاء") }
                    Text("${selectedIds.size} محدد", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(enabled = selectedIds.isNotEmpty(), onClick = {
                        filtered.filter { it.id in selectedIds }.forEach { m -> onDeleteMessage?.invoke(m) }
                        selectionMode = false; selectedIds = emptySet()
                    }) { Icon(Icons.Default.Delete, "حذف المحدد", tint = Color(0xFFD32F2F)) }
                } else {
                    IconButton(onDismiss) { Icon(Icons.Default.Close, "إغلاق") }
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${filtered.size} عنصر", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton({ selectionMode = true }) { Icon(Icons.Default.CheckCircle, "تحديد") }
                    IconButton({ showSortDialog = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "ترتيب") }
                    IconButton({ gridView = !gridView }) { Icon(if (gridView) Icons.Default.ViewList else Icons.Default.GridView, "تبديل العرض") }
                }
            }
        }

        if (!selectionMode) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("بحث بالاسم…", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(start = 8.dp).height(52.dp),
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                    }
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("الكل" to 0, "صور" to 1, "فيديو" to 2, "ملفات" to 3, "صوت" to 4).forEach { (label, idx) ->
                    FilterChip(selected = filter == idx, onClick = { filter = idx }, label = { Text(label, fontSize = 11.sp) })
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    Text(if (query.isBlank()) "لا توجد وسائط مشتركة بعد" else "لا نتائج للبحث", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (gridView && filter in listOf(0, 1, 2)) {
            val gridItemsList = filtered.filter { it.type in setOf("IMAGE", "VIDEO") }
            val otherItems = filtered.filter { it.type in setOf("FILE", "AUDIO") }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                gridItems(gridItemsList, key = { it.id }) { item ->
                    Box(Modifier.aspectRatioSquare()) {
                        MediaGalleryItem(
                            item = item,
                            attachments = attachments,
                            json = json,
                            isBusy = false,
                            onOpen = { f, mf -> onOpenFile(f, mf.name, mf.mimeType) },
                            onRequestDownload = { mf -> requestDownload(mf, item) },
                            onLongPress = { m ->
                                selectionMode = true
                                selectedIds = selectedIds + m.id
                            }
                        )
                    }
                }
                if (filter == 0) {
                    gridItems(otherItems, key = { it.id }) { item ->
                        Box(Modifier.height(64.dp)) {
                            MediaListItem(
                                item = item,
                                attachments = attachments,
                                json = json,
                                selected = item.id in selectedIds,
                                onOpen = { f, mf -> onOpenFile(f, mf.name, mf.mimeType) },
                                onRequestDownload = { mf -> requestDownload(mf, item) },
                                onToggleSelect = {}
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) {
                listItems(filtered, key = { it.id }) { item ->
                    MediaListItem(
                        item = item,
                        attachments = attachments,
                        json = json,
                        selected = item.id in selectedIds,
                        onOpen = { f, mf -> onOpenFile(f, mf.name, mf.mimeType) },
                        onRequestDownload = { mf -> requestDownload(mf, item) },
                        onToggleSelect = {}
                    )
                }
            }
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("ترتيب حسب", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    GallerySortOrder.values().forEach { order ->
                        Text(
                            order.label,
                            fontSize = 14.sp,
                            fontWeight = if (sort == order) FontWeight.Bold else FontWeight.Normal,
                            color = if (sort == order) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable { sort = order; showSortDialog = false }.padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ showSortDialog = false }) { Text("إلغاء") } }
        )
    }
}

private fun Modifier.aspectRatioSquare(): Modifier =
    this.then(Modifier.fillMaxWidth().height(110.dp))
