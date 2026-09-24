package com.red.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.red.core.delivery.MessageDao
import com.red.core.delivery.MessageDeliveryManager
import com.red.core.delivery.MessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val deliveryManager: MessageDeliveryManager,
    private val messageDao: MessageDao
) : ViewModel() {

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    fun sendMessage(conversationId: String, text: String) {
        viewModelScope.launch {
            deliveryManager.sendMessage(conversationId, text)
        }
    }
}
