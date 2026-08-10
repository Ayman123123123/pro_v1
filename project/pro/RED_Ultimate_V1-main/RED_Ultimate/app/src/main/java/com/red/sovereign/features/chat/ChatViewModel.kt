package com.red.sovereign.features.chat

import androidx.lifecycle.ViewModel
import com.red.sovereign.core.delivery.MasterDeliveryEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val deliveryEngine: MasterDeliveryEngine
) : ViewModel() {
    fun sendMessage(chatId: String, text: String) {
        deliveryEngine.dispatchMessage(chatId, text)
    }
}
