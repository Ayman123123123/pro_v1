package com.red.sovereign.features.communities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ════════════════════════════════════════════════════════════════════════
 *  Communities Screen — مجتمعات وقنوات عامة
 *  - 100% API-driven — كل البيانات من /api/communities
 *  - إنشاء/انضمام/مغادرة فورية
 *  - بحث حي مع debounce
 *  - إدارة المجتمع (للمشرفين فقط)
 * ════════════════════════════════════════════════════════════════════════
 */

private val Muted = Color(0xFF7A8590)
private val Surface = Color(0xFF161C24)

data class CommunitiesUiState(
    val loading: Boolean = false,
    val communities: List<Community> = emptyList(),
    val error: String? = null,
    val query: String = "",
    val showCreate: Boolean = false
)

class CommunitiesViewModel(private val api: CommunitiesApi) : ViewModel() {
    private val _state = MutableStateFlow(CommunitiesUiState())
    val state: StateFlow<CommunitiesUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = it.communities.isEmpty(), error = null) }
        viewModelScope.launch {
            loadList(_state.value.query)
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        // Debounce 300ms
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadList(q)
        }
    }

    private suspend fun loadList(query: String) {
        when (val result = api.list(query.takeIf { it.isNotBlank() })) {
            is ApiResult.Success -> _state.update {
                it.copy(loading = false, communities = result.value, error = null)
            }
            is ApiResult.Error -> _state.update {
                it.copy(loading = false, error = result.message)
            }
        }
    }

    fun join(community: Community) = viewModelScope.launch {
        when (val result = api.join(community.id)) {
            is ApiResult.Success -> {
                _state.update { current ->
                    current.copy(communities = current.communities.map {
                        if (it.id == community.id) result.value else it
                    })
                }
            }
            is ApiResult.Error -> _state.update { it.copy(error = result.message) }
        }
    }

    fun leave(community: Community) = viewModelScope.launch {
        when (val result = api.leave(community.id)) {
            is ApiResult.Success -> refresh()
            is ApiResult.Error -> _state.update { it.copy(error = result.message) }
        }
    }

    fun delete(community: Community) = viewModelScope.launch {
        when (api.delete(community.id)) {
            is ApiResult.Success -> refresh()
            is ApiResult.Error -> _state.update { it.copy(error = "فشل الحذف") }
        }
    }

    fun showCreate() = _state.update { it.copy(showCreate = true) }
    fun hideCreate() = _state.update { it.copy(showCreate = false) }

    fun create(
        name: String,
        description: String,
        category: String,
        isPublic: Boolean,
        onSuccess: () -> Unit
    ) {
        if (name.length < 2) {
            _state.update { it.copy(error = "الاسم يجب أن يكون حرفين على الأقل") }
            return
        }
        viewModelScope.launch {
            val body = CreateCommunityBody(
                name = name.trim(),
                description = description.takeIf { it.isNotBlank() }?.trim(),
                category = category,
                isPublic = isPublic
            )
            when (val result = api.create(body)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(showCreate = false, communities = listOf(result.value) + it.communities) }
                    onSuccess()
                }
                is ApiResult.Error -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesScreen(
    tokens: TokenStore,
    onBack: () -> Unit,
    onOpenCommunity: (communityId: String, communityName: String) -> Unit = { _, _ -> }
) {
    val api = remember(tokens) { CommunitiesApi(AuthorizedApiClient(tokens)) }
    val vm: CommunitiesViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CommunitiesViewModel(api) as T
        }
    )
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("المجتمعات والقنوات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = vm::showCreate) {
                        Icon(Icons.Default.Add, "إنشاء مجتمع", tint = YounesEmerald)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Subtitle
            Text(
                "انضم لمجتمعات عامة وتابع قنوات — ليست مشفرة، بل عامة بإدارة",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Search bar
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("بحث المجتمعات...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    { IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Clear, null) } }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Error banner
            state.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(err, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        IconButton(onClick = vm::clearError) { Icon(Icons.Default.Close, null) }
                    }
                }
            }

            when {
                state.loading && state.communities.isEmpty() -> CenteredLoader("جاري التحميل...")
                state.communities.isEmpty() -> EmptyState(
                    title = if (state.query.isNotBlank()) "لا توجد نتائج" else "لا توجد مجتمعات بعد",
                    subtitle = if (state.query.isBlank()) "كن أول من ينشئ مجتمعاً!" else "جرّب كلمة أخرى"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.communities, key = { it.id }) { community ->
                        CommunityCard(
                            community = community,
                            onOpen = { onOpenCommunity(community.id, community.name) },
                            onJoin = { vm.join(community) },
                            onLeave = { vm.leave(community) },
                            onDelete = { vm.delete(community) }
                        )
                    }
                }
            }
        }
    }

    if (state.showCreate) {
        CreateCommunityDialog(
            onDismiss = vm::hideCreate,
            onSubmit = { name, desc, cat, isPublic -> vm.create(name, desc, cat, isPublic) {} }
        )
    }
}

@Composable
private fun CommunityCard(
    community: Community,
    onOpen: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val avatarColor = parseColorOrDefault(community.avatarColor)
    val isAdmin = community.myRole == "ADMIN"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = community.name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(community.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (!community.isPublic) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            null,
                            tint = Muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                community.description?.let { desc ->
                    Text(
                        desc,
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Group, null, tint = YounesEmerald, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${community.memberCount} عضو",
                        color = YounesEmerald,
                        fontSize = 11.sp
                    )
                    if (community.myRole != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "· ${roleLabel(community.myRole)}",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            // Action button
            when {
                isAdmin -> {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("حذف المجتمع") },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
                community.isJoined -> OutlinedButton(onClick = onJoin, enabled = false) {
                    Text("منضم", fontSize = 12.sp)
                }
                else -> Button(onClick = onJoin, colors = ButtonDefaults.buttonColors(containerColor = YounesEmerald)) {
                    Text("انضم", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCommunityDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, description: String, category: String, isPublic: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("GENERAL") }
    var isPublic by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء مجتمع جديد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المجتمع") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                Spacer(Modifier.height(12.dp))
                Text("التصنيف", fontSize = 13.sp)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("GENERAL", "TECH", "BUSINESS", "EDUCATION", "CULTURE").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(categoryLabel(cat), fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPublic) "عام (الانضمام تلقائي)" else "خاص (يتطلب موافقة)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name, description, category, isPublic) },
                enabled = name.trim().length >= 2
            ) { Text("إنشاء", color = YounesEmerald, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun CenteredLoader(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = YounesEmerald)
            Spacer(Modifier.height(12.dp))
            Text(text, color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Public, null, tint = Muted, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

private fun categoryLabel(c: String): String = when (c) {
    "GENERAL" -> "عام"
    "TECH" -> "تقنية"
    "BUSINESS" -> "أعمال"
    "EDUCATION" -> "تعليم"
    "CULTURE" -> "ثقافة"
    else -> c
}

private fun roleLabel(r: String): String = when (r) {
    "ADMIN" -> "مشرف"
    "MODERATOR" -> "وسيط"
    "MEMBER" -> "عضو"
    else -> r
}

private fun parseColorOrDefault(hex: String?): Color = try {
    if (hex.isNullOrBlank()) Color(0xFF45B7D1)
    else Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color(0xFF45B7D1)
}
