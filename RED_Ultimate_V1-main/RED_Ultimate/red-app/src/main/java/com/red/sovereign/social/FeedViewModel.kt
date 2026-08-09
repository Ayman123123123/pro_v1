package com.red.sovereign.social

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

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val api = FeedApi(AuthorizedApiClient(TokenStore(application)))
    val posts = mutableStateListOf<Post>()
    val threadPosts = mutableStateListOf<Post>()
    var state: FeedState by mutableStateOf(FeedState.Loading); private set
    var threadState: ThreadState by mutableStateOf(ThreadState.Idle); private set
    var scope: String? = null; private set

    init { load(null) }

    fun load(newScope: String?) = viewModelScope.launch {
        scope = newScope; state = FeedState.Loading
        when (val result = api.load(newScope)) {
            is ApiResult.Success -> { posts.clear(); posts.addAll(result.value.posts); state = FeedState.Ready }
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun create(text: String, done: () -> Unit) = viewModelScope.launch {
        state = FeedState.Publishing
        when (val result = api.create(text)) {
            is ApiResult.Success -> { posts.add(0, result.value); state = FeedState.Ready; done() }
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun follow(post: Post) = viewModelScope.launch {
        when (val result = api.follow(post.authorRedId)) {
            is ApiResult.Success -> state = FeedState.Message("تمت متابعة @${post.authorUsername}")
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun toggleLike(post: Post) = viewModelScope.launch {
        // The first feed version exposes aggregate counts only. Adding a LIKE is idempotent
        // per account on the server; viewer-specific unlike state comes with the social graph.
        when (val result = api.react(post.id, "LIKE", true)) {
            is ApiResult.Success -> replace(result.value)
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun vote(post: Post, optionId: String) = viewModelScope.launch {
        when (val result = api.vote(post.id, optionId)) {
            is ApiResult.Success -> replace(result.value)
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun loadThread(post: Post) = viewModelScope.launch {
        threadState = ThreadState.Loading
        when (val result = api.thread(post.id)) {
            is ApiResult.Success -> { threadPosts.clear(); threadPosts.addAll(result.value); threadState = ThreadState.Ready }
            is ApiResult.Error -> threadState = ThreadState.Error(result.message)
        }
    }

    fun reply(post: Post, text: String, done: () -> Unit) = viewModelScope.launch {
        threadState = ThreadState.Publishing
        when (val result = api.create(CreatePostRequest(text.trim(), post.visibility, parentId = post.id))) {
            is ApiResult.Success -> {
                threadPosts.add(result.value)
                posts.indexOfFirst { it.id == post.id }.takeIf { it >= 0 }?.let { index -> posts[index] = posts[index].copy(replyCount = posts[index].replyCount + 1) }
                threadState = ThreadState.Ready
                done()
            }
            is ApiResult.Error -> threadState = ThreadState.Error(result.message)
        }
    }

    fun quote(post: Post, text: String, done: () -> Unit) = viewModelScope.launch {
        state = FeedState.Publishing
        when (val result = api.create(CreatePostRequest(text.trim(), post.visibility, quotePostId = post.id))) {
            is ApiResult.Success -> { posts.add(0, result.value); state = FeedState.Ready; done() }
            is ApiResult.Error -> state = FeedState.Error(result.message)
        }
    }

    fun closeThread() { threadPosts.clear(); threadState = ThreadState.Idle }

    private fun replace(post: Post) {
        posts.indexOfFirst { it.id == post.id }.takeIf { it >= 0 }?.let { posts[it] = post }
        state = FeedState.Ready
    }
}

sealed interface ThreadState {
    data object Idle : ThreadState
    data object Loading : ThreadState
    data object Publishing : ThreadState
    data object Ready : ThreadState
    data class Error(val message: String) : ThreadState
}

sealed interface FeedState { data object Loading: FeedState; data object Ready: FeedState; data object Publishing: FeedState; data class Message(val text:String): FeedState; data class Error(val message:String): FeedState }
