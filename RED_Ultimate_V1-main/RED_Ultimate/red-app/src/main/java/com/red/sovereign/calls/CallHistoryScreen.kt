package com.red.sovereign.calls

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * شاشة سجل المكالمات — Call History Screen
 *
 * تعرض كل المكالمات (Voice, Video, Group, Conference, PSTN) مع:
 * - فلترة حسب النوع (الكل، فائتة، واردة، صادرة، جماعية، بث، فيديو، PSTN)
 * - بحث بالاسم أو الرقم
 * - إحصائيات سريعة (إجمالي المكالمات، معدل النجاح، أكثر جهة اتصال)
 * - تصدير CSV
 * - حذف فردي / مسح الكل
 * - تشفير محلي لبيانات المشاركين (CallLogCipher)
 */
@Composable
fun CallHistoryScreen(
    viewModel: CallHistoryViewModel = viewModel(),
    onCallClick: (CallHistoryItem) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل المكالمات", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.SurfaceDark,
                    titleContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.CallEnd, "رجوع", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterMenu = !showFilterMenu }) {
                        Icon(Icons.Default.FilterList, "تصفية", tint = Color.White)
                    }
                    IconButton(onClick = {
                        viewModel.exportCsvFile(context)?.let { file ->
                            // Open share/save dialog
                        }
                    }) {
                        Icon(Icons.Default.History, "تصدير", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(SovereignColors.SurfaceDarkVariant)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Search, "بحث", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        Text(
                            "بحث بالاسم أو الرقم...",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { showFilterMenu = !showFilterMenu },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.FilterList, "تصفية", tint = AqyalGold)
                }
            }

            // Filter Menu
            DropdownMenu(
                expanded = showFilterMenu,
                onDismissRequest = { showFilterMenu = false }
            ) {
                CallFilterType.values().forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter.label, color = if (viewModel.selectedFilter == filter) AqyalGold else Color.White) },
                        onClick = {
                            viewModel.selectedFilter = filter
                            showFilterMenu = false
                        }
                    )
                }
            }

            // Stats Summary
            CallHistoryStatsCard(stats = viewModel.getStats())

            Spacer(Modifier.height(12.dp))

            // Call List
            when {
                viewModel.loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AqyalGold)
                    }
                }
                viewModel.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Warning, "خطأ", tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(viewModel.error ?: "حدث خطأ", color = Color.Red)
                        Spacer(Modifier.height(8.dp))
                        IconButton(onClick = { viewModel.load() }) {
                            Icon(Icons.Default.Call, "إعادة تحميل", tint = AqyalGold, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                viewModel.filteredCalls.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.History, "لا يوجد سجل", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("لا توجد مكالمات", color = Color.White.copy(alpha = 0.5f))
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.filteredCalls, key = { it.id }) { call ->
                            CallHistoryItemCard(
                                call = call,
                                onClick = { onCallClick(call) },
                                onDelete = { viewModel.deleteCall(call.id) }
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun CallHistoryStatsCard(stats: CallStatsSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text("إحصائيات سريعة", color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem("الإجمالي", stats.totalCalls.toString())
                StatItem("تمت الرد", stats.answeredCalls.toString())
                StatItem("فائتة", stats.missedCalls.toString())
                StatItem("نسبة النجاح", "${stats.successRate}%")
            }
            if (stats.topPeer != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "أكثر جهة اتصال: ${stats.topPeer.first} (${stats.topPeer.second} مكالمة)",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}

@Composable
fun CallHistoryItemCard(
    call: CallHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when {
        call.type.equals("VIDEO", ignoreCase = true) -> Icons.Default.VideoCall
        call.direction.equals("INCOMING", ignoreCase = true) -> Icons.Default.CallReceived
        call.direction.equals("OUTGOING", ignoreCase = true) -> Icons.Default.Call
        else -> Icons.Default.CallEnd
    }
    val iconColor = when {
        call.status.equals("MISSED", ignoreCase = true) || call.status.equals("NO_ANSWER", ignoreCase = true) -> Color.Red
        call.status.equals("ANSWERED", ignoreCase = true) || call.status.equals("COMPLETED", ignoreCase = true) -> Color(0xFF14C79A)
        else -> Color.White.copy(alpha = 0.7f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        call.peerLabel.ifBlank { call.peerId },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${call.direction.lowercase()} • ${call.type.uppercase()} • ${call.route.uppercase()}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    Text(
                        "المدة: ${call.computedDurationSeconds().formatCallDuration()}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatDate(call.startedAt),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    call.status.uppercase(),
                    color = iconColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Delete, "حذف", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

fun formatDate(timestamp: String): String {
    return runCatching {
        val instant = parseCallTimestamp(timestamp) ?: return timestamp
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(instant))
    }.getOrElse { timestamp }
}
