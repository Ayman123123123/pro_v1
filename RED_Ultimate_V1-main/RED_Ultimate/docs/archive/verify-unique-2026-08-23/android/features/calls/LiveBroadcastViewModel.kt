package com.red.sovereign.features.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveBroadcastViewModel @Inject constructor(
    private val manager: LiveBroadcastManager
) : ViewModel() {

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount

    private val _reactions = MutableStateFlow<List<String>>(emptyList())
    val reactions: StateFlow<List<String>> = _reactions

    fun startLive(streamId: String) = manager.startBroadcasting(streamId)
    fun joinLive(streamId: String) = manager.joinStream(streamId)

    fun sendHeart(streamId: String) {
        viewModelScope.launch {
            manager.sendReaction(streamId, "HEART")
            // Optimistic UI
            _reactions.value += "HEART"
        }
    }
}
