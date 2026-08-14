package com.red.sovereign.stories

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.database.LocalRepository
import com.red.sovereign.core.database.StoryEntity
import com.red.sovereign.media.MediaApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private fun storyTimestamp(value: String): Long =
    value.toLongOrNull() ?: runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull() ?: 0L

class StoryViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AuthorizedApiClient(TokenStore(application))
    private val media = MediaApi(application, client)
    private val repository = LocalRepository(application)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val stories = mutableStateListOf<Story>()
    var state: StoryState by mutableStateOf(StoryState.Idle); private set
    var viewer: StoryViewerState by mutableStateOf(StoryViewerState.Closed); private set

    init {
        load()
        viewModelScope.launch {
            repository.getActiveStories().collectLatest { entities ->
                stories.clear()
                stories.addAll(entities.map { entity ->
                    Story(
                        id = entity.id,
                        ownerRedId = entity.userId,
                        ownerUsername = "user",
                        ownerDisplayName = "Owner",
                        mediaUrl = entity.mediaUrl,
                        mediaType = entity.mediaType,
                        caption = entity.caption,
                        createdAt = entity.timestamp.toString(),
                        expiresAt = entity.expiresAt.toString()
                    )
                })
            }
        }
    }

    fun load() = viewModelScope.launch {
        state = StoryState.Loading
        when (val result = client.request("GET", "/api/stories")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<List<Story>>(result.value) }
                .onSuccess { list ->
                    state = StoryState.Idle
                    repository.saveStories(list.map { 
                        StoryEntity(it.id, it.ownerRedId, it.mediaUrl, it.mediaType, it.caption, storyTimestamp(it.createdAt), storyTimestamp(it.createdAt) + 86400000)
                    })
                }
                .onFailure { state = StoryState.Error("INVALID_STORY_RESPONSE") }
            is ApiResult.Error -> state = StoryState.Error(result.message)
        }
    }

    fun upload(uri: Uri, caption: String? = null, visibleTo: String = "EVERYONE", mediaType: String? = null) = viewModelScope.launch {
        state = StoryState.Uploading
        // Professional: compress before upload (handled by MediaCompressor)
        when (val uploaded = media.upload(uri)) {
            is ApiResult.Error -> state = StoryState.Error(uploaded.message)
            is ApiResult.Success -> when (val created = client.request("POST", "/api/stories", json.encodeToString(CreateStoryRequest(uploaded.value.objectKey, caption, visibleTo, mediaType = mediaType ?: uploaded.value.mimeType)))) {
                is ApiResult.Success -> runCatching { json.decodeFromString<Story>(created.value) }
                    .onSuccess { stories.add(0, it); state = StoryState.Idle }
                    .onFailure { state = StoryState.Error("INVALID_STORY_RESPONSE") }
                is ApiResult.Error -> state = StoryState.Error(created.message)
            }
        }
    }


    fun createTextStory(text: String, backgroundColor: String = "#1565C0", visibleTo: String = "EVERYONE") = viewModelScope.launch {
        if (text.isBlank() || text.length > 500) { state = StoryState.Error("النص يجب أن يكون 1..500 حرف"); return@launch }
        state = StoryState.Uploading
        // Text stories don't need media upload — send text directly as caption with TEXT type
        when (val created = client.request("POST", "/api/stories", json.encodeToString(CreateStoryRequest("text://${text.hashCode()}", text, visibleTo, mediaType = "TEXT", backgroundColor = backgroundColor)))) {
            is ApiResult.Success -> runCatching { json.decodeFromString<Story>(created.value) }.onSuccess { stories.add(0, it); state = StoryState.Idle }
            is ApiResult.Error -> state = StoryState.Error(created.message)
        }
    }

    fun createVoiceStory(uri: Uri, durationMs: Long, waveform: List<Int>, visibleTo: String = "EVERYONE") = viewModelScope.launch {
        state = StoryState.Uploading
        when (val uploaded = media.upload(uri)) {
            is ApiResult.Error -> state = StoryState.Error(uploaded.message)
            is ApiResult.Success -> when (val created = client.request("POST", "/api/stories", json.encodeToString(CreateStoryRequest(uploaded.value.objectKey, null, visibleTo, mediaType = "VOICE", durationMs = durationMs)))) {
                is ApiResult.Success -> runCatching { json.decodeFromString<Story>(created.value) }.onSuccess { stories.add(0, it); state = StoryState.Idle }
                is ApiResult.Error -> state = StoryState.Error(created.message)
            }
        }
    }

    fun open(story: Story) = viewModelScope.launch {
        viewed(story)
        viewer = StoryViewerState.Loading(story)
        when {
            story.isText() -> {
                // TEXT stories — no download needed, show directly
                viewer = StoryViewerState.Text(story)
            }
            story.isVoice() -> when (val result = media.downloadToPrivateCache(story.mediaUrl, "ogg")) {
                is ApiResult.Error -> viewer = StoryViewerState.Error(story, result.message)
                is ApiResult.Success -> viewer = StoryViewerState.Voice(story, result.value.toUri())
            }
            story.mediaType.startsWith("image/", ignoreCase = true) -> when (val result = media.download(story.mediaUrl)) {
                is ApiResult.Error -> viewer = StoryViewerState.Error(story, result.message)
                is ApiResult.Success -> {
                    val bitmap = BitmapFactory.decodeByteArray(result.value, 0, result.value.size)
                    viewer = if (bitmap == null) StoryViewerState.Error(story, "INVALID_IMAGE")
                    else StoryViewerState.Image(story, bitmap.asImageBitmap())
                }
            }
            story.mediaType.equals("video/mp4", true) || story.mediaType.equals("video/webm", true) -> {
                val extension = if (story.mediaType.equals("video/webm", true)) "webm" else "mp4"
                when (val result = media.downloadToPrivateCache(story.mediaUrl, extension)) {
                    is ApiResult.Error -> viewer = StoryViewerState.Error(story, result.message)
                    is ApiResult.Success -> viewer = StoryViewerState.Video(story, result.value.toUri())
                }
            }
            else -> viewer = StoryViewerState.Unsupported(story, "نوع الوسائط غير مدعوم في عارض الحالات")
        }
    }

    fun closeViewer() { viewer = StoryViewerState.Closed }

    override fun onCleared() {
        media.clearPrivateCache()
        super.onCleared()
    }

    fun react(story: Story, emoji: String) = viewModelScope.launch {
        client.request("POST", "/api/stories/${story.id}/react", json.encodeToString(StoryReactionRequest(emoji)))
    }

    fun delete(story: Story) = viewModelScope.launch {
        when (val result = client.request("DELETE", "/api/stories/${story.id}")) {
            is ApiResult.Success -> stories.remove(story)
            is ApiResult.Error -> state = StoryState.Error(result.message)
        }
    }

    fun viewed(story: Story) = viewModelScope.launch {
        client.request("POST", "/api/stories/${story.id}/view")
    }
}

sealed interface StoryViewerState {
    data object Closed : StoryViewerState
    data class Loading(val story: Story) : StoryViewerState
    data class Image(val story: Story, val image: ImageBitmap) : StoryViewerState
    data class Video(val story: Story, val uri: Uri) : StoryViewerState
    data class Text(val story: Story) : StoryViewerState
    data class Voice(val story: Story, val uri: Uri) : StoryViewerState
    data class Unsupported(val story: Story, val message: String) : StoryViewerState
    data class Error(val story: Story, val message: String) : StoryViewerState
}

sealed interface StoryState { data object Idle: StoryState; data object Loading: StoryState; data object Uploading: StoryState; data class Error(val message:String): StoryState }
