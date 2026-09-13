package com.red.sovereign.features.explore

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.calls.ConferenceService
import com.red.sovereign.calls.LiveStreamService
import com.red.sovereign.ui.rememberCallPermissionLauncher
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesMuted
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ExploreLiveStream(
    val streamId: String,
    val title: String,
    val broadcasterName: String,
    val broadcasterRedId: String,
    val isPrivate: Boolean = false,
    val viewerCount: Int = 0,
    val inviteLink: String = "",
    // Legendary V2 — حقول جديدة بافتراضيات (الخادم القديم لا يرسلها)
    val category: String = "عام",
    val slowModeSec: Int = 0,
    val recordingEnabled: Boolean = false,
    val broadcasterAvatar: String? = null
)

@Serializable
data class ExploreVod(
    val streamId: String = "",
    val title: String = "",
    val broadcasterName: String = "",
    val category: String = "عام",
    val peakViewers: Int = 0,
    val hlsUrl: String? = null,
    val vodUrl: String? = null
)

@Serializable
data class ExploreSpace(
    val roomId: String,
    val title: String,
    val hostName: String,
    val hostRedId: String,
    val isSpace: Boolean = true,
    val isPrivate: Boolean = false,
    val participantCount: Int = 0,
    val inviteLink: String = ""
)

@Serializable
private data class CreateStreamBody(val title: String, val category: String = "عام", val isPrivate: Boolean = false, val password: String? = null)

@Serializable
private data class CreateSpaceBody(
    val roomId: String = "",
    val title: String,
    val isSpace: Boolean = true,
    val isPrivate: Boolean = false,
    val password: String? = null
)

@Serializable
private data class JoinBody(val password: String? = null)

private class ExploreApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun streams(query: String, page: Int = 0, size: Int = 20, category: String? = null): ApiResult<List<ExploreLiveStream>> {
        val cat = if (category.isNullOrBlank() || category == "الكل") "" else "&category=${encode(category)}"
        return decodeList(
            client.request("GET", "/api/livestream/public?query=${encode(query)}&page=$page&size=$size$cat")
        )
    }

    suspend fun vods(limit: Int = 10): ApiResult<List<ExploreVod>> = decodeList(
        client.request("GET", "/api/livestream/vods?limit=$limit")
    )

    suspend fun spaces(query: String): ApiResult<List<ExploreSpace>> = decodeList(
        client.request("GET", "/api/conference/public?isSpace=true&query=${encode(query)}")
    )

    suspend fun createStream(title: String, category: String = "عام", isPrivate: Boolean = false, password: String? = null): ApiResult<ExploreLiveStream> = decode(
        client.request("POST", "/api/livestream/create", json.encodeToString(CreateStreamBody(title.trim(), category, isPrivate, password)))
    )

    suspend fun createSpace(title: String): ApiResult<ExploreSpace> = decode(
        client.request("POST", "/api/conference/create", json.encodeToString(CreateSpaceBody(title = title.trim())))
    )

    suspend fun authorizeStream(streamId: String, password: String? = null): ApiResult<String> = client.request(
        "POST", "/api/livestream/$streamId/join", json.encodeToString(JoinBody(password))
    )

    suspend fun authorizeSpace(roomId: String): ApiResult<String> = client.request(
        "POST", "/api/conference/$roomId/join", json.encodeToString(JoinBody())
    )

    private inline fun <reified T> decode(result: ApiResult<String>): ApiResult<T> = when (result) {
        is ApiResult.Success -> runCatching { json.decodeFromString<T>(result.value) }
            .fold({ ApiResult.Success(result.code, it) }, { ApiResult.Error(500, "INVALID_EXPLORE_RESPONSE") })
        is ApiResult.Error -> result
    }

    private inline fun <reified T> decodeList(result: ApiResult<String>): ApiResult<List<T>> = decode(result)

    private fun encode(value: String): String = java.net.URLEncoder.encode(value.trim(), Charsets.UTF_8.name())
}

private data class ExploreState(
    val loading: Boolean = true,
    val query: String = "",
    val category: String = "الكل",
    val streams: List<ExploreLiveStream> = emptyList(),
    val spaces: List<ExploreSpace> = emptyList(),
    val vods: List<ExploreVod> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
) {
    companion object {
        // موحدة مع LIVE_CATEGORIES في الحوار (كانت 9 بلا طبخ/أعمال).
        val CATEGORIES = listOf("الكل", "عام", "تقنية", "ألعاب", "موسيقى", "تعليم", "ترفيه", "رياضة", "ديني", "طبخ", "أعمال")
    }
}

