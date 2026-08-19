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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.red.sovereign.ui.theme.SovereignColors
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
    val inviteLink: String = ""
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
private data class CreateStreamBody(val title: String, val isPrivate: Boolean = false, val password: String? = null)

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

    suspend fun streams(query: String): ApiResult<List<ExploreLiveStream>> = decodeList(
        client.request("GET", "/api/livestream/public?query=${encode(query)}")
    )

    suspend fun spaces(query: String): ApiResult<List<ExploreSpace>> = decodeList(
        client.request("GET", "/api/conference/public?isSpace=true&query=${encode(query)}")
    )

    suspend fun createStream(title: String): ApiResult<ExploreLiveStream> = decode(
        client.request("POST", "/api/livestream/create", json.encodeToString(CreateStreamBody(title.trim())))
    )

    suspend fun createSpace(title: String): ApiResult<ExploreSpace> = decode(
        client.request("POST", "/api/conference/create", json.encodeToString(CreateSpaceBody(title = title.trim())))
    )

    suspend fun authorizeStream(streamId: String): ApiResult<String> = client.request(
        "POST", "/api/livestream/$streamId/join", json.encodeToString(JoinBody())
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
    val streams: List<ExploreLiveStream> = emptyList(),
    val spaces: List<ExploreSpace> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null
)

private class ExploreViewModel(private val api: ExploreApi) : ViewModel() {
    private val _state = MutableStateFlow(ExploreState())
    val state: StateFlow<ExploreState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init { refresh() }

    fun query(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refresh()
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        val query = _state.value.query
        val streams = api.streams(query)
        val spaces = api.spaces(query)
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
        _state.update {
            it.copy(
                loading = false,
                streams = streamItems,
                spaces = spaceItems,
                error = null
            )
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

    fun joinLive(streamId: String, joined: (String) -> Unit) = operation {
        when (val result = api.authorizeStream(streamId)) {
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

    Scaffold(
        containerColor = SovereignColors.Obsidian,
        topBar = {
            TopAppBar(
                title = { Text("الاستكشاف والسيادة", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "رجوع") } },
                actions = { IconButton(vm::refresh) { Icon(Icons.Default.Refresh, "تحديث") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { createKind = "LIVE" },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.LiveRed),
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.LiveTv, null); Text(" بدء بث") }
                Button(
                    onClick = { createKind = "SPACE" },
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.SpacePurple),
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
                    if (state.streams.isEmpty()) item { ExploreEmpty("لا توجد بثوث عامة نشطة") }
                    items(state.streams, key = { it.streamId }) { stream ->
                        ExploreCard(
                            title = stream.title,
                            host = stream.broadcasterName.ifBlank { stream.broadcasterRedId },
                            count = "${stream.viewerCount} مشاهد",
                            color = SovereignColors.LiveRed,
                            action = "مشاهدة",
                            enabled = !state.busy
                        ) {
                            vm.joinLive(stream.streamId) { id -> LiveStreamService.start(context, id, ownRedId, false) }
                        }
                    }
                    item { Text("🎙️ الغرف الصوتية المفتوحة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SovereignColors.Gold) }
                    if (state.spaces.isEmpty()) item { ExploreEmpty("لا توجد مساحات صوتية نشطة") }
                    items(state.spaces, key = { it.roomId }) { space ->
                        ExploreCard(
                            title = space.title,
                            host = space.hostName.ifBlank { space.hostRedId },
                            count = "${space.participantCount} مشارك",
                            color = SovereignColors.SpacePurple,
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
                            vm.createLive(clean) { id -> LiveStreamService.start(context, id, ownRedId, true) }
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
}

@Composable
private fun ExploreCard(
    title: String,
    host: String,
    count: String,
    color: Color,
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
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White)
                Text("بواسطة $host · $count", fontSize = 12.sp, color = Color.LightGray)
            }
            Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = color)) {
                Text(action, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ExploreEmpty(message: String) {
    Surface(Modifier.fillMaxWidth(), color = SovereignColors.SurfaceNavy, shape = RoundedCornerShape(14.dp)) {
        Text(message, Modifier.padding(18.dp), color = Color.Gray)
    }
}
