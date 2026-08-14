package com.red.sovereign.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * تنظيم محلي للدردشات والمجموعات — مجلدات، مفضلة، قفل، نجوم، تثبيت رسائل.
 * لا يغادر الجهاز ولا يلمس مفاتيح التشفير.
 */
class ChatOrganizationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, 0)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var revision by mutableIntStateOf(prefs.getInt(KEY_REVISION, 0))
        private set

    fun folders(): List<ChatFolder> = decodeList(KEY_FOLDERS)

    fun saveFolders(folders: List<ChatFolder>) {
        prefs.edit().putString(KEY_FOLDERS, json.encodeToString(folders.take(12))).apply()
        bump()
    }

    fun createFolder(name: String, chatIds: List<String> = emptyList()): ChatFolder {
        val folder = ChatFolder(
            id = "folder-${System.currentTimeMillis()}",
            name = name.trim().take(32).ifBlank { "مجلد" },
            chatIds = chatIds.distinct()
        )
        saveFolders(folders() + folder)
        return folder
    }

    fun deleteFolder(id: String) = saveFolders(folders().filterNot { it.id == id })

    fun setFolderChats(id: String, chatIds: List<String>) {
        saveFolders(folders().map { if (it.id == id) it.copy(chatIds = chatIds.distinct()) else it })
    }

    fun isFavorite(id: String) = id in stringSet(KEY_FAVORITES)
    fun toggleFavorite(id: String) = toggleSet(KEY_FAVORITES, id)

    fun isLocked(id: String) = id in stringSet(KEY_LOCKED)
    fun setLocked(id: String, locked: Boolean) = mutateSet(KEY_LOCKED, id, locked)

    fun isMarkedUnread(id: String) = id in stringSet(KEY_UNREAD)
    fun markUnread(id: String, unread: Boolean) = mutateSet(KEY_UNREAD, id, unread)

    fun starredIds(): Set<String> = stringSet(KEY_STARRED)
    fun isStarred(messageId: String) = messageId in starredIds()
    fun toggleStarred(messageId: String) = toggleSet(KEY_STARRED, messageId)

    fun pinnedMessageIds(conversationId: String): List<String> =
        pinnedMap()[conversationId].orEmpty()

    fun setPinnedMessages(conversationId: String, ids: List<String>) {
        val next = pinnedMap().toMutableMap()
        if (ids.isEmpty()) next.remove(conversationId) else next[conversationId] = ids.distinct().take(10)
        prefs.edit().putString(KEY_PINS, json.encodeToString(next)).apply()
        bump()
    }

    fun togglePinnedMessage(conversationId: String, messageId: String) {
        val current = pinnedMessageIds(conversationId)
        setPinnedMessages(
            conversationId,
            if (messageId in current) current - messageId else (listOf(messageId) + current).take(10)
        )
    }

    fun disappearingMs(conversationId: String): Long =
        prefs.getLong("$KEY_DISAPPEAR$conversationId", 0L)

    fun setDisappearingMs(conversationId: String, ms: Long) {
        prefs.edit().putLong("$KEY_DISAPPEAR$conversationId", ms).apply()
        bump()
    }

    private fun pinnedMap(): Map<String, List<String>> =
        runCatching {
            json.decodeFromString<Map<String, List<String>>>(prefs.getString(KEY_PINS, "{}") ?: "{}")
        }.getOrDefault(emptyMap())

    private fun decodeList(key: String): List<ChatFolder> =
        runCatching {
            json.decodeFromString<List<ChatFolder>>(prefs.getString(key, "[]") ?: "[]")
        }.getOrDefault(emptyList())

    private fun stringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty()

    private fun toggleSet(key: String, id: String) {
        val next = stringSet(key).toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs.edit().putStringSet(key, next).apply()
        bump()
    }

    private fun mutateSet(key: String, id: String, include: Boolean) {
        val next = stringSet(key).toMutableSet()
        if (include) next.add(id) else next.remove(id)
        prefs.edit().putStringSet(key, next).apply()
        bump()
    }

    private fun bump() {
        val next = revision + 1
        prefs.edit().putInt(KEY_REVISION, next).apply()
        revision = next
    }

    companion object {
        private const val PREFS = "younes_chat_org"
        private const val KEY_FOLDERS = "folders_v1"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_LOCKED = "locked"
        private const val KEY_UNREAD = "marked_unread"
        private const val KEY_STARRED = "starred"
        private const val KEY_PINS = "pinned_messages"
        private const val KEY_DISAPPEAR = "disappear_"
        private const val KEY_REVISION = "revision"
    }
}

@Serializable
data class ChatFolder(
    val id: String,
    val name: String,
    val chatIds: List<String> = emptyList()
)

enum class InboxFilter(val label: String) {
    ALL("الكل"),
    UNREAD("غير مقروء"),
    PINNED("المثبتة"),
    FAVORITES("المفضلة"),
    ARCHIVED("الأرشيف")
}

object InboxQuery {
    fun <T> filter(
        items: List<T>,
        filter: InboxFilter,
        folderChatIds: Set<String>?,
        archived: (T) -> Boolean,
        pinned: (T) -> Boolean,
        favorite: (T) -> Boolean,
        unread: (T) -> Boolean,
        idOf: (T) -> String
    ): List<T> {
        val scoped = when {
            filter == InboxFilter.ARCHIVED -> items.filter(archived)
            else -> items.filterNot(archived)
        }
        val byFolder = if (folderChatIds == null) scoped else scoped.filter { idOf(it) in folderChatIds }
        return when (filter) {
            InboxFilter.ALL, InboxFilter.ARCHIVED -> byFolder
            InboxFilter.UNREAD -> byFolder.filter(unread)
            InboxFilter.PINNED -> byFolder.filter(pinned)
            InboxFilter.FAVORITES -> byFolder.filter(favorite)
        }.sortedWith(compareByDescending<T>(pinned).thenByDescending { unread(it) })
    }
}
