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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class CallHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AuthorizedApiClient(TokenStore(application))
    private val json = Json { ignoreUnknownKeys = true }
    val calls = mutableStateListOf<CallHistoryItem>()
    var loading by mutableStateOf(false); private set
    var error: String? by mutableStateOf(null); private set

    init { load() }

    fun load() = viewModelScope.launch {
        loading = true; error = null
        when (val result = client.request("GET", "/api/calls/history?limit=100")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<List<CallHistoryItem>>(result.value) }
                .onSuccess { calls.clear(); calls.addAll(it) }.onFailure { error = "INVALID_CALL_HISTORY" }
            is ApiResult.Error -> error = result.message
        }
        loading = false
    }
}
