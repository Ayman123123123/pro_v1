package com.red.sovereign.features.communities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald

data class Community(val id: String, val name: String, val description: String, val members: Int, val isJoined: Boolean)

@Composable
fun CommunitiesScreen(onBack: () -> Unit) {
    var communities by remember { mutableStateOf(listOf(
        Community("1", "يمنيون", "مجتمع اليمن العام", 1240, false),
        Community("2", "تقنية", "أخبار التقنية والذكاء الاصطناعي", 890, true),
        Community("3", "ريادة أعمال", "مجتمع رواد الأعمال اليمنيين", 560, false)
    )) }
    var query by remember { mutableStateOf("") }
    val filtered = communities.filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("المجتمعات والقنوات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onBack) { Text("رجوع") }
        }
        Text("انضم لمجتمعات عامة وتابع قنوات — ليست مشفرة، بل عامة بإدارة", color = Color.Gray, fontSize = 12.sp)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 12.dp), placeholder = { Text("بحث المجتمعات...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ /* TODO: Create community */ }, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text(" إنشاء مجتمع") }
            OutlinedButton({ query = "" }, Modifier.weight(1f)) { Text("استكشاف") }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { community ->
                Card(Modifier.fillMaxWidth().clickable {}, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, null, tint = AqyalGold, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(community.name, fontWeight = FontWeight.Bold)
                            Text(community.description, color = Color.Gray, fontSize = 12.sp, maxLines = 2)
                            Text("${community.members} عضو", color = YounesEmerald, fontSize = 11.sp)
                        }
                        if (community.isJoined) AssistChip(onClick = {}, label = { Text("منضم") }, enabled = false)
                        else Button(onClick = { communities = communities.map { if (it.id == community.id) it.copy(isJoined = true, members = it.members + 1) else it } }) { Text("انضم") }
                    }
                }
            }
        }
    }
}
