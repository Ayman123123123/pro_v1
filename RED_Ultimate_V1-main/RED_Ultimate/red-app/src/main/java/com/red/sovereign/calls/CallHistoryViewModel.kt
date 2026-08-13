package com.red.sovereign.calls

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.database.CallLogEntity
import com.red.sovereign.core.database.LocalRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class CallHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AuthorizedApiClient(TokenStore(application))
    private val repository = LocalRepository(application)
    private val cipher = CallLogCipher()
    private val json = Json { ignoreUnknownKeys = true }
    val calls = mutableStateListOf<CallHistoryItem>()
    var loading by mutableStateOf(false); private set
    var error: String? by mutableStateOf(null); private set

    init {
        viewModelScope.launch {
            repository.getCallLogs().collectLatest { entities ->
                calls.clear()
                // نُفك تشفير peerId/label للعرض في الواجهة فقط — DB تبقى مشفرة
                calls.addAll(entities.map { it.toCallHistoryItem() })
            }
        }
        load()
    }

    fun load() = viewModelScope.launch {
        loading = true; error = null
        when (val result = client.request("GET", "/api/calls/history?limit=100")) {
            is ApiResult.Success -> runCatching {
                json.decodeFromString<List<CallHistoryItem>>(result.value)
            }.onSuccess { list ->
                if (list.isNotEmpty()) repository.saveCallLogs(list.map { it.toCallLogEntity() })
            }.onFailure { error = "INVALID_CALL_HISTORY: ${it.message}" }
            is ApiResult.Error -> error = result.message
        }
        loading = false
    }

    private fun CallLogEntity.toCallHistoryItem(): CallHistoryItem = CallHistoryItem(
        id = id,
        peerId = cipher.decryptPeerId(peerId),
        peerLabel = cipher.decryptLabel(peerLabel),
        direction = direction,
        type = type,
        route = route,
        status = status,
        startedAt = timestamp.toString(),
        answeredAt = answeredAt?.toString(),
        endedAt = endedAt?.toString()
    )

    private fun CallHistoryItem.toCallLogEntity(): CallLogEntity = CallLogEntity(
        id = id,
        peerId = cipher.encryptPeerId(peerId),
        peerLabel = cipher.encryptLabel(peerLabel),
        type = type.toString(),
        direction = direction,
        route = route.toString(),
        status = status.toString(),
        timestamp = parseCallTimestamp(startedAt) ?: 0L,
        answeredAt = parseCallTimestamp(answeredAt),
        endedAt = parseCallTimestamp(endedAt)
    )
}
