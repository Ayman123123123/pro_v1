package com.red.sovereign.features.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.ui.components.SovereignGlassCard
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val MutedText = Color(0xFF8B98A5)
private val CardBg = Color(0xFF131B26)
private val BoostPurple = Color(0xFF9C27B0)
private val BroadcastBlue = Color(0xFF2196F3)

data class ChannelsUiState(
    val loading: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val subscribedChannelIds: Set<String> = emptySet(),
    val error: String? = null,
    val query: String = "",
    val showCreateDialog: Boolean = false,
    val selectedChannelForBoost: Channel? = null,
    val activeTab: ChannelTab = ChannelTab.ALL
)

enum class ChannelTab(val label: String) {
    ALL("جميع القنوات"),
    SUBSCRIBED("قنواتي المشترك بها")
}

class ChannelsViewModel(private val api: ChannelsApi) : ViewModel() {
    private val _state = MutableStateFlow(ChannelsUiState())
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = it.channels.isEmpty(), error = null) }
        viewModelScope.launch {
            loadChannels(_state.value.query)
        }
    }

    fun setTab(tab: ChannelTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadChannels(q)
        }
    }

    private suspend fun loadChannels(query: String) {
        when (val result = api.list(search = query.takeIf { it.isNotBlank() }, limit = 30)) {
            is ApiResult.Success -> {
                _state.update {
                    it.copy(loading = false, channels = result.value, error = null)
                }
            }
            is ApiResult.Error -> {
                _state.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun join(channel: Channel) = viewModelScope.launch {
        when (val result = api.join(channel.id)) {
            is ApiResult.Success -> {
                _state.update { current ->
                    current.copy(
                        subscribedChannelIds = current.subscribedChannelIds + channel.id,
                        channels = current.channels.map {
                            if (it.id == channel.id) it.copy(subscriberCount = it.subscriberCount + 1) else it
                        }
                    )
                }
            }
            is ApiResult.Error -> {
                _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun leave(channel: Channel) = viewModelScope.launch {
        when (val result = api.leave(channel.id)) {
            is ApiResult.Success -> {
                _state.update { current ->
                    current.copy(
                        subscribedChannelIds = current.subscribedChannelIds - channel.id,
                        channels = current.channels.map {
                            if (it.id == channel.id) it.copy(subscriberCount = (it.subscriberCount - 1).coerceAtLeast(0)) else it
                        }
                    )
                }
            }
            is ApiResult.Error -> {
                _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun openBoostDialog(channel: Channel) {
        _state.update { it.copy(selectedChannelForBoost = channel) }
    }

    fun closeBoostDialog() {
        _state.update { it.copy(selectedChannelForBoost = null) }
    }

    fun showCreate() = _state.update { it.copy(showCreateDialog = true) }
    fun hideCreate() = _state.update { it.copy(showCreateDialog = false) }

    fun createChannel(
        name: String,
        username: String?,
        description: String?,
        isPublic: Boolean,
        isBroadcast: Boolean,
        onSuccess: () -> Unit
    ) {
        if (name.trim().length < 2) {
            _state.update { it.copy(error = "اسم القناة يجب أن يكون حرفين على الأقل") }
            return
        }
        viewModelScope.launch {
            val body = CreateChannelBody(
                name = name.trim(),
                username = username?.takeIf { it.isNotBlank() }?.trim()?.removePrefix("@"),
                description = description?.takeIf { it.isNotBlank() }?.trim(),
                isPublic = isPublic,
                isBroadcast = isBroadcast
            )
            when (val result = api.create(body)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            showCreateDialog = false,
                            channels = listOf(result.value) + it.channels,
                            subscribedChannelIds = it.subscribedChannelIds + result.value.id
                        )
                    }
                    onSuccess()
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    tokens: TokenStore,
    onBack: () -> Unit,
    onOpenChannel: (channelId: String, channelName: String) -> Unit = { _, _ -> }
) {
    val api = remember(tokens) { ChannelsApi(AuthorizedApiClient(tokens)) }
    val vm: ChannelsViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChannelsViewModel(api) as T
        }
    )
    val state by vm.state.collectAsState()

    Scaffold(
        containerColor = SovereignColors.ObsidianDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("القنوات السيادية", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "بث مباشر ومشاركات عامة ومشفرة",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = vm::showCreate) {
                        Icon(Icons.Default.AddCircle, "إنشاء قناة", tint = YounesEmerald)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SovereignColors.ObsidianDeep)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search Bar
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("بحث عن قناة أو @معرّف...", color = MutedText) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = YounesEmerald) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, null, tint = MutedText)
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = YounesEmerald,
                    unfocusedBorderColor = Color(0xFF263238),
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Tabs Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChannelTab.entries.forEach { tab ->
                    val selected = state.activeTab == tab
                    Surface(
                        onClick = { vm.setTab(tab) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) YounesEmerald.copy(alpha = 0.2f) else CardBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) YounesEmerald else Color(0xFF263238)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = tab.label,
                            color = if (selected) YounesEmerald else MutedText,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }

            // Error Banner
            state.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(err, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        IconButton(onClick = vm::clearError, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            val displayedChannels = remember(state.channels, state.activeTab, state.subscribedChannelIds) {
                when (state.activeTab) {
                    ChannelTab.ALL -> state.channels
                    ChannelTab.SUBSCRIBED -> state.channels.filter { it.id in state.subscribedChannelIds }
                }
            }

            when {
                state.loading && state.channels.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = YounesEmerald)
                    }
                }
                displayedChannels.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Campaign,
                                contentDescription = null,
                                tint = MutedText.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (state.query.isNotBlank()) "لا توجد قنوات تطابق البحث" else "لا توجد قنوات حالياً",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                if (state.activeTab == ChannelTab.SUBSCRIBED) "لم تشترك في أي قناة بعد" else "كن أول من ينشئ قناة سيادية!",
                                color = MutedText,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedChannels, key = { it.id }) { channel ->
                            val isSubscribed = channel.id in state.subscribedChannelIds
                            ChannelCard(
                                channel = channel,
                                isSubscribed = isSubscribed,
                                onOpen = { onOpenChannel(channel.id, channel.name) },
                                onJoin = { vm.join(channel) },
                                onLeave = { vm.leave(channel) },
                                onBoost = { vm.openBoostDialog(channel) }
                            )
                        }
                    }
                }
            }
        }

        if (state.showCreateDialog) {
            CreateChannelDialog(
                onDismiss = vm::hideCreate,
                onCreate = { name, username, desc, isPublic, isBroadcast ->
                    vm.createChannel(name, username, desc, isPublic, isBroadcast) {}
                }
            )
        }

        state.selectedChannelForBoost?.let { channel ->
            ChannelBoostBottomSheet(
                channel = channel,
                onDismiss = vm::closeBoostDialog
            )
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    isSubscribed: Boolean,
    onOpen: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onBoost: () -> Unit
) {
    val level = channel.level
    val levelPerks = remember(level) { perksForLevel(level) }

    SovereignGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar / Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(YounesEmerald.copy(alpha = 0.8f), BroadcastBlue.copy(alpha = 0.8f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (channel.isBroadcast) Icons.Default.Campaign else Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = channel.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (level > 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AqyalGold.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, AqyalGold)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, null, tint = AqyalGold, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("مستوى $level", fontSize = 10.sp, color = AqyalGold, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    channel.username?.let {
                        Text(
                            text = "@$it",
                            fontSize = 12.sp,
                            color = YounesEmerald
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            "${channel.subscriberCount} مشترك",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                        if (channel.isBroadcast) {
                            Text("• قناة بث", fontSize = 11.sp, color = BroadcastBlue)
                        }
                    }
                }

                // Join/Leave Button
                Button(
                    onClick = if (isSubscribed) onLeave else onJoin,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSubscribed) Color(0xFF263238) else YounesEmerald,
                        contentColor = if (isSubscribed) Color.White else Color(0xFF002117)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        if (isSubscribed) "مغادرة" else "انضمام",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            channel.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom Actions: Boosts & Status
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = onBoost,
                    shape = RoundedCornerShape(10.dp),
                    color = BoostPurple.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, BoostPurple.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, null, tint = BoostPurple, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${channel.boostsCount} تعزيز",
                            fontSize = 11.sp,
                            color = BoostPurple,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (levelPerks.customLinks) {
                    Text(
                        "⚡ مفعّل: روابط مخصصة وHD",
                        fontSize = 10.sp,
                        color = AqyalGold
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateChannelDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, username: String?, desc: String?, isPublic: Boolean, isBroadcast: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var isBroadcast by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161E28),
        shape = RoundedCornerShape(18.dp),
        title = {
            Text("إنشاء قناة سيادية جديدة", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم القناة *", color = MutedText) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YounesEmerald,
                        unfocusedBorderColor = Color(0xFF263238),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("المعرّف (اختياري، مثلاً news)", color = MutedText) },
                    prefix = { Text("@", color = YounesEmerald) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YounesEmerald,
                        unfocusedBorderColor = Color(0xFF263238),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("وصف القناة", color = MutedText) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YounesEmerald,
                        unfocusedBorderColor = Color(0xFF263238),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Switch(
                        checked = isBroadcast,
                        onCheckedChange = { isBroadcast = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = YounesEmerald)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("وضع البث (Broadcast)", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("المشرفون فقط يمكنهم النشر", fontSize = 11.sp, color = MutedText)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = YounesEmerald)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("قناة عامة (Public)", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("تظهر في البحث للجميع", fontSize = 11.sp, color = MutedText)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, username, desc, isPublic, isBroadcast) },
                enabled = name.trim().length >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = YounesEmerald, contentColor = Color(0xFF002117))
            ) {
                Text("إنشاء", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = MutedText)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelBoostBottomSheet(
    channel: Channel,
    onDismiss: () -> Unit
) {
    val level = channel.level
    val perks = remember(level) { perksForLevel(level) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131A24),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(BoostPurple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bolt, null, tint = BoostPurple, modifier = Modifier.size(36.dp))
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "تعزيز قناة ${channel.name}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
            Text(
                "التعزيزات تفتح مزايا حصرية للقناة وترفع مستواها",
                fontSize = 12.sp,
                color = MutedText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Current Stats Card
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF263238)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("التعزيزات الحالية", fontSize = 11.sp, color = MutedText)
                        Text("${channel.boostsCount}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BoostPurple)
                    }
                    Divider(
                        modifier = Modifier
                            .height(30.dp)
                            .width(1.dp),
                        color = Color(0xFF263238)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("المستوى الحالي", fontSize = 11.sp, color = MutedText)
                        Text("Level $level", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AqyalGold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("مزايا المستوى الحالي:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))

            val perkItems = remember(perks) {
                listOf(
                    "رفع وسائط بدقة فائقة HD" to (level >= 1),
                    "روابط مخصصة للقناة" to (level >= 2),
                    "ردود أفعال إيموجي مخصصة" to (level >= 3),
                    "شارات ذهبية ومظهر سيادي مخصص" to (level >= 5)
                )
            }

            perkItems.forEach { (perk, unlocked) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (unlocked) YounesEmerald else MutedText.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        perk,
                        fontSize = 13.sp,
                        color = if (unlocked) Color.White else MutedText.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BoostPurple),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إغلاق", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
