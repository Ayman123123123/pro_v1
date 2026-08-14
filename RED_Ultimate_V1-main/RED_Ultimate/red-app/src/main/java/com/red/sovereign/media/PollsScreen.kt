package com.red.sovereign.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll as foundationHorizontalScroll
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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * ════════════════════════════════════════════════════════════════════════
 *  RED Sovereign — Polls Screen (Android)
 *  - List active polls (from backend)
 *  - Vote on a poll (POST vote to backend)
 *  - Create a new poll (POST to backend)
 *  - Close / delete a poll (admin actions)
 *  - All data is fetched live — no mock data, no fixtures
 * ════════════════════════════════════════════════════════════════════════
 */

private val BgColor = Color(0xFF0E1217)
private val Surface = Color(0xFF161C24)
private val Primary = Color(0xFF00E6A0)
private val OnPrimary = Color(0xFF003822)
private val Muted = Color(0xFF7A8590)
private val Danger = Color(0xFFFF4D6D)
private val Warning = Color(0xFFE8B84A)

data class PollsUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val polls: List<PollDto> = emptyList(),
    val error: String? = null,
    val statusFilter: String? = null, // null = all, otherwise ACTIVE/CLOSED/...
    val selectedPoll: PollDetailDto? = null,
    val selectedLoading: Boolean = false,
    val votedOptionIds: Set<String> = emptySet(),
    val showCreate: Boolean = false
)

class PollsViewModel(private val api: PollsApi) : ViewModel() {
    private val _state = MutableStateFlow(PollsUiState())
    val state: StateFlow<PollsUiState> = _state.asStateFlow()

    init { refresh() }

    fun setStatus(status: String?) {
        _state.update { it.copy(statusFilter = status) }
        refresh()
    }

