package com.red.sovereign.groups

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.media.MediaApi
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GroupViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AuthorizedApiClient(TokenStore(application))
    private val media = MediaApi(application, client)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val groups = mutableStateListOf<Group>()
    val avatars = mutableStateMapOf<String, ImageBitmap>()
    val joinRequests = mutableStateListOf<GroupJoinRequestResponse>()
    var latestInvite: GroupInviteResponse? by mutableStateOf(null); private set
    var state: GroupState by mutableStateOf(GroupState.Loading); private set
    init { load() }

    fun load() = viewModelScope.launch {
        state = GroupState.Loading
        when (val result = client.request("GET", "/api/groups")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<List<Group>>(result.value) }
                .onSuccess { groups.clear(); groups.addAll(it); state = GroupState.Ready }
                .onFailure { state = GroupState.Error("INVALID_GROUP_RESPONSE") }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun create(name: String, description: String?, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("POST", "/api/groups", json.encodeToString(CreateGroupRequest(name, description)))) {
            is ApiResult.Success -> decodeAndStore(result.value, prepend = true, done)
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun addMember(group: Group, redId: String, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("POST", "/api/groups/${group.id}/members", json.encodeToString(AddGroupMemberRequest(redId.trim().uppercase())))) {
            is ApiResult.Success -> decodeAndStore(result.value, done = done)
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun updateRole(group: Group, member: GroupMember, role: String) = viewModelScope.launch {
        require(role == "ADMIN" || role == "MEMBER")
        state = GroupState.Saving
        when (val result = client.request("PATCH", "/api/groups/${group.id}/members/${member.userId}", json.encodeToString(UpdateGroupRoleRequest(role)))) {
            is ApiResult.Success -> decodeAndStore(result.value) {}
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun removeMember(group: Group, member: GroupMember) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("DELETE", "/api/groups/${group.id}/members/${member.userId}")) {
            is ApiResult.Success -> decodeAndStore(result.value) {}
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun loadAvatar(group: Group) {
        val url = group.avatarUrl ?: return
        if (avatars.containsKey(group.id)) return
        viewModelScope.launch {
            when (val response = media.download(url, 10 * 1024 * 1024)) {
                is ApiResult.Success -> BitmapFactory.decodeByteArray(response.value, 0, response.value.size)?.let { avatars[group.id] = it.asImageBitmap() }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun updateAvatar(group: Group, uri: Uri) = viewModelScope.launch {
        state = GroupState.Saving
        when (val uploaded = media.upload(uri)) {
            is ApiResult.Error -> state = GroupState.Error(uploaded.message)
            is ApiResult.Success -> when (val response = client.request("PATCH", "/api/groups/${group.id}/avatar", json.encodeToString(UpdateGroupAvatarRequest(uploaded.value.objectKey)))) {
                is ApiResult.Success -> { avatars.remove(group.id); decodeAndStore(response.value) {} }
                is ApiResult.Error -> { media.delete(uploaded.value.url); state = GroupState.Error(response.message) }
            }
        }
    }

    fun createInvite(group: Group, requireApproval: Boolean = true) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("POST", "/api/groups/${group.id}/invites", json.encodeToString(CreateGroupInviteRequest(requireApproval = requireApproval)))) {
            is ApiResult.Success -> runCatching { json.decodeFromString<GroupInviteResponse>(result.value) }.onSuccess { latestInvite = it; state = GroupState.Ready }.onFailure { state = GroupState.Error("INVALID_INVITE_RESPONSE") }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun joinWithToken(token: String, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("POST", "/api/groups/join-requests", json.encodeToString(JoinGroupRequest(token.trim())))) {
            is ApiResult.Success -> { state = GroupState.Ready; load(); done() }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun loadJoinRequests(group: Group) = viewModelScope.launch {
        when (val result = client.request("GET", "/api/groups/${group.id}/join-requests")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<List<GroupJoinRequestResponse>>(result.value) }.onSuccess { joinRequests.clear(); joinRequests.addAll(it) }.onFailure { state = GroupState.Error("INVALID_JOIN_REQUEST_RESPONSE") }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun resolveJoin(group: Group, request: GroupJoinRequestResponse, approve: Boolean) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("POST", "/api/groups/${group.id}/join-requests/${request.id}", json.encodeToString(ResolveJoinRequest(approve)))) {
            is ApiResult.Success -> { decodeAndStore(result.value) {}; joinRequests.removeAll { it.id == request.id } }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun clearInvite() { latestInvite = null }

    fun transferOwnership(group: Group, member: GroupMember, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("POST", "/api/groups/${group.id}/transfer-ownership", json.encodeToString(TransferGroupOwnershipRequest(member.userId)))) {
            is ApiResult.Success -> decodeAndStore(result.value, done = done)
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun deleteGroup(group: Group, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("DELETE", "/api/groups/${group.id}")) {
            is ApiResult.Success -> { groups.removeAll { it.id == group.id }; state = GroupState.Ready; done() }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun leave(group: Group, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("DELETE", "/api/groups/${group.id}/membership")) {
            is ApiResult.Success -> { groups.removeAll { it.id == group.id }; state = GroupState.Ready; done() }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    private fun decodeAndStore(value: String, prepend: Boolean = false, done: () -> Unit) {
        runCatching { json.decodeFromString<Group>(value) }
            .onSuccess { updated ->
                groups.indexOfFirst { it.id == updated.id }.takeIf { it >= 0 }?.let { groups[it] = updated }
                    ?: if (prepend) groups.add(0, updated) else groups.add(updated)
                state = GroupState.Ready
                done()
            }
            .onFailure { state = GroupState.Error("INVALID_GROUP_RESPONSE") }
    }
}

sealed interface GroupState { data object Loading:GroupState; data object Saving:GroupState; data object Ready:GroupState; data class Error(val message:String):GroupState }
