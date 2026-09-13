package com.red.sovereign.social

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * ════════════════════════════════════════════════════════════════════════
 *  BookmarksStore — تخزين المنشورات المحفوظة محلياً (Save / Bookmark)
 * ════════════════════════════════════════════════════════════════════════
 */
class BookmarksStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val file = File(context.filesDir, "saved_bookmarks.json")

    private val _bookmarkedPosts = MutableStateFlow<List<Post>>(emptyList())
    val bookmarkedPosts: StateFlow<List<Post>> = _bookmarkedPosts.asStateFlow()

    init {
        scope.launch { loadFromDisk() }
    }

    private suspend fun loadFromDisk() {
        mutex.withLock {
            try {
                if (file.exists()) {
                    val content = file.readText()
                    if (content.isNotBlank()) {
                        val list = json.decodeFromString<List<Post>>(content)
                        _bookmarkedPosts.value = list
                    }
                }
            } catch (e: Exception) {
                Log.e("BookmarksStore", "Failed to load bookmarks: ${e.message}", e)
            }
        }
    }

    private suspend fun saveToDisk(list: List<Post>) {
        try {
            file.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            Log.e("BookmarksStore", "Failed to save bookmarks: ${e.message}", e)
        }
    }

    fun isBookmarked(postId: String): Boolean = _bookmarkedPosts.value.any { it.id == postId }

    fun toggle(post: Post): Boolean {
        var nowBookmarked = false
        scope.launch {
            mutex.withLock {
                val current = _bookmarkedPosts.value.toMutableList()
                val idx = current.indexOfFirst { it.id == post.id }
                if (idx >= 0) {
                    current.removeAt(idx)
                    nowBookmarked = false
                } else {
                    current.add(0, post)
                    nowBookmarked = true
                }
                _bookmarkedPosts.value = current
                saveToDisk(current)
            }
        }
        return !_bookmarkedPosts.value.any { it.id == post.id }
    }

    fun clear() {
        scope.launch {
            mutex.withLock {
                _bookmarkedPosts.value = emptyList()
                if (file.exists()) file.delete()
            }
        }
    }
}