private class ExploreViewModel(private val api: ExploreApi) : ViewModel() {
    private val _state = MutableStateFlow(ExploreState())
    val state: StateFlow<ExploreState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init { refresh() }

    fun query(value: String) {
        _state.update { it.copy(query = value, page = 0) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refresh()
        }
    }

    fun category(value: String) {
        _state.update { it.copy(category = value, page = 0) }
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null, page = 0) }
        val s = _state.value
        val streams = api.streams(s.query, 0, 20, s.category)
        val spaces = api.spaces(s.query)
        val vods = api.vods(10)
        if (streams is ApiResult.Error) {
            _state.update { it.copy(loading = false, error = streams.message) }
            return@launch
        }
        if (spaces is ApiResult.Error) {
            _state.update { it.copy(loading = false, error = spaces.message) }
            return@launch
        }
        val streamItems = (streams as ApiResult.Success).value
        val spaceItems = (spaces as ApiResult.Success).value
        val vodItems = (vods as? ApiResult.Success)?.value ?: emptyList()
        _state.update {
            it.copy(
                loading = false,
                streams = streamItems,
                spaces = spaceItems,
                vods = vodItems,
                page = 0,
                hasMore = streamItems.size >= 20,
                error = null
            )
        }
    }

    fun loadMore() = viewModelScope.launch {
        val s = _state.value
        if (s.loadingMore || !s.hasMore) return@launch
        _state.update { it.copy(loadingMore = true) }
        when (val r = api.streams(s.query, s.page + 1, 20, s.category)) {
            is ApiResult.Success -> {
                _state.update {
                    it.copy(
                        streams = it.streams + r.value,
                        page = it.page + 1,
                        hasMore = r.value.size >= 20,
                        loadingMore = false
                    )
                }
            }
            is ApiResult.Error -> _state.update { it.copy(loadingMore = false, error = r.message) }
        }
    }

    fun createLive(title: String, started: (String) -> Unit) = operation {
        when (val result = api.createStream(title)) {
            is ApiResult.Success -> { started(result.value.streamId); refresh() }
            is ApiResult.Error -> fail(result.message)
        }
    }

    fun createSpace(title: String, started: (String) -> Unit) = operation {
        when (val result = api.createSpace(title)) {
            is ApiResult.Success -> { started(result.value.roomId); refresh() }
            is ApiResult.Error -> fail(result.message)
        }
    }

    fun joinLive(streamId: String, password: String? = null, joined: (String) -> Unit) = operation {
        when (val result = api.authorizeStream(streamId, password)) {
            is ApiResult.Success -> joined(streamId)
            is ApiResult.Error -> fail(result.message)
        }
    }

    fun joinSpace(roomId: String, joined: (String) -> Unit) = operation {
        when (val result = api.authorizeSpace(roomId)) {
            is ApiResult.Success -> joined(roomId)
            is ApiResult.Error -> fail(result.message)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun operation(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true, error = null) }
        try {
            block()
        } catch (error: Exception) {
            fail(error.message ?: "EXPLORE_OPERATION_FAILED")
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    private fun fail(message: String?) = _state.update { it.copy(error = message ?: "EXPLORE_OPERATION_FAILED") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedExploreScreen(tokens: TokenStore, ownRedId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val api = remember(tokens) { ExploreApi(AuthorizedApiClient(tokens)) }
    val vm: ExploreViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ExploreViewModel(api) as T
        }
    )
    val state by vm.state.collectAsState()
    var createKind by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var joinTarget by remember { mutableStateOf<ExploreLiveStream?>(null) }
    var joinPassword by remember { mutableStateOf("") }
    var pendingBroadcastId by remember { mutableStateOf<String?>(null) }
    // بوابة الصلاحيات: بدء البث كمذيع يحتاج كاميرا+ميكروفون (إصلاح الشاشة السوداء)
    val broadcastPermissionGate = rememberCallPermissionLauncher(
        needCamera = true,
        onGranted = {
            pendingBroadcastId?.let { id ->
                pendingBroadcastId = null
                LiveStreamService.start(context, id, ownRedId, true)
            }
        },
        onDenied = {
            pendingBroadcastId = null
            android.widget.Toast.makeText(context, "مطلوب إذن الكاميرا والميكروفون للبث — يمكنك المشاهدة بدونها", android.widget.Toast.LENGTH_LONG).show()
        }
    )

    Scaffold(
        containerColor = SovereignColors.Obsidian,
        topBar = {
            TopAppBar(
                title = { Text("الاستكشاف والسيادة", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
                actions = { IconButton(vm::refresh) { Icon(Icons.Default.Refresh, "تحديث") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { createKind = "LIVE" },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SovereignColors.LiveContainer,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.LiveTv, null); Text(" بدء بث") }
                Button(
                    onClick = { createKind = "SPACE" },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SovereignColors.SpaceContainer,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.Mic, null); Text(" إنشاء مساحة") }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::query,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                placeholder = { Text("ابحث بالبث أو المضيف أو المعرّف") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    { IconButton({ vm.query("") }) { Icon(Icons.Default.Close, "مسح") } }
                } else null,
                singleLine = true
            )
            // شرائح الفئات Legendary V2 — فلترة حقيقية عبر ?category=
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ExploreState.CATEGORIES) { cat ->
                    val sel = state.category == cat
                    Button(
                        onClick = { vm.category(cat) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sel) SovereignColors.LiveAccent else SovereignColors.SurfaceNavy,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(cat, fontSize = 12.sp) }
                }
            }
            state.error?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Text(error, Modifier.weight(1f).padding(horizontal = 8.dp))
                        IconButton(vm::clearError) { Icon(Icons.Default.Close, "إغلاق") }
                    }
                }
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Text("📡 البث المباشر المحلي", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SovereignColors.Cyan) }
                    // بانر البطل — أعلى بث ترند (الخادم يرتب بالترند) — مُقتبس من شاشة الاكتشاف التجريبية ببيانات حية
                    if (state.streams.isNotEmpty()) {
                        item {
                            val top = state.streams.first()
                            HeroLiveBanner(
                                title = top.title,
                                host = top.broadcasterName.ifBlank { top.broadcasterRedId },
                                viewers = top.viewerCount,
                                category = top.category,
                                recording = top.recordingEnabled,
                                onClick = {
                                    vm.joinLive(top.streamId, null) { id ->
                                        LiveStreamService.start(context, id, ownRedId, false)
                                    }
                                }
                            )
                        }
                    }
                    if (state.streams.isEmpty()) item { ExploreEmpty("لا توجد بثوث عامة نشطة — كن أول من يبث 🔴") }
                    items(state.streams, key = { it.streamId }) { stream ->
                        ExploreCard(
                            title = stream.title,
                            host = stream.broadcasterName.ifBlank { stream.broadcasterRedId },
                            count = "${stream.viewerCount} مشاهد • ${stream.category}" +
                                (if (stream.slowModeSec > 0) " • 🐢${stream.slowModeSec}ث" else "") +
                                (if (stream.recordingEnabled) " • ●REC" else ""),
                            accent = SovereignColors.LiveAccent,
                            container = SovereignColors.LiveContainer,
                            action = "مشاهدة",
                            enabled = !state.busy
                        ) {
                            // البث الخاص لا يظهر هنا أصلاً، لكن كلمة السر تُدعم للروابط المباشرة
                            vm.joinLive(stream.streamId, null) { id ->
                                LiveStreamService.start(context, id, ownRedId, false)
                            }
                        }
                    }
                    item {
                        if (state.hasMore || state.loadingMore) {
                            Button(
                                onClick = vm::loadMore,
                                enabled = !state.loadingMore && !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.SurfaceNavy)
                            ) {
                                if (state.loadingMore) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Text("عرض المزيد (${state.streams.size} معروضة)")
                            }
                        }
                    }
                    item {
                        Text(
                            "🕓 الإعادات VOD — تُحفظ تلقائياً عند تفعيل التسجيل من لوحة المذيع",
                            fontSize = 13.sp,
                            color = YounesMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (state.vods.isEmpty()) {
                        item { ExploreEmpty("لا توجد إعادات بعد — فعّل ●REC أثناء البث لحفظ إعادة") }
                    } else {
                        items(state.vods, key = { it.streamId }) { vod ->
                            ExploreCard(
                                title = vod.title.ifBlank { "إعادة بث" },
                                host = vod.broadcasterName.ifBlank { "مذيع" } + " • 👁 ذروة ${vod.peakViewers}",
                                count = vod.category,
                                accent = SovereignColors.Cyan,
                                container = SovereignColors.SurfaceNavy,
                                action = if (vod.vodUrl.isNullOrBlank() && vod.hlsUrl.isNullOrBlank()) "قريباً" else "تشغيل",
                                enabled = !vod.vodUrl.isNullOrBlank() || !vod.hlsUrl.isNullOrBlank()
                            ) {
                                val url = vod.vodUrl ?: vod.hlsUrl ?: return@ExploreCard
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item { Text("🎙️ الغرف الصوتية المفتوحة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SovereignColors.Gold) }
                    if (state.spaces.isEmpty()) item { ExploreEmpty("لا توجد مساحات صوتية نشطة") }
                    items(state.spaces, key = { it.roomId }) { space ->
                        ExploreCard(
                            title = space.title,
                            host = space.hostName.ifBlank { space.hostRedId },
                            count = "${space.participantCount} مشارك",
                            accent = SovereignColors.SpaceAccent,
                            container = SovereignColors.SpaceContainer,
                            action = "دخول",
                            enabled = !state.busy
                        ) {
                            vm.joinSpace(space.roomId) { id -> ConferenceService.join(context, id, ownRedId, false) }
                        }
                    }
                }
            }
        }
    }

    createKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { createKind = null; title = "" },
            title = { Text(if (kind == "LIVE") "بدء بث مباشر" else "إنشاء مساحة صوتية") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("العنوان") },
                    minLines = 2,
                    maxLines = 3
                )
            },
            confirmButton = {
                Button(
                    enabled = title.trim().length >= 3 && !state.busy,
                    onClick = {
                        val clean = title.trim()
                        if (kind == "LIVE") {
                            // بوابة الصلاحيات قبل إنشاء البث — لا شاشة سوداء
                            vm.createLive(clean) { id ->
                                pendingBroadcastId = id
                                broadcastPermissionGate()
                            }
                        } else {
                            vm.createSpace(clean) { id -> ConferenceService.join(context, id, ownRedId, false) }
                        }
                        createKind = null
                        title = ""
                    }
                ) { Text("بدء") }
            },
            dismissButton = { TextButton({ createKind = null; title = "" }) { Text("إلغاء") } }
        )
    }

    // حوار كلمة السر للبث الخاص (روابط مباشرة younes://livestream)
    joinTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { joinTarget = null; joinPassword = "" },
            title = { Text("بث خاص 🔒") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل كلمة سر البث \"${target.title}\"", fontSize = 13.sp)
                    OutlinedTextField(
                        value = joinPassword,
                        onValueChange = { joinPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("كلمة السر") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = joinPassword.length >= 4 && !state.busy,
                    onClick = {
                        val t = target
                        val p = joinPassword
                        joinTarget = null; joinPassword = ""
                        vm.joinLive(t.streamId, p.ifBlank { null }) { id ->
                            LiveStreamService.watch(context, id, ownRedId, p.ifBlank { null })
                        }
                    }
                ) { Text("انضمام") }
            },
            dismissButton = { TextButton({ joinTarget = null; joinPassword = "" }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun HeroLiveBanner(
    title: String,
    host: String,
    viewers: Int,
    category: String,
    recording: Boolean,
    onClick: () -> Unit
) {
    var pulse by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            pulse = !pulse
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(170.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF1A0A2E), Color(0xFF0F172A)))
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(SovereignColors.LiveContainer.copy(alpha = if (pulse) 1f else 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) { Text("🔴 LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black) }
                Text("👁 ${formatLiveViewers(viewers)} • $category" + if (recording) " • ●REC" else "", color = Color.White.copy(0.85f), fontSize = 12.sp)
            }
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("بواسطة $host — الأعلى ترنداً الآن", color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
        }
    }
}

private fun formatLiveViewers(n: Int): String = when {
    n >= 1_000_000 -> "%.1fم".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fك".format(n / 1_000.0)
    else -> "$n"
}

@Composable
private fun ExploreCard(
    title: String,
    host: String,
    count: String,
    /** لون العلامة على السطح الداكن — يُقاس على SurfaceNavy (≥3:1). */
    accent: Color,
    /** لون حاوية الزرّ — يُقاس نصُّه الأبيض عليه (≥4.5:1). */
    container: Color,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White)
                Text("بواسطة $host · $count", fontSize = 12.sp, color = Color.LightGray)
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor = Color.White
                )
            ) {
                Text(action, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ExploreEmpty(message: String) {
    Surface(Modifier.fillMaxWidth(), color = SovereignColors.SurfaceNavy, shape = RoundedCornerShape(14.dp)) {
        // Color.Gray (808080) يعطي 3.70:1 على SurfaceNavy — دون AA.
        // YounesMuted رمز النص الثانوي المعتمد: 6.37:1 على السطح نفسه.
        Text(message, Modifier.padding(18.dp), color = YounesMuted)
    }
}
