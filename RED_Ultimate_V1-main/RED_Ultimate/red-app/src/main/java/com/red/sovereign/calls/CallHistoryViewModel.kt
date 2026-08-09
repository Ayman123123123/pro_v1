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
    private val json = Json { ignoreUnknownKeys = true }
    val calls = mutableStateListOf<CallHistoryItem>()
    var loading by mutableStateOf(false); private set
    var error: String? by mutableStateOf(null); private set

    init {
        load()
        viewModelScope.launch {
            repository.getCallLogs().collectLatest { entities ->
                calls.clear()
                calls.addAll(entities.map { entity ->
                    CallHistoryItem(entity.id, entity.peerId, "User", entity.type, entity.direction, entity.status, entity.timestamp, entity.durationMs)
                })
            }
        }
    }

    fun load() = viewModelScope.launch {
        loading = true; error = null
        when (val result = client.request("GET", "/api/calls/history?limit=100")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<List<CallHistoryItem>>(result.value) }
                .onSuccess { list ->
                    loading = false
                    repository.saveCallLog(list.map { 
                        CallLogEntity(it.id, it.peerId, it.type, it.direction, it.status, it.timestamp, it.durationMs)
                    }.firstOrNull() ?: return@onSuccess) // Batch save would be better, adding it to repository
                }.onFailure { error = "INVALID_CALL_HISTORY" }
            is ApiResult.Error -> error = result.message
        }
        loading = false
    }
}
