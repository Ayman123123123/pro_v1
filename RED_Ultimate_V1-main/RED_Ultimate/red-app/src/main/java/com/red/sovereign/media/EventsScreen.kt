package com.red.sovereign.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
 *  RED Sovereign — Events Screen (Android)
 *  - List upcoming/live/past events
 *  - RSVP to events
 *  - Create new events
 *  - All data fetched live from backend — no mock data anywhere
 * ════════════════════════════════════════════════════════════════════════
 */

private val BgColor = Color(0xFF0E1217)
private val Surface = Color(0xFF161C24)
private val Primary = Color(0xFF00E6A0)
private val OnPrimary = Color(0xFF003822)
private val Muted = Color(0xFF7A8590)
private val Danger = Color(0xFFFF4D6D)
private val Warning = Color(0xFFE8B84A)
private val Info = Color(0xFF35CBE0)

data class EventsUiState(
    val loading: Boolean = false,
    val events: List<EventDto> = emptyList(),
    val error: String? = null,
    val statusFilter: String? = null,
    val selected: EventDetailDto? = null,
    val selectedLoading: Boolean = false,
    val showCreate: Boolean = false
)

class EventsViewModel(private val api: EventsApi) : ViewModel() {
    private val _state = MutableStateFlow(EventsUiState())
    val state: StateFlow<EventsUiState> = _state.asStateFlow()

    init { refresh() }

    fun setStatus(status: String?) {
        _state.update { it.copy(statusFilter = status) }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = it.events.isEmpty(), error = null) }
        viewModelScope.launch {
            when (val result = api.list(page = 0, size = 50, status = _state.value.statusFilter)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, events = result.value.content, error = null)
                }
                is ApiResult.Error -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun openEvent(id: String) {
        _state.update { it.copy(selected = null, selectedLoading = true) }
        viewModelScope.launch {
            when (val r = api.detail(id)) {
                is ApiResult.Success -> _state.update {
                    it.copy(selectedLoading = false, selected = r.value)
                }
                is ApiResult.Error -> _state.update {
                    it.copy(selectedLoading = false, error = r.message)
                }
            }
        }
    }

    fun closeDetail() { _state.update { it.copy(selected = null) } }

    fun rsvp(status: String) {
        val event = _state.value.selected ?: return
        viewModelScope.launch {
            when (api.rsvp(event.event.id, status)) {
                is ApiResult.Success -> openEvent(event.event.id)
                is ApiResult.Error -> { /* keep UI */ }
            }
        }
    }

    fun showCreate() { _state.update { it.copy(showCreate = true) } }
    fun hideCreate() { _state.update { it.copy(showCreate = false) } }

    fun create(
        title: String,
        description: String,
        locationName: String,
        startsAtIso: String,
        endsAtIso: String?,
        eventType: String,
        visibility: String
    ) {
        if (title.isBlank() || startsAtIso.isBlank()) {
            _state.update { it.copy(error = "العنوان وتاريخ البدء مطلوبان") }
            return
        }
        viewModelScope.launch {
            val req = CreateEventRequest(
                title = title.trim(),
                description = description.takeIf { it.isNotBlank() }?.trim(),
                locationName = locationName.takeIf { it.isNotBlank() }?.trim(),
                startsAt = startsAtIso,
                endsAt = endsAtIso,
                eventType = eventType,
                visibility = visibility
            )
            when (api.create(req)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(showCreate = false) }
                    refresh()
                }
                is ApiResult.Error -> _state.update { it.copy(error = "تعذر إنشاء الحدث") }
            }
        }
    }

    fun cancel(reason: String) {
        val event = _state.value.selected ?: return
        viewModelScope.launch {
            when (api.cancel(event.event.id, reason)) {
                is ApiResult.Success -> {
                    closeDetail()
                    refresh()
                }
                is ApiResult.Error -> { /* keep UI */ }
            }
        }
    }

    fun delete() {
        val event = _state.value.selected ?: return
        viewModelScope.launch {
            when (api.delete(event.event.id)) {
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
fun EventsScreen(tokens: TokenStore, onBack: () -> Unit, isAdmin: Boolean = false) {
    val client = remember(tokens) { AuthorizedApiClient(tokens) }
    val api = remember(client) { EventsApi(client) }
    val vm: EventsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = EventsViewModel(api) as T
        }
    )
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = { Text("الفعاليات", color = Color.White) },
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
                            Icon(Icons.Default.Add, contentDescription = "إنشاء فعالية", tint = Primary)
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
                ErrorBanner(err, onClose = vm::clearError)
            }

            StatusFilterBar(state.statusFilter, vm::setStatus)

            when {
                state.loading -> CenteredLoader("جاري تحميل الفعاليات...")
                state.events.isEmpty() -> EmptyState(
                    title = "لا توجد فعاليات",
                    subtitle = "اضغط + لإنشاء أول فعالية"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.events, key = { it.id }) { event ->
                        EventListItem(event, onClick = { vm.openEvent(event.id) })
                    }
                }
            }
        }

        if (state.selectedLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }

        state.selected?.let { detail ->
            EventDetailSheet(
                detail = detail,
                onRsvp = vm::rsvp,
                onClose = vm::closeDetail,
                onCancel = if (isAdmin) ({ reason -> vm.cancel(reason) }) else null,
                onDelete = if (isAdmin) ({ vm.delete() }) else null
            )
        }

        if (state.showCreate) {
            CreateEventDialog(
                onDismiss = vm::hideCreate,
                onSubmit = vm::create
            )
        }
    }
}

