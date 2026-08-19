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
import com.red.sovereign.core.database.OutboxEntity
import com.red.sovereign.core.database.OutboxEnvelopeEntity
import com.red.sovereign.core.outbox.OutboxRetryWorker
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** حدث تفاعل إيموجي وارد (E2EE) — يُبَث للواجهة لتحديث الـ chips تحت الرسالة. */
data class ReactionEvent(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val emoji: String?,
    val timestamp: Long,
    val remove: Boolean
)

/** ناقل أحداث التفاعلات — يُستمع إليه في الواجهة لتحديث العرض فورياً. */
object ReactionEventBus {
    private val _events = MutableSharedFlow<ReactionEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events = _events.asSharedFlow()
    fun publish(event: ReactionEvent) { _events.tryEmit(event) }
}

/** Local-first replacement for cloud push: an explicit foreground WebSocket connection. */
class RedConnectionService : Service() {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var reconnectTask: ScheduledFuture<*>? = null
    private var attempts = 0
    @Volatile private var connected = false
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var tokenStore: TokenStore
    private lateinit var repository: LocalRepository
    /** آخر محادثة واردة — تُستخدم في إشعار الرسالة لفتح المحادثة الصحيحة. */
    @Volatile private var currentConversationId: String? = null
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
            val clientId = intent.getStringExtra(EXTRA_CLIENT_ID)
            scope.launch {
                repository.enqueueOutbox(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(), kind = OUTBOX_DIRECT, clientId = clientId,
                        targetRedId = target, conversationId = conversation, messageType = type, payload = payload, groupJson = null
                    )
                )
                drainOutboxOrConnect()
            }
        } else if (intent?.action == ACTION_SEND_GROUP_TEXT) {
            val encodedGroup = intent.getStringExtra(EXTRA_GROUP) ?: return START_STICKY
            val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf(String::isNotBlank) ?: return START_STICKY
            val isRich = intent.getBooleanExtra(EXTRA_GROUP_RICH, false)
            val groupType = intent.getStringExtra(EXTRA_GROUP_TYPE)
            val clientId = intent.getStringExtra(EXTRA_CLIENT_ID)
            scope.launch {
                repository.enqueueOutbox(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(), kind = OUTBOX_GROUP, clientId = clientId,
                        targetRedId = null, conversationId = extractGroupId(encodedGroup), messageType = groupType,
                        payload = text.toByteArray(Charsets.UTF_8), groupJson = encodedGroup, isRich = isRich
                    )
                )
                drainOutboxOrConnect()
            }
        } else if (intent?.action == ACTION_SEND_TYPING) {
            val target = intent.getStringExtra(EXTRA_TARGET) ?: return START_STICKY
            val conversation = intent.getStringExtra(EXTRA_CONVERSATION) ?: return START_STICKY
            val isTyping = intent.getBooleanExtra(EXTRA_IS_TYPING, false)
            if (connected) socket.typing(conversation, target, isTyping)
        } else socket.connect()
        return START_STICKY
    }

    private fun drainOutboxOrConnect() {
        if (connected) {
            drainOutbox()
        } else {
            OutboxRetryWorker.schedule(applicationContext)
            socket.connect()
        }
    }

    /** يستنزف سجلات Room بترتيب إنشائها؛ لا تحذف النية قبل ACK من الخادم. */
    private fun drainOutbox() {
        scope.launch {
            repository.pendingOutbox().forEach { item ->
                runCatching { sendOutbox(item) }
                    .onFailure { error ->
                        repository.markOutboxAttempt(item.id)
                        OutboxRetryWorker.schedule(applicationContext)
                        android.util.Log.w("RedConnectionService", "outbox send failed for ${item.id}: ${error.message}")
                    }
            }
        }
    }

    private suspend fun sendOutbox(item: OutboxEntity) {
        var envelopes = repository.outboxEnvelopes(item.id)
        if (envelopes.isEmpty()) {
            envelopes = when (item.kind) {
                OUTBOX_DIRECT -> prepareDirectOutbox(item)
                OUTBOX_GROUP -> prepareGroupOutbox(item)
                else -> {
                    failOutgoing(item.clientId, item.conversationId, "INVALID_OUTBOX_KIND")
                    repository.discardOutbox(item.id)
                    emptyList()
                }
            }
        }
        if (envelopes.isEmpty()) return
        envelopes.forEach { envelope ->
            socket.sendEncrypted(
                receiverRedId = envelope.receiverRedId,
                conversationId = envelope.conversationId,
                messageType = envelope.messageType,
                senderDeviceId = keyManager.protocolDeviceId(),
                encrypted = com.red.sovereign.crypto.EncryptedEnvelope(
                    envelope.receiverDeviceId,
                    envelope.ciphertextType,
                    envelope.encryptedPayload
                ),
                messageId = envelope.messageId
            )
        }
        repository.markOutboxAttempt(item.id)
    }

    private suspend fun prepareDirectOutbox(item: OutboxEntity): List<OutboxEnvelopeEntity> {
        val target = item.targetRedId ?: run {
            failOutgoing(item.clientId, item.conversationId, "INVALID_TARGET")
            repository.discardOutbox(item.id)
            return emptyList()
        }
        val type = item.messageType ?: run {
            failOutgoing(item.clientId, item.conversationId, "INVALID_MESSAGE_TYPE")
            repository.discardOutbox(item.id)
            return emptyList()
        }
        return when (val encrypted = signal.encrypt(target, item.payload)) {
            is ApiResult.Error -> {
                notifyConnection(getString(com.red.sovereign.R.string.status_encryption_failed, encrypted.message))
                repository.markOutboxAttempt(item.id)
                OutboxRetryWorker.schedule(applicationContext)
                emptyList()
            }
            is ApiResult.Success -> {
                val envelopes = encrypted.value.map { envelope ->
                    OutboxEnvelopeEntity(
                        messageId = UuidV7.next(), outboxId = item.id, receiverRedId = target,
                        conversationId = item.conversationId, messageType = type, encryptedPayload = envelope.bytes,
                        receiverDeviceId = envelope.receiverDeviceId, ciphertextType = envelope.ciphertextType
                    )
                }
                repository.saveOutboxEnvelopes(envelopes)
                presentDirectOutbox(item, envelopes.firstOrNull()?.messageId, type)
                envelopes
            }
        }
    }

    private suspend fun prepareGroupOutbox(item: OutboxEntity): List<OutboxEnvelopeEntity> {
        val group = item.groupJson?.let { runCatching { json.decodeFromString<Group>(it) }.getOrNull() }
            ?: run {
                failOutgoing(item.clientId, item.conversationId, "INVALID_GROUP")
                repository.discardOutbox(item.id)
                return emptyList()
            }
        val type = item.messageType?.takeIf { it in ALLOWED_MESSAGE_TYPES } ?: if (item.isRich) "RICH_TEXT" else "GROUP_MESSAGE"
        return when (val prepared = groupCrypto.prepare(group, item.payload)) {
            is ApiResult.Error -> {
                notifyConnection(getString(com.red.sovereign.R.string.status_group_encryption_failed, prepared.message))
                repository.markOutboxAttempt(item.id)
                OutboxRetryWorker.schedule(applicationContext)
                emptyList()
            }
            is ApiResult.Success -> {
                val distributions = prepared.value.distributions.map { distribution ->
                    OutboxEnvelopeEntity(
                        messageId = UuidV7.next(), outboxId = item.id, receiverRedId = distribution.receiverRedId,
                        conversationId = group.id, messageType = "GROUP_KEY_DISTRIBUTION", encryptedPayload = distribution.encrypted.bytes,
                        receiverDeviceId = distribution.encrypted.receiverDeviceId, ciphertextType = distribution.encrypted.ciphertextType
                    )
                }
                val messages = prepared.value.recipients.map { recipient ->
                    OutboxEnvelopeEntity(
                        messageId = UuidV7.next(), outboxId = item.id, receiverRedId = recipient.redId,
                        conversationId = group.id, messageType = type, encryptedPayload = prepared.value.groupCiphertext.bytes,
                        receiverDeviceId = recipient.protocolDeviceId, ciphertextType = prepared.value.groupCiphertext.ciphertextType
                    )
                }
                val envelopes = distributions + messages
                if (envelopes.isEmpty()) {
                    failOutgoing(item.clientId, group.id, "NO_RECIPIENT")
                    repository.discardOutbox(item.id)
                    return emptyList()
                }
                repository.saveOutboxEnvelopes(envelopes)
                presentGroupOutbox(item, group.id, messages.firstOrNull()?.messageId, type)
                envelopes
            }
        }
    }

    private suspend fun presentDirectOutbox(item: OutboxEntity, messageId: String?, type: String) {
        val id = messageId ?: return
        val rich = RichMessage.decode(item.payload)
        if (rich?.action == "REACTION" || rich?.action == "REACTION_REMOVE") {
            applyOutgoingReactionLocally(rich, item.conversationId, tokenStore.redId.orEmpty())
            succeedOutgoing(item.clientId, item.conversationId, id)
            return
        }
        val timestamp = System.currentTimeMillis()
        repository.saveLocalHistory(LocalHistoryEntity(id, item.conversationId, tokenStore.redId.orEmpty(), item.payload, type, timestamp, true))
        repository.linkOutboxLocalMessage(item.id, id)
        DecryptedMessageBus.publish(DecryptedMessage(id, item.conversationId, tokenStore.redId.orEmpty(), item.payload, timestamp, 0, type = type, outgoing = true))
        runCatching { repository.onMessageStored(item.conversationId, item.targetRedId.orEmpty(), decodeMessagePreview(item.payload).orEmpty(), timestamp, isIncoming = false) }
        succeedOutgoing(item.clientId, item.conversationId, id)
    }

    private suspend fun presentGroupOutbox(item: OutboxEntity, groupId: String, messageId: String?, type: String) {
        val id = messageId ?: return
        val rich = if (item.isRich) RichMessage.decode(item.payload) else null
        if (rich?.action == "REACTION" || rich?.action == "REACTION_REMOVE") {
            applyOutgoingReactionLocally(rich, groupId, tokenStore.redId.orEmpty())
            succeedOutgoing(item.clientId, groupId, id)
            return
        }
        val timestamp = System.currentTimeMillis()
        repository.saveLocalHistory(LocalHistoryEntity(id, groupId, tokenStore.redId.orEmpty(), item.payload, type, timestamp, true))
        repository.linkOutboxLocalMessage(item.id, id)
        DecryptedMessageBus.publish(DecryptedMessage(id, groupId, tokenStore.redId.orEmpty(), item.payload, timestamp, 0, type = type, outgoing = true))
        succeedOutgoing(item.clientId, groupId, id)
    }

    private fun extractGroupId(groupJson: String): String =
        runCatching { json.decodeFromString<Group>(groupJson).id }.getOrDefault("")

    /** يُطبّق تفاعلاً صادراً محلياً (في جدول التفاعلات) ويُبَثه للواجهة دون حفظه كرسالة. */
    private fun applyOutgoingReactionLocally(rich: com.red.sovereign.core.RichMessage, conversationId: String, myRedId: String) {
        val targetId = rich.reactionOf ?: return
        scope.launch {
            if (rich.action == "REACTION" && rich.emoji != null) {
                repository.applyReaction(targetId, conversationId, myRedId, rich.emoji, remove = false, System.currentTimeMillis())
                ReactionEventBus.publish(ReactionEvent(targetId, conversationId, myRedId, rich.emoji, System.currentTimeMillis(), remove = false))
            } else if (rich.action == "REACTION_REMOVE") {
                repository.applyReaction(targetId, conversationId, myRedId, null, remove = true, System.currentTimeMillis())
                ReactionEventBus.publish(ReactionEvent(targetId, conversationId, myRedId, null, System.currentTimeMillis(), remove = true))
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
                drainOutbox()
            }
            ConnectionState.CONNECTING -> notifyConnection(getString(com.red.sovereign.R.string.status_connecting_local))
            ConnectionState.DISCONNECTED -> {
                connected = false
                OutboxRetryWorker.schedule(applicationContext)
                scheduleReconnect()
            }
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
                scope.launch {
                if (message.receiverId == tokenStore.redId && message.receiverDeviceId == keyManager.protocolDeviceId()) {
                    runCatching {
                        repository.saveIncomingMessage(message)
                        when (message.type) {
                            "GROUP_MESSAGE" -> groupCrypto.decrypt(message.senderId, message.senderDeviceId, message.payload.toByteArray())
                            "RICH_TEXT" -> {
                                // رسالة غنية: قد تكون فردية أو جماعية. نحسم عبر طول conversationId:
                                // محادثة فردية = hash مُقتطع (32 حرفاً)، معرف مجموعة = UUID طويل (>32).
                                if (message.conversationId.length > 32) {
                                    groupCrypto.decrypt(message.senderId, message.senderDeviceId, message.payload.toByteArray())
                                } else {
                                    signal.decrypt(message.senderId, message.senderDeviceId, message.ciphertextType, message.payload.toByteArray())
                                }
                            }
                            else -> signal.decrypt(message.senderId, message.senderDeviceId, message.ciphertextType, message.payload.toByteArray())
                        }
                    }.onSuccess { plaintext ->
                        if (message.type == "GROUP_KEY_DISTRIBUTION") {
                            groupCrypto.processDistribution(message.senderId, message.senderDeviceId, plaintext)
                        } else if (message.type == "RICH_TEXT") {
                            val rich = com.red.sovereign.core.RichMessage.decode(plaintext)
                            when (rich?.action) {
                                // التعديل: تحديث نص الرسالة الأصلية في التخزين (لا إضافة سجل جديد)
                                "EDIT" -> rich.editOf?.let { editOf ->
                                    repository.updateLocalHistoryText(editOf, com.red.sovereign.core.RichMessage.encode(rich.copy(replyTo = rich.replyTo)))
                                    DecryptedMessageBus.publish(DecryptedMessage(editOf, message.conversationId, message.senderId, com.red.sovereign.core.RichMessage.encode(rich), message.timestamp, message.sequenceNumber, type = "RICH_TEXT"))
                                }
                                // الحذف للجميع: حذف الرسالة الأصلية من التخزين
                                "DELETE" -> rich.deleteOf?.let { deleteOf ->
                                    repository.deleteLocalMessage(deleteOf)
                                    DecryptedMessageBus.publish(DecryptedMessage(message.id, message.conversationId, message.senderId, plaintext, message.timestamp, message.sequenceNumber, type = "RICH_TEXT"))
                                }
                                // تفاعل إيموجي: لا يُحفظ كرسالة، بل يُطبّق على جدول التفاعلات
                                "REACTION" -> rich.reactionOf?.let { targetId ->
                                    repository.applyReaction(targetId, message.conversationId, message.senderId, rich.emoji, remove = false, message.timestamp)
                                    ReactionEventBus.publish(ReactionEvent(targetId, message.conversationId, message.senderId, rich.emoji, message.timestamp, remove = false))
                                }
                                // إزالة تفاعل إيموجي
                                "REACTION_REMOVE" -> rich.reactionOf?.let { targetId ->
                                    repository.applyReaction(targetId, message.conversationId, message.senderId, null, remove = true, message.timestamp)
                                    ReactionEventBus.publish(ReactionEvent(targetId, message.conversationId, message.senderId, null, message.timestamp, remove = true))
                                }
                                else -> storeRichOrPlainMessage(message, plaintext)
                            }
                            socket.acknowledge(message.id, message.sequenceNumber, "DELIVERED")
                        } else {
                            repository.saveLocalHistory(LocalHistoryEntity(message.id, message.conversationId, message.senderId, plaintext, message.type, message.timestamp, false))
                            DecryptedMessageBus.publish(DecryptedMessage(message.id, message.conversationId, message.senderId, plaintext, message.timestamp, message.sequenceNumber, type = message.type))
                            // Show notification for incoming encrypted message
                            val preview = decodeMessagePreview(plaintext)
                            // تحديث/إنشاء صف المحادثة لتظهر في قائمة الدردشات (فردية فقط — لا لمجموعة)
                            if (message.type != "GROUP_MESSAGE") {
                                runCatching { repository.onMessageStored(message.conversationId, message.senderId, preview.orEmpty(), message.timestamp, isIncoming = true) }
                            }
                            if (shouldNotify(message.conversationId, message.type)) {
                                currentConversationId = message.conversationId
                                notifyEncryptedMessage(message.senderId, preview, message.type, message.conversationId)
                            }
                            socket.acknowledge(message.id, message.sequenceNumber, "DELIVERED")
                        }
                    }
                } else if (message.senderId == tokenStore.redId) {
                    repository.saveIncomingMessage(message, outgoing = true)
                }
                }
            }
            RedProtos.RedRED.SignalCase.ACK -> scope.launch {
                val ack = envelope.ack
                repository.updateMessageStatus(ack.messageId, ack.status)
                // ACK الحالة SENT من الخادم يؤكد قبوله للمعرف نفسه؛ تحذف الحوامل
                // واحدةً واحدة، ولا يحذف سجل Outbox الأب إلا بعد آخر جهاز مستهدف.
                repository.acknowledgeOutbox(ack.messageId)
                com.red.sovereign.crypto.MessageAckBus.publish(com.red.sovereign.crypto.MessageAck(ack.messageId, ack.status))
            }
            RedProtos.RedRED.SignalCase.DELETE -> scope.launch {
                val delete = envelope.delete
                when {
                    delete.messageId.isNotBlank() -> {
                        runCatching { repository.deleteLocalMessage(delete.messageId) }
                            .onFailure { android.util.Log.w("RedConnectionService", "delete failed for ${delete.messageId}: ${it.message}") }
                    }
                    delete.conversationId.isNotBlank() -> {
                        runCatching { repository.deleteConversation(delete.conversationId) }
                            .onFailure { android.util.Log.w("RedConnectionService", "delete conversation failed: ${it.message}") }
                    }
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
    private suspend fun storeRichOrPlainMessage(message: RedProtos.ChatMessage, plaintext: ByteArray) {
        repository.saveLocalHistory(LocalHistoryEntity(message.id, message.conversationId, message.senderId, plaintext, message.type, message.timestamp, false))
        DecryptedMessageBus.publish(DecryptedMessage(message.id, message.conversationId, message.senderId, plaintext, message.timestamp, message.sequenceNumber, type = message.type))
        val preview = decodeMessagePreview(plaintext)
        // المحادثة الفردية تُدار في جدول conversations؛ رسائل المجموعة (conversationId طويل = UUID) لا تُنشئ صف محادثة فردية.
        if (message.conversationId.length <= 32) {
            runCatching { repository.onMessageStored(message.conversationId, message.senderId, preview.orEmpty(), message.timestamp, isIncoming = true) }
        }
        if (shouldNotify(message.conversationId, message.type)) {
            notifyEncryptedMessage(message.senderId, preview, message.type, message.conversationId)
        }
    }

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

    private fun shouldNotify(conversationId: String, messageType: String): Boolean {
        if (!SettingsRuntime.current.messageNotifications) return false
        val isGroup = conversationId.length > 32 || messageType == "GROUP_MESSAGE"
        if (isGroup && !SettingsRuntime.current.groupNotifications) return false
        val mutedUntil = runCatching { MessageStore(this).conversationPreference(conversationId).third }.getOrDefault(0L)
        return mutedUntil <= System.currentTimeMillis()
    }

    /** إشعار رسالة (فردية أو مجموعة) — يدعم التجميع والمعاينة المحسّنة. */
    private fun notifyEncryptedMessage(sender: String, plaintext: String?, messageType: String = "TEXT", conversationId: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        val convoId = conversationId ?: currentConversationId ?: sender
        val isGroup = convoId.length > 32
        val rawPreview = plaintext?.take(120)
        val preview = rawPreview?.takeIf { SettingsRuntime.current.notificationPreview }
        val body = preview ?: getString(com.red.sovereign.R.string.notif_new_message_body, sender)

        val builder = NotificationCompat.Builder(this, MESSAGE_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(sender, convoId))

        if (isGroup) {
            // تجميع إشعارات المجموعة في إشعار واحد قابل للتوسيع
            val groupKey = "group_$convoId"
            val groupSender = sender.take(12)
            builder
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setContentTitle(getString(com.red.sovereign.R.string.notif_group_message_title))
                .setContentText("$groupSender: $body")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$groupSender: $body"))
        } else {
            builder
                .setContentTitle(if (messageType == "VOICE") getString(com.red.sovereign.R.string.notif_voice_message_title) else getString(com.red.sovereign.R.string.notif_new_message_title))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        manager.notify(sender.hashCode(), builder.build())
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

    private fun failOutgoing(clientId: String?, conversationId: String, error: String) {
        if (clientId.isNullOrBlank()) return
        OutgoingSendBus.publish(OutgoingSendEvent(conversationId, clientId, success = false, error = error))
    }

    private fun succeedOutgoing(clientId: String?, conversationId: String, serverId: String) {
        if (clientId.isNullOrBlank()) return
        OutgoingSendBus.publish(OutgoingSendEvent(conversationId, clientId, success = true, serverId = serverId))
    }

    private fun openAppIntent(senderRedId: String? = null, conversationId: String? = null): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
        if (!senderRedId.isNullOrBlank()) i.putExtra("sender_red_id", senderRedId)
        if (!conversationId.isNullOrBlank()) i.putExtra("conversation_id", conversationId)
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(this, senderRedId?.hashCode() ?: 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

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
        private const val EXTRA_GROUP_RICH = "groupRich"
        private const val EXTRA_GROUP_TYPE = "groupType"
        private const val EXTRA_CLIENT_ID = "clientId"
        private const val OUTBOX_DIRECT = "DIRECT"
        private const val OUTBOX_GROUP = "GROUP"
        private val ALLOWED_MESSAGE_TYPES = setOf("TEXT", "RICH_TEXT", "FILE", "VOICE", "IMAGE", "VIDEO", "AUDIO", "STICKER")

        fun start(context: Context) = context.startForegroundService(Intent(context, RedConnectionService::class.java))
        fun sendText(context: Context, targetRedId: String, conversationId: String, text: String) =
            sendPayload(context, targetRedId, conversationId, "TEXT", text.toByteArray(Charsets.UTF_8))

        fun sendRichText(context: Context, targetRedId: String, conversationId: String, message: RichMessage, clientId: String? = null) =
            sendPayload(context, targetRedId, conversationId, "RICH_TEXT", RichMessage.encode(message), clientId)

        fun sendPayload(context: Context, targetRedId: String, conversationId: String, type: String, payload: ByteArray, clientId: String? = null) {
            startSendService(
                context,
                Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_PAYLOAD)
                    .putExtra(EXTRA_TARGET, targetRedId).putExtra(EXTRA_CONVERSATION, conversationId)
                    .putExtra(EXTRA_TYPE, type).putExtra(EXTRA_PAYLOAD, payload)
                    .putExtra(EXTRA_CLIENT_ID, clientId),
                clientId,
                conversationId,
            )
        }
        fun sendGroupText(context: Context, group: Group, text: String, clientId: String? = null) = startSendService(
            context,
            Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_GROUP_TEXT)
                .putExtra(EXTRA_GROUP, Json.encodeToString(group)).putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_CLIENT_ID, clientId),
            clientId,
            group.id,
        )

        /** يرسل رسالة جماعية غنية (RICH_TEXT) — تدعم الرد/الاقتباس والرسائل المؤقتة. */
        fun sendGroupRichText(context: Context, group: Group, message: RichMessage, clientId: String? = null) = startSendService(
            context,
            Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_GROUP_TEXT)
                .putExtra(EXTRA_GROUP, Json.encodeToString(group))
                .putExtra(EXTRA_TEXT, RichMessage.encode(message).toString(Charsets.UTF_8))
                .putExtra(EXTRA_GROUP_RICH, true)
                .putExtra(EXTRA_CLIENT_ID, clientId),
            clientId,
            group.id,
        )

        /** إرسال حمولة مجموعة بنوع صريح (صورة/صوت/ملصق) عبر Sender Keys — ليست رسالة نصية JSON. */
        fun sendGroupPayload(context: Context, group: Group, type: String, payload: String, clientId: String? = null) = startSendService(
            context,
            Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_GROUP_TEXT)
                .putExtra(EXTRA_GROUP, Json.encodeToString(group))
                .putExtra(EXTRA_TEXT, payload)
                .putExtra(EXTRA_GROUP_TYPE, type)
                .putExtra(EXTRA_CLIENT_ID, clientId),
            clientId,
            group.id,
        )

        private fun startSendService(context: Context, intent: Intent, clientId: String?, conversationId: String) {
            try {
                context.startForegroundService(intent)
            } catch (error: RuntimeException) {
                if (!clientId.isNullOrBlank()) {
                    OutgoingSendBus.publish(
                        OutgoingSendEvent(conversationId, clientId, success = false, error = error.message ?: "ForegroundService")
                    )
                } else {
                    throw error
                }
            }
        }

        /** إرسال تفاعل إيموجي على رسالة في محادثة فردية (E2EE ضمن حمولة RICH_TEXT). */
        fun sendReaction(context: Context, targetRedId: String, conversationId: String, messageId: String, emoji: String) =
            sendRichText(context, targetRedId, conversationId, RichMessage(action = "REACTION", reactionOf = messageId, emoji = emoji))

        /** إزالة تفاعل إيموجي على رسالة في محادثة فردية (E2EE ضمن حمولة RICH_TEXT). */
        fun removeReaction(context: Context, targetRedId: String, conversationId: String, messageId: String) =
            sendRichText(context, targetRedId, conversationId, RichMessage(action = "REACTION_REMOVE", reactionOf = messageId))

        /** إرسال تفاعل إيموجي على رسالة في مجموعة (E2EE بـ Sender Keys). */
        fun sendGroupReaction(context: Context, group: Group, messageId: String, emoji: String) =
            sendGroupRichText(context, group, RichMessage(action = "REACTION", reactionOf = messageId, emoji = emoji))

        /** إزالة تفاعل إيموجي على رسالة في مجموعة (E2EE بـ Sender Keys). */
        fun removeGroupReaction(context: Context, group: Group, messageId: String) =
            sendGroupRichText(context, group, RichMessage(action = "REACTION_REMOVE", reactionOf = messageId))

        fun markRead(context: Context, messageId: String, sequence: Long) = context.startForegroundService(
            Intent(context, RedConnectionService::class.java).setAction(ACTION_MARK_READ)
                .putExtra(EXTRA_MESSAGE_ID, messageId).putExtra(EXTRA_SEQUENCE, sequence)
        )
        fun stop(context: Context) = context.stopService(Intent(context, RedConnectionService::class.java))
    }
}
