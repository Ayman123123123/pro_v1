package com.red.sovereign.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.red.sovereign.MainActivity
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthApi
import com.red.sovereign.auth.DeviceKeyManager
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.database.LocalHistoryEntity
import com.red.sovereign.core.database.LocalRepository
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.crypto.DecryptedMessageBus
import com.red.sovereign.crypto.SignalSessionManager
import com.red.sovereign.groups.Group
import com.red.sovereign.groups.GroupCryptoManager
import com.red.sovereign.proto.RedProtos
import com.red.sovereign.settings.SettingsRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Local-first replacement for cloud push: an explicit foreground WebSocket connection. */
class RedConnectionService : Service() {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var reconnectTask: ScheduledFuture<*>? = null
    private var attempts = 0
    @Volatile private var connected = false
    private val pendingSends = ConcurrentLinkedQueue<PendingSend>()
    private val pendingGroupSends = ConcurrentLinkedQueue<PendingGroupSend>()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var tokenStore: TokenStore
    private lateinit var repository: LocalRepository
    private lateinit var signal: SignalSessionManager
    private lateinit var groupCrypto: GroupCryptoManager
    private lateinit var keyManager: DeviceKeyManager
    private lateinit var socket: RedWebSocketClient

    override fun onCreate() {
        super.onCreate()
        createChannels()
        tokenStore = TokenStore(this)
        repository = LocalRepository(this)
        signal = SignalSessionManager(this)
        groupCrypto = GroupCryptoManager(this)
        keyManager = DeviceKeyManager(this)
        socket = RedWebSocketClient(this, tokenStore, ::onEnvelope, ::onState)
        scope.launch {
            if (signal.replenishPreKeys() is ApiResult.Error) {
                notifyConnection(getString(com.red.sovereign.R.string.status_session_keys_error))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(CONNECTION_NOTIFICATION, connectionNotification(getString(com.red.sovereign.R.string.status_connecting)))
        if (intent?.action == ACTION_MARK_READ) {
            val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return START_STICKY
            socket.acknowledge(messageId, intent.getLongExtra(EXTRA_SEQUENCE, 0), "READ")
        } else if (intent?.action == ACTION_SEND_PAYLOAD) {
            val target = intent.getStringExtra(EXTRA_TARGET) ?: return START_STICKY
            val conversation = intent.getStringExtra(EXTRA_CONVERSATION) ?: return START_STICKY
            val type = intent.getStringExtra(EXTRA_TYPE)?.takeIf { it in ALLOWED_MESSAGE_TYPES } ?: return START_STICKY
            val payload = intent.getByteArrayExtra(EXTRA_PAYLOAD)?.takeIf { it.isNotEmpty() && it.size <= 256 * 1024 } ?: return START_STICKY
            pendingSends.add(PendingSend(target, conversation, type, payload))
            if (connected) drainSends() else socket.connect()
        } else if (intent?.action == ACTION_SEND_GROUP_TEXT) {
            val encodedGroup = intent.getStringExtra(EXTRA_GROUP) ?: return START_STICKY
            val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf(String::isNotBlank) ?: return START_STICKY
            pendingGroupSends.add(PendingGroupSend(encodedGroup, text))
            if (connected) drainGroupSends() else socket.connect()
        } else if (intent?.action == ACTION_SEND_TYPING) {
            val target = intent.getStringExtra(EXTRA_TARGET) ?: return START_STICKY
            val conversation = intent.getStringExtra(EXTRA_CONVERSATION) ?: return START_STICKY
            val isTyping = intent.getBooleanExtra(EXTRA_IS_TYPING, false)
            if (connected) socket.typing(conversation, target, isTyping)
        } else socket.connect()
        return START_STICKY
    }

    private fun drainSends() {
        while (connected) {
            val pending = pendingSends.poll() ?: break
            sendEncryptedPayload(pending)
        }
    }

    private fun drainGroupSends() {
        while (connected) {
            val pending = pendingGroupSends.poll() ?: break
            val group = runCatching { json.decodeFromString<Group>(pending.groupJson) }.getOrNull() ?: continue
            scope.launch {
                when (val prepared = groupCrypto.prepare(group, pending.text.toByteArray(Charsets.UTF_8))) {
                    is ApiResult.Error -> notifyConnection(getString(com.red.sovereign.R.string.status_group_encryption_failed, prepared.message))
                    is ApiResult.Success -> {
                        prepared.value.distributions.forEach { distribution ->
                            socket.sendEncrypted(distribution.receiverRedId, group.id, "GROUP_KEY_DISTRIBUTION", keyManager.protocolDeviceId(), distribution.encrypted)
                        }
                        var firstId: String? = null
                        prepared.value.recipients.forEach { recipient ->
                            val envelope = prepared.value.groupCiphertext.copy(receiverDeviceId = recipient.protocolDeviceId)
                            val id = socket.sendEncrypted(recipient.redId, group.id, "GROUP_MESSAGE", keyManager.protocolDeviceId(), envelope)
                            if (firstId == null) firstId = id
                        }
                        firstId?.let {
                            val bytes = pending.text.toByteArray(Charsets.UTF_8); val timestamp = System.currentTimeMillis()
                            repository.saveLocalHistory(LocalHistoryEntity(it, group.id, tokenStore.redId.orEmpty(), bytes, "GROUP_MESSAGE", timestamp, true))
                            DecryptedMessageBus.publish(DecryptedMessage(it, group.id, tokenStore.redId.orEmpty(), bytes, timestamp, 0, type = "GROUP_MESSAGE", outgoing = true))
                        }
                    }
                }
            }
        }
    }

    private fun sendEncryptedPayload(pending: PendingSend) {
        scope.launch {
            when (val encrypted = signal.encrypt(pending.target, pending.payload)) {
                is ApiResult.Error -> notifyConnection(getString(com.red.sovereign.R.string.status_encryption_failed, encrypted.message))
                is ApiResult.Success -> {
                    var firstId: String? = null
                    encrypted.value.forEach { envelope ->
                        val id = socket.sendEncrypted(pending.target, pending.conversation, pending.type, keyManager.protocolDeviceId(), envelope)
                        if (firstId == null) firstId = id
                    }
                    firstId?.let {
                        val timestamp = System.currentTimeMillis()
                        repository.saveLocalHistory(LocalHistoryEntity(it, pending.conversation, tokenStore.redId.orEmpty(), pending.payload, pending.type, timestamp, true))
                        DecryptedMessageBus.publish(DecryptedMessage(it, pending.conversation, tokenStore.redId.orEmpty(), pending.payload, timestamp, sequence = 0, type = pending.type, outgoing = true))
                    }
                }
            }
        }
    }

    private fun onState(state: ConnectionState) {
        when (state) {
            ConnectionState.CONNECTED -> {
                connected = true
                attempts = 0
                reconnectTask?.cancel(false)
                notifyConnection(getString(com.red.sovereign.R.string.status_connected_local))
                scope.launch {
                    when (val stock = signal.replenishPreKeys()) {
                        is ApiResult.Success -> if (stock.value.ecAvailable < stock.value.minimumRecommended || stock.value.kyberAvailable < stock.value.minimumRecommended) {
                            notifyConnection(getString(com.red.sovereign.R.string.status_session_keys_low))
                        }
                        is ApiResult.Error -> notifyConnection(getString(com.red.sovereign.R.string.status_session_keys_error))
                    }
                }
                drainSends()
                drainGroupSends()
            }
            ConnectionState.CONNECTING -> notifyConnection(getString(com.red.sovereign.R.string.status_connecting_local))
            ConnectionState.DISCONNECTED -> { connected = false; scheduleReconnect() }
            ConnectionState.UNAUTHORIZED -> { connected = false; refreshAndReconnect() }
        }
    }

    private fun refreshAndReconnect() {
        val refresh = tokenStore.refreshToken ?: run { stopSelf(); return }
        scope.launch {
            when (val result = AuthApi(applicationContext).refresh(refresh)) {
                is ApiResult.Success -> { tokenStore.updateTokens(result.value); attempts = 0; socket.connect() }
                is ApiResult.Error -> { notifyConnection(getString(com.red.sovereign.R.string.status_session_expired)); stopSelf() }
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectTask?.cancel(false)
        val delay = minOf(60L, 1L shl minOf(attempts++, 6))
        notifyConnection(getString(com.red.sovereign.R.string.status_disconnected_retry, delay))
        reconnectTask = scheduler.schedule({ socket.connect() }, delay, TimeUnit.SECONDS)
    }

    private fun onEnvelope(envelope: RedProtos.RedRED) {
        when (envelope.signalCase) {
            RedProtos.RedRED.SignalCase.MESSAGE -> {
                val message = envelope.message
                if (message.receiverId == tokenStore.redId && message.receiverDeviceId == keyManager.protocolDeviceId()) {
                    runCatching {
                        repository.saveIncomingMessage(message)
                        when (message.type) {
                            "GROUP_MESSAGE" -> groupCrypto.decrypt(message.senderId, message.senderDeviceId, message.payload.toByteArray())
                            else -> signal.decrypt(message.senderId, message.senderDeviceId, message.ciphertextType, message.payload.toByteArray())
                        }
                    }.onSuccess { plaintext ->
                        if (message.type == "GROUP_KEY_DISTRIBUTION") {
                            groupCrypto.processDistribution(message.senderId, message.senderDeviceId, plaintext)
                        } else {
                            repository.saveLocalHistory(LocalHistoryEntity(message.id, message.conversationId, message.senderId, plaintext, message.type, message.timestamp, false))
                            DecryptedMessageBus.publish(DecryptedMessage(message.id, message.conversationId, message.senderId, plaintext, message.timestamp, message.sequenceNumber, type = message.type))
                            // Show notification for incoming encrypted message
                            val preview = decodeMessagePreview(plaintext)
                            if (SettingsRuntime.current.notificationEnabled) {
                                notifyEncryptedMessage(message.senderId, preview)
                            }
                        }
                        socket.acknowledge(message.id, message.sequenceNumber, "DELIVERED")
                    }
                } else if (message.senderId == tokenStore.redId) {
                    repository.saveIncomingMessage(message, outgoing = true)
                }
            }
            RedProtos.RedRED.SignalCase.ACK -> {
                repository.updateMessageStatus(envelope.ack.messageId, envelope.ack.status)
                com.red.sovereign.crypto.MessageAckBus.publish(com.red.sovereign.crypto.MessageAck(envelope.ack.messageId, envelope.ack.status))
            }
            RedProtos.RedRED.SignalCase.DELETE -> {
                val delete = envelope.delete
                when (delete.targetCase) {
                    RedProtos.RedDelete.TargetCase.MESSAGE_IDS -> {
                        // حذف محلي فقط — لا يُحذف من الخادم
                        delete.messageIdsList.forEach { msgId ->
                            runCatching { repository.deleteLocalMessage(msgId) }
                                .onFailure { android.util.Log.w("RedConnectionService", "delete failed for $msgId: ${it.message}") }
                        }
                    }
                    RedProtos.RedDelete.TargetCase.CONVERSATION_ID -> {
                        runCatching { repository.deleteConversation(delete.conversationId) }
                            .onFailure { android.util.Log.w("RedConnectionService", "delete conv failed: ${it.message}") }
                    }
                    RedProtos.RedDelete.TargetCase.TARGET_NOT_SET -> Unit
                }
            }
            RedProtos.RedRED.SignalCase.TYPING -> {
                val typing = envelope.typing
                TypingEventBus.publish(TypingEvent(typing.conversationId, typing.userId, typing.isTyping))
            }
            else -> Unit
        }
    }

    /**
     * فك تشفير preview للـ notification (نص عادي فقط، آمن)
     */
    private fun decodeMessagePreview(plaintext: ByteArray): String? {
        return runCatching {
            val text = String(plaintext, Charsets.UTF_8)
            // لو كانت JSON، نستخرج text
            val parsed = json.parseToJsonElement(text)
            parsed.jsonObject["text"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: runCatching {
            String(plaintext, Charsets.UTF_8).take(120)
        }.getOrNull()
    }

    private fun notifyEncryptedMessage(sender: String, plaintext: String?) {
        val manager = getSystemService(NotificationManager::class.java)
        val preview = plaintext?.take(120)?.takeIf { SettingsRuntime.current.notificationPreview }
        manager.notify(sender.hashCode(), NotificationCompat.Builder(this, MESSAGE_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(getString(com.red.sovereign.R.string.notif_new_message_title))
            .setContentText(preview ?: getString(com.red.sovereign.R.string.notif_new_message_body, sender))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build())
    }

    private fun connectionNotification(text: String) = NotificationCompat.Builder(this, CONNECTION_CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
        .setContentTitle(getString(com.red.sovereign.R.string.notif_connection_title))
        .setContentText(text)
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setSilent(true)
        .build()

    private fun notifyConnection(text: String) =
        getSystemService(NotificationManager::class.java).notify(CONNECTION_NOTIFICATION, connectionNotification(text))

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CONNECTION_CHANNEL, getString(com.red.sovereign.R.string.channel_connection_name), NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(MESSAGE_CHANNEL, getString(com.red.sovereign.R.string.channel_messages_name), NotificationManager.IMPORTANCE_HIGH))
    }

    override fun onDestroy() {
        reconnectTask?.cancel(true); scheduler.shutdownNow(); scope.cancel(); socket.disconnect(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CONNECTION_CHANNEL = "red_connection"
        private const val MESSAGE_CHANNEL = "red_messages"
        private const val CONNECTION_NOTIFICATION = 7001
        private const val ACTION_SEND_PAYLOAD = "com.red.sovereign.SEND_PAYLOAD"
        const val ACTION_MARK_READ = "com.red.sovereign.MARK_READ"
        private const val ACTION_SEND_GROUP_TEXT = "com.red.sovereign.SEND_GROUP_TEXT"
        const val ACTION_SEND_TYPING = "com.red.sovereign.SEND_TYPING"
        const val EXTRA_MESSAGE_ID = "msgId"
        const val EXTRA_TARGET = "target"
        const val EXTRA_CONVERSATION = "conversation"
        const val EXTRA_SEQUENCE = "sequence"
        const val EXTRA_IS_TYPING = "isTyping"
        private const val EXTRA_PAYLOAD = "payload"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_GROUP = "group"
        private const val EXTRA_TEXT = "text"
        private val ALLOWED_MESSAGE_TYPES = setOf("TEXT", "RICH_TEXT", "FILE", "VOICE", "IMAGE", "VIDEO", "AUDIO")

        fun start(context: Context) = context.startForegroundService(Intent(context, RedConnectionService::class.java))
        fun sendText(context: Context, targetRedId: String, conversationId: String, text: String) =
            sendPayload(context, targetRedId, conversationId, "TEXT", text.toByteArray(Charsets.UTF_8))

        fun sendRichText(context: Context, targetRedId: String, conversationId: String, message: RichMessage) =
            sendPayload(context, targetRedId, conversationId, "RICH_TEXT", RichMessage.encode(message))

        fun sendPayload(context: Context, targetRedId: String, conversationId: String, type: String, payload: ByteArray) =
            context.startForegroundService(
                Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_PAYLOAD)
                    .putExtra(EXTRA_TARGET, targetRedId).putExtra(EXTRA_CONVERSATION, conversationId)
                    .putExtra(EXTRA_TYPE, type).putExtra(EXTRA_PAYLOAD, payload)
            )
        fun sendGroupText(context: Context, group: Group, text: String) = context.startForegroundService(
            Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_GROUP_TEXT)
                .putExtra(EXTRA_GROUP, Json.encodeToString(group)).putExtra(EXTRA_TEXT, text)
        )

        fun markRead(context: Context, messageId: String, sequence: Long) = context.startForegroundService(
            Intent(context, RedConnectionService::class.java).setAction(ACTION_MARK_READ)
                .putExtra(EXTRA_MESSAGE_ID, messageId).putExtra(EXTRA_SEQUENCE, sequence)
        )
        fun stop(context: Context) = context.stopService(Intent(context, RedConnectionService::class.java))
    }
}

private data class PendingSend(val target: String, val conversation: String, val type: String, val payload: ByteArray)
private data class PendingGroupSend(val groupJson: String, val text: String)
