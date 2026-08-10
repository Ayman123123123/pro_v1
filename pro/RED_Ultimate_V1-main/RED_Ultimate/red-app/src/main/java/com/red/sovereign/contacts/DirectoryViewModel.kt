package com.red.sovereign.contacts

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
import com.red.sovereign.core.database.ContactEntity
import com.red.sovereign.core.database.LocalRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

@Serializable data class PublicRedProfile(val redId: String, val username: String, val displayName: String)
@Serializable data class ContactRequest(val id: String, val requester: PublicRedProfile, val createdAt: String)
@Serializable data class ReportRequest(val redId: String, val category: String, val details: String? = null)

class DirectoryViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AuthorizedApiClient(TokenStore(application))
    private val repository = LocalRepository(application)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val results = mutableStateListOf<PublicRedProfile>()
    val contacts = mutableStateListOf<PublicRedProfile>()
    val requests = mutableStateListOf<ContactRequest>()
    val onlineIds = mutableStateListOf<String>()
    var state: DirectoryState by mutableStateOf(DirectoryState.Idle); private set

    init {
        refresh()
        viewModelScope.launch {
            repository.getFriends().collectLatest { entities ->
                contacts.clear()
                contacts.addAll(entities.map { PublicRedProfile(it.redId, it.username, it.displayName) })
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        state = DirectoryState.Loading
        val contactResult = client.request("GET", "/api/contacts")
        val requestResult = client.request("GET", "/api/contacts/requests")
        if (contactResult is ApiResult.Error) { state = DirectoryState.Error(contactResult.message); return@launch }
        if (requestResult is ApiResult.Error) { state = DirectoryState.Error(requestResult.message); return@launch }
        runCatching {
            json.decodeFromString<List<PublicRedProfile>>((contactResult as ApiResult.Success).value) to
                json.decodeFromString<List<ContactRequest>>((requestResult as ApiResult.Success).value)
        }.onSuccess { (people, incoming) ->
            state = DirectoryState.Ready
            repository.saveContacts(people.map { ContactEntity(it.redId, it.username, it.displayName) })
            requests.clear(); requests.addAll(incoming)
            refreshPresence(people)
        }
            .onFailure { state = DirectoryState.Error("INVALID_CONTACT_RESPONSE") }
    }

    fun refreshPresence() = viewModelScope.launch { refreshPresence(contacts) }

    fun isOnline(redId: String): Boolean = redId in onlineIds

    private suspend fun refreshPresence(people: List<PublicRedProfile>) {
        if (people.isEmpty()) { onlineIds.clear(); return }
        val ids = URLEncoder.encode(people.joinToString(",") { it.redId }, "UTF-8")
        when (val response = client.request("GET", "/api/contacts/presence?ids=$ids")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<Map<String, Boolean>>(response.value) }
                .onSuccess { presence -> onlineIds.clear(); onlineIds.addAll(presence.filterValues { it }.keys) }
            is ApiResult.Error -> Unit // Presence is optional; contacts remain usable while offline.
        }
    }

    fun search(query: String) = viewModelScope.launch {
        val term = query.trim()
        if (term.length < 3) { state = DirectoryState.Error("اكتب username كاملًا أو معرّف يونس"); return@launch }
        state = DirectoryState.Loading
        val encoded = URLEncoder.encode(term, "UTF-8")
        when (val response = client.request("GET", "/api/directory/search?query=$encoded")) {
            is ApiResult.Error -> state = DirectoryState.Error(response.message)
            is ApiResult.Success -> runCatching { json.decodeFromString<List<PublicRedProfile>>(response.value) }
                .onSuccess { results.clear(); results.addAll(it); state = DirectoryState.Ready }
                .onFailure { state = DirectoryState.Error("INVALID_DIRECTORY_RESPONSE") }
        }
    }

    fun request(profile: PublicRedProfile) = viewModelScope.launch {
        when (val response = client.request("POST", "/api/contacts/requests/${profile.redId}")) {
            is ApiResult.Success -> state = DirectoryState.Message("تم إرسال طلب صداقة إلى @${profile.username}")
            is ApiResult.Error -> state = DirectoryState.Error(response.message)
        }
    }

    fun resolve(request: ContactRequest, accept: Boolean) = viewModelScope.launch {
        val action = if (accept) "accept" else "reject"
        when (val response = client.request("POST", "/api/contacts/requests/${request.id}/$action")) {
            is ApiResult.Success -> refresh()
            is ApiResult.Error -> state = DirectoryState.Error(response.message)
        }
    }

    fun remove(profile: PublicRedProfile) = viewModelScope.launch {
        when (val response = client.request("DELETE", "/api/contacts/${profile.redId}")) {
            is ApiResult.Success -> { contacts.removeAll { it.redId == profile.redId }; state = DirectoryState.Ready }
            is ApiResult.Error -> state = DirectoryState.Error(response.message)
        }
    }

    fun block(profile: PublicRedProfile) = viewModelScope.launch {
        when (val response = client.request("POST", "/api/contacts/${profile.redId}/block")) {
            is ApiResult.Success -> { contacts.removeAll { it.redId == profile.redId }; state = DirectoryState.Message("تم حظر @${profile.username}") }
            is ApiResult.Error -> state = DirectoryState.Error(response.message)
        }
    }

    fun report(profile: PublicRedProfile, category: String, details: String?) = viewModelScope.launch {
        when (val response = client.request("POST", "/api/contacts/reports", json.encodeToString(ReportRequest(profile.redId, category, details)))) {
            is ApiResult.Success -> state = DirectoryState.Message("تم إرسال البلاغ للمراجعة")
            is ApiResult.Error -> state = DirectoryState.Error(response.message)
        }
    }

    fun clear() { results.clear(); state = DirectoryState.Idle }
}

sealed interface DirectoryState {
    data object Idle : DirectoryState
    data object Loading : DirectoryState
    data object Ready : DirectoryState
    data class Message(val text: String) : DirectoryState
    data class Error(val message: String) : DirectoryState
}