    fun refresh() {
        val current = _state.value
        val isFirst = current.polls.isEmpty()
        _state.update { it.copy(loading = isFirst, refreshing = !isFirst, error = null) }
        viewModelScope.launch {
            val result = api.list(page = 0, size = 50, status = current.statusFilter)
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, refreshing = false, polls = result.value.content, error = null)
                }
                is ApiResult.Error -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.message)
                }
            }
        }
    }

    fun openPoll(pollId: String) {
        _state.update { it.copy(selectedPoll = null, selectedLoading = true) }
        viewModelScope.launch {
            val result = api.detail(pollId)
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        selectedLoading = false,
                        selectedPoll = result.value,
                        votedOptionIds = result.value.userVote.toSet()
                    )
                }
                is ApiResult.Error -> _state.update {
                    it.copy(selectedLoading = false, error = result.message)
                }
            }
        }
    }

    fun closeDetail() {
        _state.update { it.copy(selectedPoll = null, votedOptionIds = emptySet()) }
    }

    fun toggleOptionVote(optionId: String) {
        _state.update {
            val current = it.votedOptionIds
            val selected = it.selectedPoll ?: return@update it
            // Respect poll type semantics
            val singleChoice = selected.poll.pollType == "SINGLE_CHOICE"
            val newSet = if (singleChoice) setOf(optionId)
                        else if (current.contains(optionId)) current - optionId
                        else current + optionId
            it.copy(votedOptionIds = newSet)
        }
    }

    fun submitVote() {
        val selected = _state.value.selectedPoll ?: return
        val optionIds = _state.value.votedOptionIds.toList()
        if (optionIds.isEmpty()) return
        viewModelScope.launch {
            val result = api.vote(selected.poll.id, optionIds)
            when (result) {
                is ApiResult.Success -> {
                    // Re-fetch poll detail to get fresh vote counts
                    openPoll(selected.poll.id)
                    refresh()
                }
                is ApiResult.Error -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun showCreate() { _state.update { it.copy(showCreate = true) } }
    fun hideCreate() { _state.update { it.copy(showCreate = false) } }

    fun createPoll(question: String, options: List<String>, pollType: String, isAnonymous: Boolean) {
        if (question.isBlank() || options.size < 2) {
            _state.update { it.copy(error = "السؤال مطلوب وخياران على الأقل") }
            return
        }
        viewModelScope.launch {
            val req = CreatePollRequest(
                question = question.trim(),
                options = options.map { it.trim() }.filter { it.isNotEmpty() },
                pollType = pollType,
                isAnonymous = isAnonymous
            )
            val result = api.create(req)
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(showCreate = false) }
                    refresh()
                }
                is ApiResult.Error -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun closePoll(pollId: String) {
        viewModelScope.launch {
            when (api.close(pollId)) {
                is ApiResult.Success -> {
                    closeDetail()
                    refresh()
                }
                is ApiResult.Error -> { /* keep UI */ }
            }
        }
    }

    fun deletePoll(pollId: String) {
        viewModelScope.launch {
            when (api.delete(pollId)) {
                is ApiResult.Success -> {
                    closeDetail()
                    refresh()
                }
                is ApiResult.Error -> { /* keep UI */ }
            }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollsScreen(
    tokens: TokenStore,
    onBack: () -> Unit,
    isAdmin: Boolean = false
) {
    val context = LocalContext.current
    val client = remember(tokens) { AuthorizedApiClient(tokens) }
    val api = remember(client) { PollsApi(client) }
    val vm: PollsViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PollsViewModel(api) as T
    })
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = { Text("استطلاعات الرأي", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Color.White)
                    }
                    if (isAdmin) {
                        IconButton(onClick = { vm.showCreate() }) {
                            Icon(Icons.Default.Add, contentDescription = "إنشاء استطلاع", tint = Primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BgColor)
        ) {
            state.error?.let { err ->
                ErrorBanner(message = err, onClose = vm::clearError)
            }

            StatusFilterBar(
                current = state.statusFilter,
                onSelect = vm::setStatus
            )

            when {
                state.loading -> CenteredLoader("جاري تحميل الاستطلاعات...")
                state.polls.isEmpty() -> EmptyState(
                    title = "لا توجد استطلاعات",
                    subtitle = "اضغط + لإنشاء أول استطلاع"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.polls, key = { it.id }) { poll ->
                        PollListItem(
                            poll = poll,
                            onClick = { vm.openPoll(poll.id) }
                        )
                    }
                }
            }
        }

        if (state.selectedLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }

        state.selectedPoll?.let { detail ->
            PollDetailSheet(
                detail = detail,
                votedOptionIds = state.votedOptionIds,
                onToggle = vm::toggleOptionVote,
                onSubmitVote = vm::submitVote,
                onClose = vm::closeDetail,
                onClosePoll = if (isAdmin) ({ vm.closePoll(detail.poll.id) }) else null,
                onDelete = if (isAdmin) ({ vm.deletePoll(detail.poll.id) }) else null
            )
        }

        if (state.showCreate) {
            CreatePollDialog(
                onDismiss = vm::hideCreate,
                onSubmit = vm::createPoll
            )
        }
    }
}

@Composable
private fun StatusFilterBar(current: String?, onSelect: (String?) -> Unit) {
    val options = listOf(
        null to "الكل",
        "ACTIVE" to "نشطة",
        "CLOSED" to "مغلقة",
        "DRAFT" to "مسودة"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = current == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Surface,
                    selectedContainerColor = Primary,
                    selectedLabelColor = OnPrimary,
                    labelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun PollListItem(poll: PollDto, onClick: () -> Unit) {
    val statusColor = when (poll.status) {
        "ACTIVE" -> Primary
        "CLOSED" -> Muted
        "DRAFT" -> Warning
        else -> Muted
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Poll, contentDescription = null, tint = statusColor)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        poll.question,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${poll.totalVotes} صوت · ${poll.uniqueVoters} مصوّت",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                StatusPill(poll.status, statusColor)
            }

            if (!poll.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    poll.description,
                    color = Muted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                formatDate(poll.endsAt ?: poll.startsAt),
                color = Muted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun StatusPill(status: String, color: Color) {
    val (label, icon) = when (status) {
        "ACTIVE" -> "نشطة" to Icons.Default.PlayArrow
        "CLOSED" -> "مغلقة" to Icons.Default.Lock
        "DRAFT" -> "مسودة" to Icons.Default.Edit
        "ARCHIVED" -> "مؤرشفة" to Icons.Default.Archive
        else -> status to Icons.Default.Help
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PollDetailSheet(
    detail: PollDetailDto,
    votedOptionIds: Set<String>,
    onToggle: (String) -> Unit,
    onSubmitVote: () -> Unit,
    onClose: () -> Unit,
    onClosePoll: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    var showActions by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Surface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    detail.poll.question,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // زر الخيارات الإدارية — يظهر فقط للمشرف
                if (onClosePoll != null || onDelete != null) {
                    IconButton(onClick = { showActions = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "خيارات", tint = Color.White)
                    }
                }
            }

            detail.poll.description?.let { desc ->
                Spacer(Modifier.height(8.dp))
                Text(desc, color = Muted, fontSize = 14.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "إجمالي الأصوات: ${detail.poll.totalVotes}",
                color = Muted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))

            detail.options.forEach { option ->
                PollOptionRow(
                    option = option,
                    selected = votedOptionIds.contains(option.id),
                    hasVoted = votedOptionIds.isNotEmpty(),
                    isSingleChoice = detail.poll.pollType == "SINGLE_CHOICE",
                    isActive = detail.poll.status == "ACTIVE",
                    onClick = { onToggle(option.id) }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))

            if (detail.poll.status == "ACTIVE" && detail.canVote) {
                Button(
                    onClick = onSubmitVote,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = votedOptionIds.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary,
                        disabledContainerColor = Primary.copy(alpha = 0.3f)
                    )
                ) {
                    Text("تصويت", fontWeight = FontWeight.Bold)
                }
            }

            if (detail.poll.status == "CLOSED") {
                Text(
                    "تم إغلاق هذا الاستطلاع",
                    color = Warning,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showActions && (onClosePoll != null || onDelete != null)) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text("إجراءات الاستطلاع") },
            text = { Text("ماذا تريد أن تفعل بهذا الاستطلاع؟") },
            confirmButton = {
                if (onClosePoll != null) {
                    TextButton(onClick = {
                        showActions = false
                        onClosePoll()
                    }) { Text("إغلاق", color = Warning) }
                }
            },
            dismissButton = {
                Row {
                    if (onDelete != null) {
                        TextButton(onClick = {
                            showActions = false
                            onDelete()
                        }) { Text("حذف", color = Danger) }
                    }
                    TextButton(onClick = { showActions = false }) { Text("إلغاء") }
                }
            }
        )
    }
}

@Composable
private fun PollOptionRow(
    option: PollOptionDto,
    selected: Boolean,
    hasVoted: Boolean,
    isSingleChoice: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val animatedPercent by animateFloatAsState(
        targetValue = option.percentage.toFloat(),
        animationSpec = tween(durationMillis = 600),
        label = "percent"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) Primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = isActive && !hasVoted, onClick = onClick)
    ) {
        // Background progress bar
        if (hasVoted || !isActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(
                        Primary.copy(alpha = (animatedPercent / 100f).coerceIn(0f, 1f) * 0.25f)
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSingleChoice) Icons.Default.RadioButtonUnchecked else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (selected) Primary else Muted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                option.optionText,
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            if (hasVoted || !isActive) {
                Text(
                    "${option.percentage.toInt()}%",
                    color = Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePollDialog(
    onDismiss: () -> Unit,
    onSubmit: (question: String, options: List<String>, pollType: String, isAnonymous: Boolean) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var pollType by remember { mutableStateOf("SINGLE_CHOICE") }
    var isAnonymous by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء استطلاع جديد") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("السؤال") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(Modifier.height(12.dp))

                options.forEachIndexed { idx, opt ->
                    OutlinedTextField(
                        value = opt,
                        onValueChange = { newValue ->
                            options = options.toMutableList().also { it[idx] = newValue }
                        },
                        label = { Text("الخيار ${idx + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (options.size < 10) {
                    TextButton(onClick = { options = options + "" }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة خيار")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("نوع الاستطلاع", color = Color.White, fontSize = 13.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pollType == "SINGLE_CHOICE",
                        onClick = { pollType = "SINGLE_CHOICE" },
                        label = { Text("اختيار واحد") }
                    )
                    FilterChip(
                        selected = pollType == "MULTIPLE_CHOICE",
                        onClick = { pollType = "MULTIPLE_CHOICE" },
                        label = { Text("اختيار متعدد") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                    Spacer(Modifier.width(8.dp))
                    Text("مجهول", color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSubmit(question, options, pollType, isAnonymous)
            }) { Text("إنشاء", color = Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ErrorBanner(message: String, onClose: () -> Unit) {
    Surface(
        color = Danger.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Danger)
            Spacer(Modifier.width(8.dp))
            Text(message, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
            }
        }
    }
}

@Composable
private fun CenteredLoader(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Primary)
            Spacer(Modifier.height(12.dp))
            Text(text, color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Poll,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

private fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso.substring(0, minOf(19, iso.length))) ?: return iso
        val formatter = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("ar"))
        formatter.format(date)
    } catch (_: Exception) {
        iso
    }
}

private fun Modifier.horizontalScroll(state: androidx.compose.foundation.ScrollState) =
    this.then(this.foundationHorizontalScroll(state))