@Composable
private fun StatusFilterBar(current: String?, onSelect: (String?) -> Unit) {
    val options = listOf(
        null to "الكل",
        "SCHEDULED" to "قادمة",
        "LIVE" to "مباشرة",
        "ENDED" to "منتهية",
        "CANCELLED" to "ملغاة"
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
private fun EventListItem(event: EventDto, onClick: () -> Unit) {
    val (statusColor, statusLabel, statusIcon) = when (event.status) {
        "LIVE" -> Triple(Danger, "مباشرة الآن", Icons.Default.FiberManualRecord)
        "SCHEDULED" -> Triple(Info, "قادمة", Icons.Default.Schedule)
        "ENDED" -> Triple(Muted, "منتهية", Icons.Default.CheckCircle)
        "CANCELLED" -> Triple(Warning, "ملغاة", Icons.Default.Cancel)
        "DRAFT" -> Triple(Warning, "مسودة", Icons.Default.Edit)
        else -> Triple(Muted, event.status, Icons.Default.Help)
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(eventIconFor(event.eventType), contentDescription = null, tint = statusColor)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        event.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatRange(event.startsAt, event.endsAt),
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                StatusBadge(statusLabel, statusColor, statusIcon)
            }

            event.locationName?.takeIf { it.isNotBlank() }?.let { loc ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(loc, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${event.currentAttendees}${event.maxAttendees?.let { " / $it" } ?: ""} مشارك",
                    color = Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(
    detail: EventDetailDto,
    onRsvp: (String) -> Unit,
    onClose: () -> Unit,
    onCancel: ((String) -> Unit)?,
    onDelete: (() -> Unit)?
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val event = detail.event

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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(eventIconFor(event.eventType), contentDescription = null, tint = Primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        event.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatRange(event.startsAt, event.endsAt),
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
            }

            event.description?.let { desc ->
                Spacer(Modifier.height(16.dp))
                Text(desc, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
            }

            Spacer(Modifier.height(16.dp))

            InfoRow(Icons.Default.Schedule, "يبدأ", formatDateTime(event.startsAt))
            event.endsAt?.let { InfoRow(Icons.Default.AccessTime, "ينتهي", formatDateTime(it)) }
            event.locationName?.let {
                InfoRow(Icons.Default.LocationOn, "المكان", it)
            }
            event.maxAttendees?.let {
                InfoRow(Icons.Default.Group, "السعة", "$event.currentAttendees / $it")
            }
            InfoRow(Icons.Default.Visibility, "الظهور", visibilityLabel(event.visibility))

            Spacer(Modifier.height(20.dp))

            if (detail.attendees.isNotEmpty()) {
                Text("المشاركون (${detail.attendees.size})", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                detail.attendees.take(8).forEach { att ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            att.userId.take(8) + "…",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            rsvpLabel(att.rsvpStatus),
                            color = rsvpColor(att.rsvpStatus),
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (event.rsvpEnabled && (event.status == "SCHEDULED" || event.status == "LIVE")) {
                Text("حالة الحضور", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onRsvp("GOING") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (detail.myRsvp == "GOING") Primary else Surface,
                            contentColor = if (detail.myRsvp == "GOING") OnPrimary else Color.White
                        )
                    ) { Text("سأحضر") }
                    Button(
                        onClick = { onRsvp("MAYBE") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (detail.myRsvp == "MAYBE") Warning else Surface,
                            contentColor = if (detail.myRsvp == "MAYBE") Color.Black else Color.White
                        )
                    ) { Text("ربما") }
                    Button(
                        onClick = { onRsvp("NOT_GOING") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (detail.myRsvp == "NOT_GOING") Danger else Surface,
                            contentColor = if (detail.myRsvp == "NOT_GOING") Color.White else Color.White
                        )
                    ) { Text("لن أحضر") }
                }
            }

            Spacer(Modifier.height(16.dp))

            // أزرار إدارية — تظهر فقط للمشرف (isAdmin). المستخدم العادي يرى RSVP فقط.
            if (onCancel != null && onDelete != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = event.status != "CANCELLED" && event.status != "ENDED",
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Warning)
                    ) { Text("إلغاء الفعالية") }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
                    ) { Text("حذف") }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showCancelDialog && onCancel != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("إلغاء الفعالية") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب الإلغاء") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    onCancel(reason.ifBlank { "إلغاء إداري" })
                }) { Text("تأكيد الإلغاء", color = Warning) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("تراجع") }
            }
        )
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف نهائي؟") },
            text = { Text("سيتم حذف الفعالية وكل البيانات المرتبطة بها. لا يمكن التراجع.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("حذف", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("تراجع") }
            }
        )
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.width(72.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateEventDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String?, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var locationName by remember { mutableStateOf("") }
    var startsAt by remember { mutableStateOf(defaultIsoPlusOneHour()) }
    var endsAt by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("MEETING") }
    var visibility by remember { mutableStateOf("PUBLIC") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء فعالية جديدة") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("العنوان") },
                    modifier = Modifier.fillMaxWidth()
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("المكان") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = startsAt,
                    onValueChange = { startsAt = it },
                    label = { Text("يبدأ (ISO 8601)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = endsAt,
                    onValueChange = { endsAt = it },
                    label = { Text("ينتهي (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("نوع الفعالية", color = Color.White, fontSize = 13.sp)
                FlowRowWrap(values = listOf("MEETING" to "اجتماع", "CONFERENCE" to "مؤتمر", "WEBINAR" to "ندوة", "SOCIAL" to "اجتماعية", "CELEBRATION" to "احتفال", "OTHER" to "أخرى"), selected = eventType, onSelect = { eventType = it })
                Spacer(Modifier.height(8.dp))
                Text("الظهور", color = Color.White, fontSize = 13.sp)
                FlowRowWrap(values = listOf("PUBLIC" to "عام", "PRIVATE" to "خاص", "INVITATION_ONLY" to "بالدعوة"), selected = visibility, onSelect = { visibility = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSubmit(title, description, locationName, startsAt, endsAt.ifBlank { null }, eventType, visibility)
            }) { Text("إنشاء", color = Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun FlowRowWrap(
    values: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        values.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onClose: () -> Unit) {
    Surface(color = Danger.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Danger)
            Spacer(Modifier.width(8.dp))
            Text(message, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White) }
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
            Icon(Icons.Default.Event, contentDescription = null, tint = Muted, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

private fun eventIconFor(type: String) = when (type) {
    "MEETING" -> Icons.Default.Group
    "CONFERENCE" -> Icons.Default.Castle
    "WEBINAR" -> Icons.Default.LiveTv
    "SOCIAL" -> Icons.Default.Celebration
    "CELEBRATION" -> Icons.Default.Cake
    else -> Icons.Default.Event
}

private fun visibilityLabel(v: String) = when (v) {
    "PUBLIC" -> "عام"
    "PRIVATE" -> "خاص"
    "INVITATION_ONLY" -> "بالدعوة"
    else -> v
}

private fun rsvpLabel(s: String) = when (s) {
    "GOING" -> "سأحضر"
    "MAYBE" -> "ربما"
    "NOT_GOING" -> "لن أحضر"
    else -> s
}

private fun rsvpColor(s: String) = when (s) {
    "GOING" -> Primary
    "MAYBE" -> Warning
    "NOT_GOING" -> Danger
    else -> Muted
}

private fun formatDateTime(iso: String): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    parser.timeZone = TimeZone.getTimeZone("UTC")
    val date = parser.parse(iso.substring(0, minOf(19, iso.length))) ?: return iso
    SimpleDateFormat("dd MMM yyyy · HH:mm", Locale("ar")).format(date)
} catch (_: Exception) { iso }

private fun formatRange(start: String, end: String?): String {
    val s = formatDateTime(start)
    val e = end?.let { " — " + formatDateTime(it) } ?: ""
    return s + e
}

private fun defaultIsoPlusOneHour(): String {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return fmt.format(cal.time)
}

