package com.red.sovereign.features.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChannelViewModel(private val api: ChannelsApi) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState.asStateFlow()

    private val _recommendations = MutableStateFlow<List<ChannelRecommendation>>(emptyList())
    val recommendations: StateFlow<List<ChannelRecommendation>> = _recommendations.asStateFlow()

    private val _trending = MutableStateFlow<List<Channel>>(emptyList())
    val trending: StateFlow<List<Channel>> = _trending.asStateFlow()

    private val _nearby = MutableStateFlow<List<NearbyChannel>>(emptyList())
    val nearby: StateFlow<List<NearbyChannel>> = _nearby.asStateFlow()

    private val _searchResults = MutableStateFlow<ChannelSearchResult>(ChannelSearchResult())
    val searchResults: StateFlow<ChannelSearchResult> = _searchResults.asStateFlow()

    private val _analytics = MutableStateFlow<ChannelAnalytics?>(null)
    val analytics: StateFlow<ChannelAnalytics?> = _analytics.asStateFlow()

    private val _moderationQueue = MutableStateFlow<ModerationQueueResult>(ModerationQueueResult(emptyList(), 0, false))
    val moderationQueue: StateFlow<ModerationQueueResult> = _moderationQueue.asStateFlow()

    private val _bannedUsers = MutableStateFlow<List<BannedUser>>(emptyList())
    val bannedUsers: StateFlow<List<BannedUser>> = _bannedUsers.asStateFlow()

    private val _media = MutableStateFlow<MediaResult>(MediaResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0))
    val media: StateFlow<MediaResult> = _media.asStateFlow()

    private val _messages = MutableStateFlow<MessagesResult>(MessagesResult(emptyList(), false))
    val messages: StateFlow<MessagesResult> = _messages.asStateFlow()

    private val _members = MutableStateFlow<MembersResult>(MembersResult(emptyList(), false))
    val members: StateFlow<MembersResult> = _members.asStateFlow()

    private val _roles = MutableStateFlow<List<ChannelRole>>(emptyList())
    val roles: StateFlow<List<ChannelRole>> = _roles.asStateFlow()

    private val _events = MutableStateFlow<List<CommunityEvent>>(emptyList())
    val events: StateFlow<List<CommunityEvent>> = _events.asStateFlow()

    private val _scheduledMessages = MutableStateFlow<List<ChannelMessage>>(emptyList())
    val scheduledMessages: StateFlow<List<ChannelMessage>> = _scheduledMessages.asStateFlow()

    private val _invites = MutableStateFlow<List<InviteLink>>(emptyList())
    val invites: StateFlow<List<InviteLink>> = _invites.asStateFlow()

    private val _typingUsers = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 10)
    val typingUsers: kotlinx.coroutines.flow.SharedFlow<TypingEvent> = _typingUsers

    private val _newMessage = MutableSharedFlow<ChannelMessage>(extraBufferCapacity = 50)
    val newMessage: kotlinx.coroutines.flow.SharedFlow<ChannelMessage> = _newMessage

    private val _messageUpdate = MutableSharedFlow<MessageUpdate>(extraBufferCapacity = 50)
    val messageUpdate: kotlinx.coroutines.flow.SharedFlow<MessageUpdate> = _messageUpdate

    private val _reactionUpdate = MutableSharedFlow<ReactionUpdate>(extraBufferCapacity = 50)
    val reactionUpdate: kotlinx.coroutines.flow.SharedFlow<ReactionUpdate> = _reactionUpdate

    private var searchJob: Job? = null
    private var paginationJob: Job? = null
    private var currentChannelId: String? = null
    private var messagesCursor: String? = null

    init {
        loadRecommendations()
        loadTrending()
    }

    fun loadRecommendations() = viewModelScope.launch {
        when (val result = api.getRecommendations(20)) {
            is ApiResult.Success -> _recommendations.update { result.value }
            is ApiResult.Error -> {}
        }
    }

    fun loadTrending() = viewModelScope.launch {
        when (val result = api.getTrending(20)) {
            is ApiResult.Success -> _trending.update { result.value }
            is ApiResult.Error -> {}
        }
    }

    fun loadNearby(lat: Double, lng: Double, radiusKm: Int = 50) = viewModelScope.launch {
        when (val result = api.getNearby(lat, lng, radiusKm, 20)) {
            is ApiResult.Success -> _nearby.update { result.value }
            is ApiResult.Error -> {}
        }
    }

    fun refresh(query: String = "", category: String? = null, tags: List<String> = emptyList()) {
        _uiState.update { it.copy(loading = it.channels.isEmpty(), error = null, query = query) }
        viewModelScope.launch {
            loadChannels(query, category, tags)
        }
    }

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            search(q)
        }
    }

    fun search(query: String, category: String? = null) {
        if (query.trim().length < 2) {
            _searchResults.update { ChannelSearchResult() }
            return
        }
        viewModelScope.launch {
            when (val result = api.search(query, category)) {
                is ApiResult.Success -> _searchResults.update { result.value }
                is ApiResult.Error -> {}
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loadingMore || !state.hasMore || state.nextCursor == null) return
        _uiState.update { it.copy(loadingMore = true) }
        paginationJob?.cancel()
        paginationJob = viewModelScope.launch {
            when (val result = api.list(
                search = state.query.takeIf { it.isNotBlank() },
                category = state.selectedCategory,
                tags = state.selectedTags,
                sort = state.sortOrder,
                cursor = state.nextCursor,
            )) {
                is ApiResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            loadingMore = false,
                            channels = current.channels + result.value.channels,
                            hasMore = result.value.hasMore,
                            nextCursor = result.value.nextCursor,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(loadingMore = false, error = result.message) }
                }
            }
        }
    }

    private suspend fun loadChannels(query: String, category: String? = null, tags: List<String> = emptyList()) {
        when (val result = api.list(
            search = query.takeIf { it.isNotBlank() },
            category = category,
            tags = tags,
            sort = _uiState.value.sortOrder,
        )) {
            is ApiResult.Success -> {
                _uiState.update {
                    it.copy(
                        loading = false,
                        channels = result.value.channels,
                        hasMore = result.value.hasMore,
                        nextCursor = result.value.nextCursor,
                        totalCount = result.value.totalCount,
                        error = null,
                    )
                }
            }
            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(loading = false, error = result.message)
                }
            }
        }
    }

    fun setCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category, channels = emptyList(), nextCursor = null) }
        refresh(_uiState.value.query, category, _uiState.value.selectedTags)
    }

    fun toggleTag(tag: String) {
        val current = _uiState.value.selectedTags
        val updated = if (tag in current) current - tag else current + tag
        _uiState.update { it.copy(selectedTags = updated, channels = emptyList(), nextCursor = null) }
        refresh(_uiState.value.query, _uiState.value.selectedCategory, updated)
    }

    fun setSortOrder(sort: String) {
        _uiState.update { it.copy(sortOrder = sort, channels = emptyList(), nextCursor = null) }
        refresh(_uiState.value.query, _uiState.value.selectedCategory, _uiState.value.selectedTags)
    }

    fun openChannel(channelId: String) {
        currentChannelId = channelId
        messagesCursor = null
        loadChannelDetails(channelId)
        loadMessages(channelId)
        loadMedia(channelId)
        loadMembers(channelId)
        loadRoles(channelId)
        loadEvents(channelId)
        loadScheduledMessages(channelId)
        loadInvites(channelId)
        if (isAdmin(channelId)) {
            loadAnalytics(channelId)
            loadModerationQueue(channelId)
            loadBannedUsers(channelId)
        }
    }

    private suspend fun loadChannelDetails(channelId: String) {
        when (val result = api.details(channelId)) {
            is ApiResult.Success -> {
                _uiState.update { it.copy(selectedChannel = result.value) }
            }
            is ApiResult.Error -> {}
        }
    }

    fun loadMessages(channelId: String, beforeId: String? = null, afterId: String? = null, threadId: String? = null) {
        viewModelScope.launch {
            when (val result = api.getMessages(channelId, beforeId, afterId, 50, threadId)) {
                is ApiResult.Success -> {
                    if (beforeId != null) {
                        _messages.update { it.copy(messages = it.messages + result.value.messages, hasMore = result.value.hasMore) }
                    } else if (afterId != null) {
                        _messages.update { it.copy(messages = result.value.messages + it.messages, hasMore = result.value.hasMore) }
                    } else {
                        _messages.update { result.value }
                        messagesCursor = result.value.messages.lastOrNull()?.id
                    }
                }
                is ApiResult.Error -> {}
            }
        }
    }

    fun loadMoreMessages(channelId: String) {
        val lastId = _messages.value.messages.lastOrNull()?.id
        if (lastId != null) {
            loadMessages(channelId, beforeId = lastId)
        }
    }

    fun sendMessage(channelId: String, body: SendMessageBody) = viewModelScope.launch {
        when (val result = api.sendMessage(channelId, body)) {
            is ApiResult.Success -> {
                _messages.update { it.copy(messages = listOf(result.value) + it.messages) }
                _newMessage.tryEmit(result.value)
            }
            is ApiResult.Error -> {}
        }
    }

    fun react(channelId: String, messageId: String, emoji: String) = viewModelScope.launch {
        when (val result = api.react(channelId, messageId, emoji)) {
            is ApiResult.Success -> {
                _reactionUpdate.tryEmit(ReactionUpdate(messageId, emoji, result.value))
            }
            is ApiResult.Error -> {}
        }
    }

    fun removeReaction(channelId: String, messageId: String, emoji: String) = viewModelScope.launch {
        when (val result = api.removeReaction(channelId, messageId, emoji)) {
            is ApiResult.Success -> {
                _reactionUpdate.tryEmit(ReactionUpdate(messageId, emoji, Reaction(emoji, 0, false)))
            }
            is ApiResult.Error -> {}
        }
    }

    fun forward(channelId: String, messageId: String, targetChannelId: String) = viewModelScope.launch {
        api.forward(channelId, messageId, targetChannelId)
    }

    fun translate(channelId: String, messageId: String, targetLang: String) = viewModelScope.launch {
        when (val result = api.translate(channelId, messageId, targetLang)) {
            is ApiResult.Success -> {
                _messageUpdate.tryEmit(MessageUpdate(messageId, mapOf("translatedText" to result.value, "isTranslated" to true)))
            }
            is ApiResult.Error -> {}
        }
    }

    fun pinMessage(channelId: String, messageId: String) = viewModelScope.launch {
        api.pinMessage(channelId, messageId)
    }

    fun unpinMessage(channelId: String, messageId: String) = viewModelScope.launch {
        api.unpinMessage(channelId, messageId)
    }

    fun deleteMessage(channelId: String, messageId: String) = viewModelScope.launch {
        when (val result = api.deleteMessage(channelId, messageId)) {
            is ApiResult.Success -> {
                _messages.update { it.copy(messages = it.messages.filter { it.id != messageId }) }
                _messageUpdate.tryEmit(MessageUpdate(messageId, mapOf("deleted" to true)))
            }
            is ApiResult.Error -> {}
        }
    }

    fun loadMedia(channelId: String, type: MediaType? = null, cursor: String? = null) {
        viewModelScope.launch {
            when (val result = api.getMedia(channelId, type, cursor)) {
                is ApiResult.Success -> {
                    _media.update { current ->
                        when (type) {
                            MediaType.IMAGE -> current.copy(photos = if (cursor == null) result.value.photos else current.photos + result.value.photos)
                            MediaType.VIDEO -> current.copy(videos = if (cursor == null) result.value.videos else current.videos + result.value.videos)
                            MediaType.FILE -> current.copy(files = if (cursor == null) result.value.files else current.files + result.value.files)
                            MediaType.LINK -> current.copy(links = if (cursor == null) result.value.links else current.links + result.value.links)
                            MediaType.VOICE -> current.copy(voice = if (cursor == null) result.value.voice else current.voice + result.value.voice)
                            else -> result.value
                        }
                    }
                }
                is ApiResult.Error -> {}
            }
        }
    }

    fun loadAnalytics(channelId: String) = viewModelScope.launch {
        when (val result = api.getAnalytics(channelId)) {
            is ApiResult.Success -> _analytics.update { result.value }
            is ApiResult.Error -> {}
        }
    }

    fun loadModerationQueue(channelId: String, status: String? = null, page: Int = 0) {
        viewModelScope.launch {
            when (val result = api.getModerationQueue(channelId, status, page)) {
                is ApiResult.Success -> {
                    if (page == 0) {
                        _moderationQueue.update { result.value }
                    } else {
                        _moderationQueue.update { it.copy(items = it.items + result.value.items, hasMore = result.value.hasMore) }
                    }
                }
                is ApiResult.Error -> {}
            }
        }
    }

    fun moderateAction(channelId: String, itemId: String, action: String, reason: String? = null) = viewModelScope.launch {
        when (val result = api.moderateAction(channelId, itemId, action, reason)) {
            is ApiResult.Success -> {
                _moderationQueue.update { it.copy(items = it.items.filter { it.id != itemId }) }
            }
            is ApiResult.Error -> {}
        }
    }

    fun loadBannedUsers(channelId: String) = viewModelScope.launch {
        when (val result = api.getBannedUsers(channelId)) {
            is ApiResult.Success -> _bannedUsers.update { result.value }
            is ApiResult.Error -> {}
        }
    }

    fun banUser(channelId: String, userId: String, reason: String, expiresAt: String? = null) = viewModelScope.launch {
        when (val result = api.banUser(channelId, userId, reason, expiresAt)) {
            is ApiResult.Success -> _bannedUsers.update { it + result.value }
            is ApiResult.Error -> {}
        }
    }

    fun unbanUser(channelId: String, userId: String) = viewModelScope.launch {
        when (val result = api.unbanUser(channelId, userId)) {
            is ApiResult.Success -> _bannedUsers.update { it.filter { it.userId != userId } }
            is ApiResult.Error -> {}
        }
    }

    fun createInvite(channelId: String, maxUses: Int = 0, expiresAt: String? = null) = viewModelScope.launch {
        when (val result = api.createInvite(channelId, maxUses, expiresAt)) {
            is ApiResult.Success -> _invites.update { it + result.value }
            is ApiResult.Error -> {}
        }
    }

    fun loadInvites(channelId: String) = viewModelScope.launch {
        when (val result = api.getInvites(channelId)) {
            is ApiResult.Success -> _invites.update { result.value }
            is ApiResult.Error -> {}
        }
    }

    fun revokeInvite(channelId: String, code: String) = viewModelScope.launch {
        when (val result = api.revokeInvite(channelId, code)) {
            is ApiResult.Success -> _invites.update { it.filter { it.code != code } }
            is ApiResult.Error -> {}
        }
    }

    fun loadMembers(channelId: String, role: String? = null, query: String? = null, page: Int = 0) {
        viewModelScope.launch {
            when (val result = api.getMembers(channelId, role, query, page)) {
                is ApiResult.Success -> {
                    if (page == 0) {
                        _members.update { result.value }
                    } else {
                        _members.update { it.copy(members = it.members + result.value.members, hasMore = result.value.hasMore) }
                    }
                }
                is ApiResult.Error -> {}
            }
        }
    }

    fun loadRoles(channelId: String) = viewModelScope.launch {
        when (val result = api.getRoles(channelId)) {
            is ApiResult.Success -> _roles.update { result.value }
            is ApiResult.Error -> {}
        }
    }

    fun createRole(channelId: String, body: CreateRoleBody) = viewModelScope.launch {
        when (val result = api.createRole(channelId, body)) {
            is ApiResult.Success -> _roles.update { it + result.value }
            is ApiResult.Error -> {}
        }
    }

    fun updateRole(channelId: String, roleId: String, body: UpdateRoleBody) = viewModelScope.launch {
        when (val result = api.updateRole(channelId, roleId, body)) {
            is ApiResult.Success -> _roles.update { it.map { if (it.name == roleId) result.value else it } }
            is ApiResult.Error -> {}
        }
    }

    fun deleteRole(channelId: String, roleId: String) = viewModelScope.launch {
        when (val result = api.deleteRole(channelId, roleId)) {
            is ApiResult.Success -> _roles.update { it.filter { it.name != roleId } }
            is ApiResult.Error -> {}
        }
    }

    fun loadEvents(channelId: String) = viewModelScope.launch {
        when (val result = api.getEvents(channelId)) {
            is ApiResult.Success -> _events.update { result.value }
            is ApiResult.Error -> {}
        }
    }

    fun createEvent(channelId: String, body: CreateEventBody) = viewModelScope.launch {
        when (val result = api.createEvent(channelId, body)) {
            is ApiResult.Success -> _events.update { it + result.value }
            is ApiResult.Error -> {}
        }
    }

    fun updateEvent(channelId: String, eventId: String, body: UpdateEventBody) = viewModelScope.launch {
        when (val result = api.updateEvent(channelId, eventId, body)) {
            is ApiResult.Success -> _events.update { it.map { if (it.id == eventId) result.value else it } }
            is ApiResult.Error -> {}
        }
    }

    fun deleteEvent(channelId: String, eventId: String) = viewModelScope.launch {
        when (val result = api.deleteEvent(channelId, eventId)) {
            is ApiResult.Success -> _events.update { it.filter { it.id != eventId } }
            is ApiResult.Error -> {}
        }
    }

    fun rsvpEvent(channelId: String, eventId: String, rsvp: EventRsvp) = viewModelScope.launch {
        when (val result = api.rsvpEvent(channelId, eventId, rsvp)) {
            is ApiResult.Success -> _events.update { it.map { if (it.id == eventId) result.value else it } }
            is ApiResult.Error -> {}
        }
    }

    fun loadScheduledMessages(channelId: String) = viewModelScope.launch {
        when (val result = api.getScheduledMessages(channelId)) {
            is ApiResult.Success -> _scheduledMessages.update { result.value }
            is ApiResult.Error -> {}
        }
    }

    fun scheduleMessage(channelId: String, body: ScheduleMessageBody) = viewModelScope.launch {
        when (val result = api.scheduleMessage(channelId, body)) {
            is ApiResult.Success -> _scheduledMessages.update { it + result.value }
            is ApiResult.Error -> {}
        }
    }

    fun cancelScheduledMessage(channelId: String, messageId: String) = viewModelScope.launch {
        when (val result = api.cancelScheduledMessage(channelId, messageId)) {
            is ApiResult.Success -> _scheduledMessages.update { it.filter { it.id != messageId } }
            is ApiResult.Error -> {}
        }
    }

    fun createPoll(channelId: String, body: CreatePollBody) = viewModelScope.launch {
        when (val result = api.createPoll(channelId, body)) {
            is ApiResult.Success -> {
                _messages.update { it.copy(messages = listOf(result.value) + it.messages) }
                _newMessage.tryEmit(result.value)
            }
            is ApiResult.Error -> {}
        }
    }

    fun votePoll(channelId: String, messageId: String, optionIds: List<String>) = viewModelScope.launch {
        when (val result = api.votePoll(channelId, messageId, optionIds)) {
            is ApiResult.Success -> {
                _messages.update { it.copy(messages = it.messages.map {
                    if (it.id == messageId) it.copy(poll = result.value) else it
                }) }
            }
            is ApiResult.Error -> {}
        }
    }

    fun updateMemberRole(channelId: String, userId: String, role: String) = viewModelScope.launch {
        when (val result = api.updateMemberRole(channelId, userId, role)) {
            is ApiResult.Success -> _members.update { it.copy(members = it.members.map { if (it.userId == userId) result.value else it }) }
            is ApiResult.Error -> {}
        }
    }

    fun kickMember(channelId: String, userId: String) = viewModelScope.launch {
        when (val result = api.kickMember(channelId, userId)) {
            is ApiResult.Success -> _members.update { it.copy(members = it.members.filter { it.userId != userId }) }
            is ApiResult.Error -> {}
        }
    }

    fun join(channel: Channel) = viewModelScope.launch {
        when (val result = api.join(channel.id)) {
            is ApiResult.Success -> {
                _uiState.update { current ->
                    current.copy(
                        channels = current.channels.map {
                            if (it.id == channel.id) it.copy(
                                subscriberCount = it.subscriberCount + 1,
                                isJoined = true
                            ) else it
                        ),
                        selectedChannel = current.selectedChannel?.let {
                            if (it.id == channel.id) it.copy(subscriberCount = it.subscriberCount + 1, isJoined = true) else it
                        }
                    )
                }
            }
            is ApiResult.Error -> {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun leave(channel: Channel) = viewModelScope.launch {
        when (val result = api.leave(channel.id)) {
            is ApiResult.Success -> {
                _uiState.update { current ->
                    current.copy(
                        channels = current.channels.map {
                            if (it.id == channel.id) it.copy(
                                subscriberCount = (it.subscriberCount - 1).coerceAtLeast(0),
                                isJoined = false
                            ) else it
                        ),
                        selectedChannel = current.selectedChannel?.let {
                            if (it.id == channel.id) it.copy(subscriberCount = (it.subscriberCount - 1).coerceAtLeast(0), isJoined = false) else it
                        }
                    )
                }
            }
            is ApiResult.Error -> {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun mute(channel: Channel, level: NotificationLevel) = viewModelScope.launch {
        when (val result = api.mute(channel.id, level)) {
            is ApiResult.Success -> {
                _uiState.update { current ->
                    current.copy(
                        channels = current.channels.map {
                            if (it.id == channel.id) it.copy(isMuted = level == NotificationLevel.NONE, notificationLevel = level) else it
                        ),
                        selectedChannel = current.selectedChannel?.let {
                            if (it.id == channel.id) it.copy(isMuted = level == NotificationLevel.NONE, notificationLevel = level) else it
                        }
                    )
                }
            }
            is ApiResult.Error -> {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun boost(channel: Channel, count: Int = 1) = viewModelScope.launch {
        when (val result = api.boost(channel.id, count)) {
            is ApiResult.Success -> {
                _uiState.update { current ->
                    current.copy(
                        channels = current.channels.map {
                            if (it.id == channel.id) result.value else it
                        ),
                        selectedChannel = current.selectedChannel?.let {
                            if (it.id == channel.id) result.value else it
                        }
                    )
                }
            }
            is ApiResult.Error -> {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun createChannel(
        name: String,
        username: String?,
        description: String?,
        isPublic: Boolean,
        isBroadcast: Boolean,
        category: String,
        tags: List<String>,
        coverImageUrl: String?,
        onSuccess: (Channel) -> Unit
    ) = viewModelScope.launch {
        val body = CreateChannelBody(
            name = name.trim(),
            username = username?.takeIf { it.isNotBlank() }?.trim()?.removePrefix("@"),
            description = description?.takeIf { it.isNotBlank() }?.trim(),
            isPublic = isPublic,
            isBroadcast = isBroadcast,
            category = category,
            tags = tags,
            coverImageUrl = coverImageUrl,
        )
        when (val result = api.create(body)) {
            is ApiResult.Success -> {
                _uiState.update {
                    it.copy(
                        showCreateDialog = false,
                        channels = listOf(result.value) + it.channels,
                    )
                }
                onSuccess(result.value)
            }
            is ApiResult.Error -> {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    private fun isAdmin(channelId: String): Boolean {
        val channel = _uiState.value.selectedChannel ?: _uiState.value.channels.find { it.id == channelId }
        return channel?.myRole != null && isChannelAdmin(ChannelMemberRole.fromServer(channel.myRole))
    }

    fun emitTyping(channelId: String, userId: String, userName: String, isTyping: Boolean) {
        _typingUsers.tryEmit(TypingEvent(channelId, userId, userName, isTyping))
    }

    fun onNewMessage(message: ChannelMessage) {
        _newMessage.tryEmit(message)
    }

    fun onMessageUpdate(update: MessageUpdate) {
        _messageUpdate.tryEmit(update)
    }

    fun onReactionUpdate(update: ReactionUpdate) {
        _reactionUpdate.tryEmit(update)
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun showCreateDialog() = _uiState.update { it.copy(showCreateDialog = true) }
    fun hideCreateDialog() = _uiState.update { it.copy(showCreateDialog = false) }
    fun showInviteDialog() = _uiState.update { it.copy(showInviteDialog = true) }
    fun hideInviteDialog() = _uiState.update { it.copy(showInviteDialog = false) }
    fun showMediaPicker(type: MediaType) = _uiState.update { it.copy(showMediaPicker = true, mediaPickerType = type) }
    fun hideMediaPicker() = _uiState.update { it.copy(showMediaPicker = false) }
    fun showEmojiPicker(messageId: String) = _uiState.update { it.copy(showEmojiPicker = true, emojiPickerMessageId = messageId) }
    fun hideEmojiPicker() = _uiState.update { it.copy(showEmojiPicker = false, emojiPickerMessageId = null) }
    fun showThread(messageId: String) = _uiState.update { it.copy(showThread = true, threadMessageId = messageId) }
    fun hideThread() = _uiState.update { it.copy(showThread = false, threadMessageId = null) }
    fun showTranslation(messageId: String) = _uiState.update { it.copy(showTranslation = true, translationMessageId = messageId) }
    fun hideTranslation() = _uiState.update { it.copy(showTranslation = false, translationMessageId = null) }
    fun showVoiceRecorder() = _uiState.update { it.copy(showVoiceRecorder = true) }
    fun hideVoiceRecorder() = _uiState.update { it.copy(showVoiceRecorder = false) }

    override fun onCleared() {
        searchJob?.cancel()
        paginationJob?.cancel()
        super.onCleared()
    }
}

data class ChannelsUiState(
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val query: String = "",
    val selectedCategory: String? = null,
    val selectedTags: Set<String> = emptySet(),
    val sortOrder: String = "subscribers",
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val showInviteDialog: Boolean = false,
    val showMediaPicker: Boolean = false,
    val mediaPickerType: MediaType? = null,
    val showEmojiPicker: Boolean = false,
    val emojiPickerMessageId: String? = null,
    val showThread: Boolean = false,
    val threadMessageId: String? = null,
    val showTranslation: Boolean = false,
    val translationMessageId: String? = null,
    val showVoiceRecorder: Boolean = false,
    val selectedChannel: Channel? = null,
)

data class TypingEvent(
    val channelId: String,
    val userId: String,
    val userName: String,
    val isTyping: Boolean,
)

data class MessageUpdate(
    val messageId: String,
    val changes: Map<String, Any>,
)

data class ReactionUpdate(
    val messageId: String,
    val emoji: String,
    val reaction: Reaction,
)