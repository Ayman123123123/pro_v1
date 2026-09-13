package com.red.sovereign.features.communities.simple

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P1-B — شاشة مجتمعات بسيطة: قائمة + انضمام/مغادرة.
 *
 * حزمة `simple` — لا تمس شاشة `CommunitiesScreen` الكاملة في المجلد الأب.
 * (اسم الدالة مطابق للمهمة؛ الحزمة المختلفة تمنع أي تعارض.)
 */
data class SimpleCommunitiesUiState(
    val loading: Boolean = false,
    val communities: List<Community> = emptyList(),
    val error: String? = null,
    val query: String = ""
)

class SimpleCommunitiesViewModel(private val api: CommunityApi) : ViewModel() {
    private val _state = MutableStateFlow(SimpleCommunitiesUiState())
    val state: StateFlow<SimpleCommunitiesUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = it.communities.isEmpty(), error = null) }
        viewModelScope.launch { loadList(_state.value.query) }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
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
            is ApiResult.Success -> _state.update { current ->
                current.copy(
                    communities = current.communities.map {
                        if (it.id == community.id) result.value else it
                    },
                    error = null
                )
            }
            is ApiResult.Error -> _state.update { it.copy(error = result.message) }
        }
    }

    fun leave(community: Community) = viewModelScope.launch {
        when (api.leave(community.id)) {
            is ApiResult.Success -> refresh()
            is ApiResult.Error -> _state.update { it.copy(error = "فشل المغادرة") }
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
    val api = remember(tokens) { CommunityApi(AuthorizedApiClient(tokens)) }
    val vm: SimpleCommunitiesViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SimpleCommunitiesViewModel(api) as T
        }
    )
    val state by vm.state.collectAsState()
    val muted = Color(0xFF7A8590)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("المجتمعات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("بحث المجتمعات...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            state.error?.let { err ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = vm::clearError) {
                        Icon(Icons.Default.Clear, contentDescription = "إغلاق")
                    }
                }
            }

            when {
                state.loading && state.communities.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("جاري التحميل...", color = muted, fontSize = 14.sp)
                    }
                }
                state.communities.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.query.isNotBlank()) "لا توجد نتائج" else "لا توجد مجتمعات بعد",
                        color = muted,
                        fontSize = 15.sp
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.communities, key = { it.id }) { community ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpenCommunity(community.id, community.name) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        community.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    community.description?.let { desc ->
                                        Text(
                                            desc,
                                            color = muted,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Group,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "${community.memberCount} عضو",
                                            fontSize = 11.sp,
                                            color = muted
                                        )
                                    }
                                }
                                if (community.isJoined) {
                                    OutlinedButton(onClick = { vm.leave(community) }) {
                                        Text("مغادرة", fontSize = 12.sp)
                                    }
                                } else {
                                    Button(onClick = { vm.join(community) }) {
                                        Text("انضم", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
