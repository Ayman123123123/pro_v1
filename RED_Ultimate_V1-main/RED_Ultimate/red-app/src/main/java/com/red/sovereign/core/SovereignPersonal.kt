package com.red.sovereign.core

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun selfConversationId(myRedId: String): String = "self:${myRedId.trim().uppercase()}"
fun isSelfConversation(conversationId: String, myRedId: String): Boolean =
    conversationId == selfConversationId(myRedId)

@Serializable
data class PersonalChatFolder(
    val id: String,
    val name: String,
    val peerIds: List<String> = emptyList(),
    val locked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

object PersonalChatFoldersStore {
    private const val PREFS = "younes_chat_folders"
    private const val KEY = "folders_json"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): MutableList<PersonalChatFolder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching { json.decodeFromString<List<PersonalChatFolder>>(raw).toMutableList() }.getOrDefault(mutableListOf())
    }

    fun save(context: Context, folders: List<PersonalChatFolder>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(folders)).apply()
    }

    fun create(context: Context, name: String): PersonalChatFolder? {
        val clean = name.trim().take(32)
        if (clean.length < 2) return null
        val list = load(context)
        if (list.size >= 20) return null
        if (list.any { it.name.equals(clean, ignoreCase = true) }) return null
        val f = PersonalChatFolder(id = "f-${System.currentTimeMillis()}", name = clean)
        list.add(0, f); save(context, list)
        return f
    }

    fun delete(context: Context, id: String) {
        val list = load(context); list.removeAll { it.id == id }; save(context, list)
    }

    fun togglePeer(context: Context, folderId: String, peerId: String) {
        val list = load(context)
        val i = list.indexOfFirst { it.id == folderId }; if (i < 0) return
        val f = list[i]
        val peers = f.peerIds.toMutableList()
        if (peers.contains(peerId)) peers.remove(peerId) else peers.add(peerId)
        list[i] = f.copy(peerIds = peers.take(200))
        save(context, list)
    }

    fun setLocked(context: Context, folderId: String, locked: Boolean) {
        val list = load(context)
        val i = list.indexOfFirst { it.id == folderId }; if (i < 0) return
        list[i] = list[i].copy(locked = locked)
        save(context, list)
    }

    fun filterPeerIds(folders: List<PersonalChatFolder>, selectedFolderId: String?, unlockedIds: Set<String>): Set<String>? {
        if (selectedFolderId == null) return null
        val f = folders.firstOrNull { it.id == selectedFolderId } ?: return emptySet()
        if (f.locked && f.id !in unlockedIds) return emptySet()
        return f.peerIds.toSet()
    }
}

object UnlockedFolders {
    val ids = mutableStateListOf<String>()
    fun unlock(id: String) { if (!ids.contains(id)) ids.add(id) }
    fun isUnlocked(id: String): Boolean = ids.contains(id)
}

object StoryReplyHelper {
    suspend fun fetchTarget(context: Context, storyId: String): com.red.sovereign.auth.ApiResult<String> {
        val tokens = com.red.sovereign.auth.TokenStore(context)
        val api = com.red.sovereign.auth.AuthorizedApiClient(tokens)
        val encoded = java.net.URLEncoder.encode(storyId, "UTF-8")
        return api.request("GET", "/api/stories/$encoded/reply-target")
    }

    fun replyPrefix(storyId: String): String = "[رد على حالة $storyId] "
}
