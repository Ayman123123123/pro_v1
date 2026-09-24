package com.red.sovereign.features.communities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val showCreate: Boolean = false,
    val updatingCommunityId: String? = null
)

/** جسم PUT /api/communities/{id} — يطابق UpdateCommunityRequest في الباكند (ADMIN فقط). */
@Serializable
data class UpdateCommunityBody(
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val tags: List<String>? = null,
    val isPublic: Boolean? = null,
    val rules: String? = null,
    val avatarColor: String? = null
)

private val CommunityJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

/**
 * PUT /api/communities/{id} — معرّف هنا (لا في CommunitiesApi.kt خارج النطاق)
 * لأن CommunitiesApi يُخفي الـ client (private) ولا يقبل handler خارجي؛
 * المنادي يمرر tokens الجلسة نفسها فيُبنى AuthorizedApiClient مطابق.
 */
suspend fun putCommunityUpdate(
    tokens: TokenStore,
    id: String,
    body: UpdateCommunityBody
): ApiResult<Community> {
    val payload = CommunityJson.encodeToString(body)
    val raw = when (val r = AuthorizedApiClient(tokens).request("PUT", "/api/communities/$id", payload)) {
        is ApiResult.Success -> r.value
        is ApiResult.Error -> return r
    }
    return try {
        ApiResult.Success(200, CommunityJson.decodeFromString<Community>(raw))
    } catch (e: Exception) {
        ApiResult.Error(500, e.message.orEmpty())
    }
}

/** ألوان الأفاتار المقترحة — نفس لوحة pickRandomColor في الباكند. */
private val CommunityAvatarColors = listOf(
    "#FF6B6B", "#4ECDC4", "#45B7D1", "#FFA07A",
    "#98D8C8", "#FFD93D", "#6BCB77", "#C780FA"
)

/** "a, b, c" ←→ ["a","b","c"] — تُطبَّع صغيرة كما يفعل الباكند. */
private fun parseTagsInput(raw: String): List<String> =
    raw.split(',', '،', ' ').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().take(10)

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
        tags: List<String> = emptyList(),
        rules: String? = null,
        avatarColor: String? = null,
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
                tags = tags.takeIf { it.isNotEmpty() },
                isPublic = isPublic,
                rules = rules?.trim()?.takeIf { it.isNotEmpty() },
                avatarColor = avatarColor?.trim()?.takeIf { it.isNotEmpty() }
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

    /** تحديث المجتمع عبر PUT — doPut تُحقن من الشاشة (تملك tokens الجلسة). */
    fun update(
        community: Community,
        name: String?,
        description: String?,
        category: String?,
        tags: List<String>?,
        isPublic: Boolean?,
        rules: String?,
        avatarColor: String?,
        doPut: suspend (id: String, body: UpdateCommunityBody) -> ApiResult<Community>,
        onSuccess: () -> Unit = {}
    ) = viewModelScope.launch {
        if (_state.value.updatingCommunityId != null) return@launch
        _state.update { it.copy(updatingCommunityId = community.id, error = null) }
        try {
            when (val result = doPut(community.id, UpdateCommunityBody(name, description, category, tags, isPublic, rules, avatarColor))) {
                is ApiResult.Success -> {
                    applyUpdated(result.value)
                    onSuccess()
                }
                is ApiResult.Error -> _state.update { it.copy(error = result.message) }
            }
        } finally {
            _state.update { it.copy(updatingCommunityId = null) }
        }
    }

    fun applyUpdated(updated: Community) = _state.update { current ->
        current.copy(communities = current.communities.map { if (it.id == updated.id) updated else it })
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
    var editingCommunity by remember { mutableStateOf<Community?>(null) }
    var pendingDelete by remember { mutableStateOf<Community?>(null) }
    // حقن PUT من الشاشة (تملك tokens) — الـ ViewModel لا يرى الجلسة.
    val doPut: suspend (String, UpdateCommunityBody) -> ApiResult<Community> =
        remember(tokens) { { id, body -> putCommunityUpdate(tokens, id, body) } }

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
                            onDelete = { pendingDelete = community },
                            onEdit = { vm.clearError(); editingCommunity = community }
                        )
                    }
                }
            }
        }
    }

    if (state.showCreate) {
        CreateCommunityDialog(
            onDismiss = vm::hideCreate,
            onSubmit = { name, desc, cat, isPublic ->
                vm.create(name, desc, cat, isPublic) {}
            }
        )
    }

    editingCommunity?.let { community ->
        EditCommunityDialog(
            community = community,
            saving = state.updatingCommunityId == community.id,
            error = state.error,
            onDismiss = { editingCommunity = null },
            onSave = { name, desc, cat, isPublic, tags, rules, avatarColor ->
                vm.update(community, name, desc, cat, tags, isPublic, rules, avatarColor, doPut) {
                    editingCommunity = null
                }
            }
        )
    }

    pendingDelete?.let { community ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("تأكيد حذف المجتمع") },
            text = { Text("هل تريد حذف «${community.name}»؟ ستُؤرشف بيانات المجتمع ولن تظهر للأعضاء.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; vm.delete(community) }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun CommunityCard(
    community: Community,
    onOpen: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {}
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
                if (community.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        community.tags.take(5).joinToString(" ") { "#$it" },
                        color = YounesEmerald,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                community.rules?.takeIf { it.isNotBlank() }?.let { rules ->
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Muted, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            rules,
                            color = Muted,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
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
                                text = { Text("تعديل المجتمع") },
                                onClick = { menuOpen = false; onEdit() },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
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
                community.isJoined -> OutlinedButton(onClick = onLeave) {
                    Icon(Icons.Default.Logout, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مغادرة", fontSize = 12.sp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCommunityDialog(
    community: Community,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean, List<String>, String, String) -> Unit
) {
    var name by remember(community.id) { mutableStateOf(community.name) }
    var description by remember(community.id) { mutableStateOf(community.description.orEmpty()) }
    var category by remember(community.id) { mutableStateOf(community.category) }
    var tagsInput by remember(community.id) { mutableStateOf(community.tags.joinToString(", ")) }
    var rules by remember(community.id) { mutableStateOf(community.rules.orEmpty()) }
    var avatarColor by remember(community.id) { mutableStateOf(community.avatarColor) }
    var isPublic by remember(community.id) { mutableStateOf(community.isPublic) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("تعديل المجتمع") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم المجتمع") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("الوصف") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                OutlinedTextField(category, { category = it }, label = { Text("التصنيف") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(tagsInput, { tagsInput = it }, label = { Text("الوسوم (افصل بفاصلة)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(rules, { rules = it }, label = { Text("قواعد المجتمع") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it }, enabled = !saving)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPublic) "عام" else "خاص (يتطلب موافقة)")
                }
                Text("لون الصورة", fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CommunityAvatarColors.forEach { color ->
                        FilterChip(
                            selected = avatarColor.equals(color, ignoreCase = true),
                            onClick = { avatarColor = color },
                            label = { Text("●", color = parseColorOrDefault(color)) },
                            enabled = !saving
                        )
                    }
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), description.trim(), category.trim(),
                    isPublic, parseTagsInput(tagsInput), rules.trim(), avatarColor) },
                enabled = !saving && name.trim().length in 2..100 &&
                    description.length <= 500 && category.trim().length in 1..50 && rules.length <= 2000
            ) { Text(if (saving) "جارٍ الحفظ..." else "حفظ", color = YounesEmerald) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") } }
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
