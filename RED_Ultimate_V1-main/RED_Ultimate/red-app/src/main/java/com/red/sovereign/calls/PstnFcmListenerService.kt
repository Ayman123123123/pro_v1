package com.red.sovereign.calls

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PstnFcmListenerService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {}
    override fun onNewToken(token: String) {}
}
