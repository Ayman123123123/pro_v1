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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
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
    private val pendingSends = ConcurrentLinkedQueue<PendingSend>()
    private val pendingGroupSends = ConcurrentLinkedQueue<PendingGroupSend>()
    private val pendingGroupPayloadSends = ConcurrentLinkedQueue<PendingGroupPayloadSend>()
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
    private val messageStore by lazy { com.red.sovereign.core.MessageStore(applicationContext) }

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
        // ⏳ تطهير الرسائل المنتهية (disappearing) عند التشغيل ثم كل ساعة —
        // الإخفاء في الواجهة وحده كان يتركها مخزنة للأبد.
        scheduler.scheduleAtFixedRate({
            scope.launch {
                runCatching { repository.purgeExpiredMessages() }
                    .onSuccess { if (it > 0) android.util.Log.i("RedConnectionService", "purged $it expired messages") }
            }
        }, 0, 1, TimeUnit.HOURS)
        // Phase-2 reliability: re-drive 1:1 rows stuck in SENDING (process death /
        // offline kill before drainSends). Same UUID is reused so the server
        // dedups on its uuid unique index — at-least-once, never lost.
        // Group rows are skipped (re-drive needs group JSON, not stored).
        scope.launch {
            val stuck = runCatching { repository.getUnsentOutgoing() }.getOrDefault(emptyList())
            if (stuck.isEmpty()) return@launch
            var redriven = 0
            for (row in stuck) {
                if (isGroupConversation(row.conversationId)) continue
                val target = runCatching { repository.getConversation(row.conversationId)?.peerId }
                    .getOrNull()?.takeUnless { it.isBlank() } ?: row.conversationId
                pendingSends.add(PendingSend(target, row.conversationId, row.messageType, row.encryptedPlaintext, row.id))
                redriven++
            }
            if (redriven > 0) {
                android.util.Log.i("RedConnectionService", "re-driving $redriven unsent 1:1 message(s)")
                if (connected) drainSends() else socket.connect()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(CONNECTION_NOTIFICATION, connectionNotification(getString(com.red.sovereign.R.string.status_connecting)))
        if (intent?.action == ACTION_MARK_READ) {
            val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return START_STICKY
            // كان يُنفَّذ على الخيط الرئيسي (onStartCommand) وكان
            // acknowledge كان يرمي عند الانقطاع → انهيار كامل للتطبيق.
            scope.launch { socket.acknowledge(messageId, intent.getLongExtra(EXTRA_SEQUENCE, 0), "READ") }
        } else if (intent?.action == ACTION_SEND_PAYLOAD) {
            val target = intent.getStringExtra(EXTRA_TARGET) ?: return START_STICKY
            val conversation = intent.getStringExtra(EXTRA_CONVERSATION) ?: return START_STICKY
            val type = intent.getStringExtra(EXTRA_TYPE)?.takeIf { it in ALLOWED_MESSAGE_TYPES } ?: return START_STICKY
            val payload = intent.getByteArrayExtra(EXTRA_PAYLOAD)?.takeIf { it.isNotEmpty() && it.size <= 256 * 1024 } ?: return START_STICKY
            val clientId = intent.getStringExtra(EXTRA_CLIENT_ID)?.takeIf { it.isNotBlank() }
            // عرض متفائل: تُحفظ الرسالة وتظهر فوراً قبل نجاح التشفير/الإرسال.
            if (clientId != null) persistOptimistic(clientId, conversation, tokenStore.redId.orEmpty(), payload, type, target)
            pendingSends.add(PendingSend(target, conversation, type, payload, clientId))
            if (connected) drainSends() else socket.connect()
        } else if (intent?.action == ACTION_SEND_GROUP_TEXT) {
            val encodedGroup = intent.getStringExtra(EXTRA_GROUP) ?: return START_STICKY
            val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf(String::isNotBlank) ?: return START_STICKY
            val isRich = intent.getBooleanExtra(EXTRA_GROUP_RICH, false)
            val clientId = intent.getStringExtra(EXTRA_CLIENT_ID)?.takeIf { it.isNotBlank() }
            if (clientId != null) {
                val gid = runCatching { json.decodeFromString<Group>(encodedGroup).id }.getOrNull()
                if (gid != null) persistOptimistic(clientId, gid, tokenStore.redId.orEmpty(), text.toByteArray(Charsets.UTF_8), if (isRich) "RICH_TEXT" else "GROUP_MESSAGE", null)
            }
            pendingGroupSends.add(PendingGroupSend(encodedGroup, text, isRich, clientId))
            if (connected) drainGroupSends() else socket.connect()
        } else if (intent?.action == ACTION_SEND_GROUP_PAYLOAD) {
            val encodedGroup = intent.getStringExtra(EXTRA_GROUP) ?: return START_STICKY
            val type = intent.getStringExtra(EXTRA_TYPE)?.takeIf { it in ALLOWED_MESSAGE_TYPES } ?: return START_STICKY
            val payload = intent.getByteArrayExtra(EXTRA_PAYLOAD)?.takeIf { it.isNotEmpty() && it.size <= 256 * 1024 } ?: return START_STICKY
            val clientId = intent.getStringExtra(EXTRA_CLIENT_ID)?.takeIf { it.isNotBlank() }
            if (clientId != null) {
                val gid = runCatching { json.decodeFromString<Group>(encodedGroup).id }.getOrNull()
                if (gid != null) persistOptimistic(clientId, gid, tokenStore.redId.orEmpty(), payload, type, null)
            }
            pendingGroupPayloadSends.add(PendingGroupPayloadSend(encodedGroup, type, payload, clientId))
            if (connected) drainGroupPayloadSends() else socket.connect()
        } else if (intent?.action == ACTION_SEND_TYPING) {
            val conversation = intent.getStringExtra(EXTRA_CONVERSATION) ?: return START_STICKY
            val target = intent.getStringExtra(EXTRA_TARGET) // قد يكون null للمجموعات
            val isTyping = intent.getBooleanExtra(EXTRA_IS_TYPING, false)
            if (connected) {
                if (isGroupConversation(conversation)) {
                    // مجموعة: بث جماعي
                    socket.typingGroup(conversation, isTyping)
                } else {
                    if (target.isNullOrBlank()) return START_STICKY
                    socket.typing(conversation, target, isTyping)
                }
            }
        } else if (intent?.action == ACTION_DELETE) {
            val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return START_STICKY
            val conversation = intent.getStringExtra(EXTRA_CONVERSATION) ?: return START_STICKY
            val forEveryone = intent.getBooleanExtra(EXTRA_FOR_EVERYONE, false)
            scope.launch {
                try { repository.deleteReactionsForMessage(messageId) } catch (_: Exception) {}
                try { repository.deleteLocalMessage(messageId) } catch (_: Exception) {}
                try { repository.deleteMessage(messageId) } catch (_: Exception) {}
            }
            if (forEveryone) {
                // إرسال أمر حذف للجميع عبر الخادم — يبث للطرفين
                if (connected) {
                    socket.deleteMessage(messageId, conversation, true)
                } else {
                    socket.connect()
                    // محاولة ثانية بعد 800ms
                    scheduler.schedule({
                        runCatching { socket.deleteMessage(messageId, conversation, true) }
                    }, 800, TimeUnit.MILLISECONDS)
                }
                // أيضاً عبر RichMessage DELETE كاحتياط للمجموعات (SenderKey)
                // نحدد الطرف الآخر من conversationId (للمحادثة الفردية)
                scope.launch {
                    val myId = tokenStore.redId ?: return@launch
                    // حاول استنتاج target من conversationId أو من السجل قبل الحذف
                    // للتبسيط: إن كانت محادثة فردية نرسل Rich DELETE أيضاً
                    if (!isGroupConversation(conversation)) {
                        val peerId = conversation // في المحادثات الفردية conversationId = hash أو peerId — fallback
                        val richDelete = com.red.sovereign.core.RichMessage(action = "DELETE", deleteOf = messageId)
                        val payload = com.red.sovereign.core.RichMessage.encode(richDelete)
                        // لا نعرف peerId بدقة هنا فلا نرسل rich إضافي — يكفي DeleteRED للفردي
                    }
                }
            }
        } else if (intent?.action == ACTION_QUICK_REPLY) {
            val target = intent.getStringExtra(EXTRA_TARGET) ?: return START_STICKY
            val conversation = intent.getStringExtra(EXTRA_CONVERSATION) ?: return START_STICKY
            val text = intent.getStringExtra(EXTRA_QUICK_REPLY_TEXT) ?: return START_STICKY
            if (text.isNotBlank()) {
                val clientId = UuidV7.next()
                persistOptimistic(clientId, conversation, tokenStore.redId.orEmpty(), text.toByteArray(Charsets.UTF_8), "TEXT", target)
                pendingSends.add(PendingSend(target, conversation, "TEXT", text.toByteArray(Charsets.UTF_8), clientId))
                if (connected) drainSends() else socket.connect()
            }
        } else socket.connect()
        return START_STICKY
    }

    /**
     * حفظ متفائل فوري للرسائل الصادرة: تُحفظ في Room وتُبث للواجهة وتُنشئ صف
     * المحادثة لحظة الضغط على إرسال — قبل نجاح التشفير/الشبكة. عند اكتمال
     * الإرسال يُعاد استخدام نفس المعرف سلكياً فلا تكرار ولا فقدان.
     */
    private fun persistOptimistic(id: String, conversation: String, senderId: String, payload: ByteArray, type: String, peerId: String?) {
        scope.launch {
            // Phase-2 reliability: 1:1 rows (peerId != null) start as SENDING so a
            // process death before drain leaves a re-drivable marker; group rows
            // keep SENT (group re-drive needs group JSON, not stored — future work).
            // UI already renders SENDING ticks (LuxuryChatBubble).
            val initialStatus = if (peerId != null) "SENDING" else "SENT"
            runCatching { repository.saveLocalHistory(LocalHistoryEntity(id, conversation, senderId, payload, type, System.currentTimeMillis(), true, status = initialStatus)) }
                .onFailure { e -> android.util.Log.w("RedConnectionService", "optimistic save failed for $id", e) }
            DecryptedMessageBus.publish(DecryptedMessage(id, conversation, senderId, payload, System.currentTimeMillis(), 0, type = type, outgoing = true))
            if (peerId != null) {
                runCatching { repository.onMessageStored(conversation, peerId, decodeMessagePreview(payload).orEmpty(), System.currentTimeMillis(), isIncoming = false) }
                    .onFailure { e -> android.util.Log.w("RedConnectionService", "optimistic conversation row failed for $conversation", e) }
            }
        }
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
                    is ApiResult.Error -> {
                        notifyConnection(getString(com.red.sovereign.R.string.status_group_encryption_failed, prepared.message))
                        reportSendFailure(pending.clientId, group.id, null, prepared.message)
                    }
                    is ApiResult.Success -> {
                        prepared.value.distributions.forEach { distribution ->
                            socket.sendEncrypted(distribution.receiverRedId, group.id, "GROUP_KEY_DISTRIBUTION", keyManager.protocolDeviceId(), distribution.encrypted)
                        }
                        val sendType = if (pending.isRich) "RICH_TEXT" else "GROUP_MESSAGE"
                        // تفاعل الإيموجي الجماعي: يُطبّق محلياً ولا يُحفظ كرسالة
                        val outgoingRich = if (pending.isRich) com.red.sovereign.core.RichMessage.decode(pending.text.toByteArray(Charsets.UTF_8)) else null
                        if (outgoingRich?.action == "REACTION" || outgoingRich?.action == "REACTION_REMOVE") {
                            applyOutgoingReactionLocally(outgoingRich, group.id, tokenStore.redId.orEmpty())
                            return@launch
                        }
                        // AUTO-FIX (message reliability): one message UUID must NOT be reused for every
                        // fan-out target. The server keys messages by uuid (unique index) and rejects a
                        // repeated uuid addressed to a different receiver/device ("Message UUID collision"),
                        // so every member/device except the first silently lost the message. Only the first
                        // copy keeps the clientId (it matches the local optimistic row); the rest get fresh ids.
                        var fanoutClientIdUsed = false
                        var firstId: String? = null
                        prepared.value.recipients.forEach { recipient ->
                            val envelope = prepared.value.groupCiphertext.copy(receiverDeviceId = recipient.protocolDeviceId)
                            val id = socket.sendEncrypted(recipient.redId, group.id, sendType, keyManager.protocolDeviceId(), envelope, if (fanoutClientIdUsed) null else pending.clientId.also { fanoutClientIdUsed = true })
                            if (firstId == null) firstId = id
                        }
                        firstId?.let {
                            // عرض متفائل مسبق (clientId) → لا حفظ ولا بث مكرر.
                            if (pending.clientId != null) return@launch
                            val bytes = pending.text.toByteArray(Charsets.UTF_8); val timestamp = System.currentTimeMillis()
                            repository.saveLocalHistory(LocalHistoryEntity(it, group.id, tokenStore.redId.orEmpty(), bytes, sendType, timestamp, true))
                            DecryptedMessageBus.publish(DecryptedMessage(it, group.id, tokenStore.redId.orEmpty(), bytes, timestamp, 0, type = sendType, outgoing = true))
                        }
                    }
                }
            }
        }
    }

    private fun drainGroupPayloadSends() {
        while (connected) {
            val pending = pendingGroupPayloadSends.poll() ?: break
            val group = runCatching { json.decodeFromString<Group>(pending.groupJson) }.getOrNull() ?: continue
            scope.launch {
                when (val prepared = groupCrypto.prepare(group, pending.payload)) {
                    is ApiResult.Error -> {
                        notifyConnection(getString(com.red.sovereign.R.string.status_group_encryption_failed, prepared.message))
                        reportSendFailure(pending.clientId, group.id, null, prepared.message)
                    }
                    is ApiResult.Success -> {
                        prepared.value.distributions.forEach { distribution ->
                            socket.sendEncrypted(distribution.receiverRedId, group.id, "GROUP_KEY_DISTRIBUTION", keyManager.protocolDeviceId(), distribution.encrypted)
                        }
                        val sendType = pending.type
                        // AUTO-FIX (message reliability): one message UUID must NOT be reused for every
                        // fan-out target. The server keys messages by uuid (unique index) and rejects a
                        // repeated uuid addressed to a different receiver/device ("Message UUID collision"),
                        // so every member/device except the first silently lost the message. Only the first
                        // copy keeps the clientId (it matches the local optimistic row); the rest get fresh ids.
                        var fanoutClientIdUsed = false
                        var firstId: String? = null
                        prepared.value.recipients.forEach { recipient ->
                            val envelope = prepared.value.groupCiphertext.copy(receiverDeviceId = recipient.protocolDeviceId)
                            val id = socket.sendEncrypted(recipient.redId, group.id, sendType, keyManager.protocolDeviceId(), envelope, if (fanoutClientIdUsed) null else pending.clientId.also { fanoutClientIdUsed = true })
                            if (firstId == null) firstId = id
                        }
                        firstId?.let {
                            if (pending.clientId != null) return@launch
                            val timestamp = System.currentTimeMillis()
                            repository.saveLocalHistory(LocalHistoryEntity(it, group.id, tokenStore.redId.orEmpty(), pending.payload, sendType, timestamp, true))
                            DecryptedMessageBus.publish(DecryptedMessage(it, group.id, tokenStore.redId.orEmpty(), pending.payload, timestamp, 0, type = sendType, outgoing = true))
                        }
                    }
                }
            }
        }
    }

    private fun sendEncryptedPayload(pending: PendingSend) {
        scope.launch {
            when (val encrypted = signal.encrypt(pending.target, pending.payload)) {
                is ApiResult.Error -> {
                    notifyConnection(getString(com.red.sovereign.R.string.status_encryption_failed, encrypted.message))
                    reportSendFailure(pending.clientId, pending.conversation, pending.target, encrypted.message)
                }
                is ApiResult.Success -> {
                    // AUTO-FIX (message reliability): one message UUID must NOT be reused for every
                    // fan-out target. The server keys messages by uuid (unique index) and rejects a
                    // repeated uuid addressed to a different receiver/device ("Message UUID collision"),
                    // so every member/device except the first silently lost the message. Only the first
                    // copy keeps the clientId (it matches the local optimistic row); the rest get fresh ids.
                    var fanoutClientIdUsed = false
                    var firstId: String? = null
                    encrypted.value.forEach { envelope ->
                        val id = socket.sendEncrypted(pending.target, pending.conversation, pending.type, keyManager.protocolDeviceId(), envelope, if (fanoutClientIdUsed) null else pending.clientId.also { fanoutClientIdUsed = true })
                        if (firstId == null) firstId = id
                    }
                    firstId?.let {
                        // تفاعل الإيموجي: يُطبّق محلياً ولا يُحفظ كرسالة
                        val rich = com.red.sovereign.core.RichMessage.decode(pending.payload)
                        if (rich?.action == "REACTION" || rich?.action == "REACTION_REMOVE") {
                            applyOutgoingReactionLocally(rich, pending.conversation, tokenStore.redId.orEmpty())
                            return@launch
                        }
                        // عرض متفائل مسبق (clientId) → يُكتفى بتحديث صف المحادثة.
                        if (pending.clientId != null) {
                            val timestamp = System.currentTimeMillis()
                            // Phase-2 reliability: optimistic row was SENDING — confirm SENT now
                            // that bytes hit the socket (ACK/READ still advance it further).
                            runCatching { repository.updateMessageStatus(pending.clientId, "SENT") }
                            runCatching { repository.onMessageStored(pending.conversation, pending.target, decodeMessagePreview(pending.payload).orEmpty(), timestamp, isIncoming = false) }
                            return@launch
                        }
                        val timestamp = System.currentTimeMillis()
                        repository.saveLocalHistory(LocalHistoryEntity(it, pending.conversation, tokenStore.redId.orEmpty(), pending.payload, pending.type, timestamp, true))
                        DecryptedMessageBus.publish(DecryptedMessage(it, pending.conversation, tokenStore.redId.orEmpty(), pending.payload, timestamp, sequence = 0, type = pending.type, outgoing = true))
                        // تحديث/إنشاء صف المحادثة لتظهر في قائمة الدردشات
                        runCatching { repository.onMessageStored(pending.conversation, pending.target, decodeMessagePreview(pending.payload).orEmpty(), timestamp, isIncoming = false) }
                    }
                }
            }
        }
    }

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
                drainSends()
                drainGroupSends()
                drainGroupPayloadSends()
                scope.launch { catchUpMissedMessages() }
                runCatching { com.red.sovereign.calls.PendingOfferPoller.pollNow(applicationContext) }; runCatching { com.red.sovereign.calls.PendingOfferPoller.schedule(applicationContext) }
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
        // الحارس: إذا أُنهي المجدول (الخدمة قيد التدمير) لا تخطط لإعادة اتصال —
        // السباق السابق مع onClosed كان يرمي RejectedExecutionException على
        // مؤشر OkHttp فينهار التطبيق كاملاً.
        if (scheduler.isShutdown || scheduler.isTerminated) return
        val delay = minOf(60L, 1L shl minOf(attempts++, 6))
        notifyConnection(getString(com.red.sovereign.R.string.status_disconnected_retry, delay))
        reconnectTask = try {
            scheduler.schedule({
                // بعد فشل متكرر: عنوان IP للخادم قد يكون تغيّر (DHCP) —
                // أعد اكتشافه على الشبكة المحلية (تحقق صريح) ثم اتصل.
                if (attempts == 4 || attempts == 10) scope.launch { rediscoverAndConnect() } else socket.connect()
            }, delay, TimeUnit.SECONDS)
        } catch (_: RejectedExecutionException) {
            null
        }
    }

    /** إعادة اكتشاف عنوان الخادم على الشبكة المحلية عند فشل الاتصال المتكرر. */
    private suspend fun rediscoverAndConnect() {
        // AUTO-FIX (network resilience): FAST discovery only probes a static candidate list, so a
        // changed server IP used to end in YOUNES_SERVER_NOT_FOUND. Escalate to the full /24 + mDNS
        // scan (THOROUGH) before giving up; FAST already tries the last known-good endpoint first.
        val discovery = LocalServerDiscovery(applicationContext)
        var found = discovery.discover(LocalServerDiscovery.Mode.FAST)
        if (found !is ApiResult.Success) {
            found = discovery.discover(LocalServerDiscovery.Mode.THOROUGH)
        }
        if (found is ApiResult.Success) {
            attempts = 0
            notifyConnection(getString(com.red.sovereign.R.string.status_connecting_local))
        }
        socket.connect()
    }

    /** AUTO-FIX (message reliability): pull messages stored for us while offline and feed them
     * through the normal envelope pipeline (decrypt + store + notify). */
    private suspend fun catchUpMissedMessages() {
        runCatching {
            val client = com.red.sovereign.auth.AuthorizedApiClient(tokenStore)
            val store = com.red.sovereign.core.SecureStore(applicationContext, "red_catchup")
            var cursor = store.get("since")?.toLongOrNull() ?: (System.currentTimeMillis() - 48L * 3600_000L)
            // AUTO-FIX (message reliability): page through the whole backlog and advance the cursor to
            // the newest message actually returned - never blindly to `now`. The previous version set
            // `since = now` even when the server truncated the page at `limit`, permanently dropping
            // every message beyond the first page and anything that arrived during the pull.
            var pages = 0
            while (pages < CATCHUP_MAX_PAGES) {
                pages++
                val result = client.request("GET", "/api/messages/catchup?since=$cursor&limit=$CATCHUP_PAGE_SIZE")
                if (result !is ApiResult.Success) break
                val arr = org.json.JSONArray(result.value)
                if (arr.length() == 0) break
                var newest = cursor
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val b64 = obj.optString("envelope")
                    if (b64.isBlank()) continue
                    runCatching {
                        val env = RedProtos.RedRED.parseFrom(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                        if (env.hasMessage()) {
                            handleEnvelope(env)
                            if (env.message.timestamp > newest) newest = env.message.timestamp
                        }
                    }
                }
                // Stop when the page made no forward progress (avoid an infinite loop).
                if (newest <= cursor) break
                cursor = newest
                if (arr.length() < CATCHUP_PAGE_SIZE) break
            }
            store.put("since", cursor.toString())
        }
    }

    private fun onEnvelope(envelope: RedProtos.RedRED) {
        scope.launch { handleEnvelope(envelope) }
    }

    private suspend fun handleEnvelope(envelope: RedProtos.RedRED) {
        when (envelope.signalCase) {
            RedProtos.RedRED.SignalCase.MESSAGE -> {
                val message = envelope.message
                if (message.receiverId == tokenStore.redId && message.receiverDeviceId == keyManager.protocolDeviceId()) {
                    val isGroupConversation = isGroupConversation(message.conversationId)
                    val plaintext = try {
                        repository.saveIncomingMessage(message)
                        when (message.type) {
                            // توزيع مفاتيح المجموعة يُشفَّر زوجياً لكل عضو (ليس SenderKey)
                            "GROUP_KEY_DISTRIBUTION" -> signal.decrypt(message.senderId, message.senderDeviceId, message.ciphertextType, message.payload.toByteArray())
                            "RICH_TEXT" -> {
                                // رسالة غنية: قد تكون فردية أو جماعية. نحسم عبر isGroupConversation (prefix صريح).
                                if (isGroupConversation) {
                                    groupCrypto.decrypt(message.senderId, message.senderDeviceId, message.payload.toByteArray())
                                } else {
                                    signal.decrypt(message.senderId, message.senderDeviceId, message.ciphertextType, message.payload.toByteArray())
                                }
                            }
                            // وسائط المجموعة (صورة/فيديو/صوت/ملف/ملصق) تُشفَّر بـ SenderKeys كـ GROUP_MESSAGE
                            else -> if (isGroupConversation) {
                                groupCrypto.decrypt(message.senderId, message.senderDeviceId, message.payload.toByteArray())
                            } else {
                                signal.decrypt(message.senderId, message.senderDeviceId, message.ciphertextType, message.payload.toByteArray())
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.w("RedConnectionService", "decrypt failed for ${message.id}: ${t.message}")
                        null
                    }
                    if (plaintext == null) {
                        // Phase-1 (2026-09-14): عنصر نائب بدل الإسقاط الصامت — بلا ACK
                        // فيعيد السيرفر التسليم تلقائياً وتُحاوَل إعادة الفك.
                        saveDecryptPlaceholder(message)
                        return
                    }
                    // هل كان لهذه الرسالة عنصر نائب؟ (نجاح متأخر بعد فشل سابق)
                    val wasPlaceholder = runCatching {
                        repository.getLocalHistoryEntry(message.id)?.let { isPendingDecryptPlaceholder(it.encryptedPlaintext) } == true
                    }.getOrDefault(false)
                    if (plaintext != null) {
                        if (message.type == "GROUP_KEY_DISTRIBUTION") {
                            groupCrypto.processDistribution(message.senderId, message.senderDeviceId, plaintext)
                        } else if (message.type == "RICH_TEXT") {
                            val rich = com.red.sovereign.core.RichMessage.decode(plaintext)
                            // صوت استطلاع: يُخزَّن للمزامنة المحلية دون إشعار المستخدم
                            if (rich?.action == "POLL_VOTE") {
                                repository.saveLocalHistory(LocalHistoryEntity(message.id, message.conversationId, message.senderId, plaintext, message.type, message.timestamp, false))
                                DecryptedMessageBus.publish(DecryptedMessage(message.id, message.conversationId, message.senderId, plaintext, message.timestamp, message.sequenceNumber, type = message.type))
                                socket.acknowledge(message.id, message.sequenceNumber, "DELIVERED")
                            } else when (rich?.action) {
                                // التعديل: تحديث نص الرسالة الأصلية في التخزين (لا إضافة سجل جديد).
                                // 🔐 يُطبق فقط إن كان مُرسل التعديل هو مالك الرسالة الأصلية + ضمن 24h.
                                "EDIT" -> rich.editOf?.let { editOf ->
                                    val original = repository.getLocalHistoryEntry(editOf)
                                    if (original != null && original.senderId == message.senderId &&
                                        message.timestamp >= original.createdAt &&
                                        message.timestamp - original.createdAt <= EDIT_WINDOW_MS
                                    ) {
                                        repository.updateLocalHistoryText(editOf, com.red.sovereign.core.RichMessage.encode(rich.copy(replyTo = rich.replyTo)))
                                        DecryptedMessageBus.publish(DecryptedMessage(editOf, message.conversationId, message.senderId, com.red.sovereign.core.RichMessage.encode(rich), message.timestamp, message.sequenceNumber, type = "RICH_TEXT"))
                                    }
                                }
                                // الحذف للجميع: حذف الرسالة الأصلية من التخزين.
                                // 🔐 يُطبق فقط إن كان مُرسل الحذف هو مالك الرسالة الأصلية (منع حذف رسائل الغير).
                                "DELETE" -> rich.deleteOf?.let { deleteOf ->
                                    val original = repository.getLocalHistoryEntry(deleteOf)
                                    if (original != null && original.senderId == message.senderId) {
                                        repository.deleteLocalMessage(deleteOf)
                                        repository.deleteReactionsForMessage(deleteOf)
                                        DecryptedMessageBus.publish(DecryptedMessage(message.id, message.conversationId, message.senderId, plaintext, message.timestamp, message.sequenceNumber, type = "RICH_TEXT"))
                                    }
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
                            if (!isGroupConversation && message.type != "GROUP_MESSAGE") {
                                try { repository.onMessageStored(message.conversationId, message.senderId, preview.orEmpty(), message.timestamp, isIncoming = true) } catch (_: Throwable) { }
                            }
                            if (SettingsRuntime.current.messageNotifications) {
                                currentConversationId = message.conversationId
                                notifyEncryptedMessage(message.senderId, preview, message.type)
                            }
                            socket.acknowledge(message.id, message.sequenceNumber, "DELIVERED")
                        }
                    }
                    // Phase-1 (2026-09-14): نجح الفك بعد عنصر نائب → ادفع المحتوى الحقيقي للواجهة.
                    if (wasPlaceholder) {
                        publishPlaceholderResolution(message)
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
                val messageId = delete.messageId
                if (messageId.isNotEmpty()) {
                    // حذف محلي فقط — لا يُحذف من الخادم
                    try { repository.deleteLocalMessage(messageId) } catch (t: Throwable) { android.util.Log.w("RedConnectionService", "delete failed for $messageId: ${t.message}") }
                } else if (delete.conversationId.isNotEmpty()) {
                    try { repository.deleteConversation(delete.conversationId) } catch (t: Throwable) { android.util.Log.w("RedConnectionService", "delete conv failed: ${t.message}") }
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
    /**
     * Phase-1 (2026-09-14): يحفظ عنصراً نائباً لرسالة تعذّر فكها — مرة واحدة لكل معرف.
     * يحدّث صف المحادثة (لتظهر في القائمة) وينشر للواجهة، دون إشعار فوري (يأتي مع الفك
     * الحقيقي) ودون ACK إطلاقاً (شرط إعادة التسليم التلقائي من السيرفر).
     */
    private suspend fun saveDecryptPlaceholder(message: RedProtos.ChatMessage) {
        // توزيع المفاتيح ليس رسالة مرئية — لا عنصر نائب له (يُعاد تسليمه ويُعالج بصمت).
        if (message.type == "GROUP_KEY_DISTRIBUTION") return
        if (runCatching { repository.getLocalHistoryEntry(message.id) }.getOrNull() != null) return
        val payload = pendingDecryptPayload()
        repository.saveLocalHistory(
            LocalHistoryEntity(message.id, message.conversationId, message.senderId, payload, message.type, message.timestamp, false)
        )
        DecryptedMessageBus.publish(
            DecryptedMessage(message.id, message.conversationId, message.senderId, payload, message.timestamp, message.sequenceNumber, type = message.type)
        )
        val peerId = if (isGroupConversation(message.conversationId)) message.conversationId else message.senderId
        runCatching {
            repository.onMessageStored(message.conversationId, peerId, pendingDecryptDisplayText(), message.timestamp, isIncoming = true)
        }
    }

    /**
     * Phase-1: بعد نجاح فك رسالة كان لها عنصر نائب — اقرأ السجل النهائي
     * (بعد كل فروع المعالجة) وانشره كتحديث استبدالي للواجهة.
     * إن بقي السجل نائباً (مغلف إجراء بلا محتوى مرئي كـ REACTION) يُحذف بصمت.
     */
    private suspend fun publishPlaceholderResolution(message: RedProtos.ChatMessage) {
        val finalEntry = runCatching { repository.getLocalHistoryEntry(message.id) }.getOrNull() ?: return
        if (isPendingDecryptPlaceholder(finalEntry.encryptedPlaintext)) {
            // مغلف إجراء (تفاعل/تعديل/حذف) لا محتوى مرئي له — أزل النائب.
            runCatching { repository.deleteLocalMessage(finalEntry.id) }
            return
        }
        com.red.sovereign.crypto.DecryptedMessageUpdateBus.publish(
            DecryptedMessage(
                finalEntry.id, finalEntry.conversationId, finalEntry.senderId,
                finalEntry.encryptedPlaintext, finalEntry.createdAt,
                message.sequenceNumber, type = finalEntry.messageType
            )
        )
    }

    /**
     * Phase-1 (2026-09-14): فشل إرسال صادر — يُعلَّم الصف المتفائل FAILED (يظهر ⚠ في الفقاعة)
     * ويُبث للواجهة لتنبيه المستخدم، بدل الضياع في سجل الحالة.
     */
    private fun reportSendFailure(clientId: String?, conversationId: String, targetId: String?, errorMessage: String) {
        if (clientId != null) {
            scope.launch {
                runCatching { repository.updateMessageStatus(clientId, "FAILED") }
                com.red.sovereign.crypto.MessageAckBus.publish(com.red.sovereign.crypto.MessageAck(clientId, "FAILED"))
            }
        }
        com.red.sovereign.crypto.MessageSendErrorBus.publish(
            com.red.sovereign.crypto.SendError(conversationId, targetId, errorMessage, sendFailureArabic(errorMessage))
        )
    }

    /** ترجمة رموز فشل التشفير/الدليل إلى عربية مفهومة. */
    private fun sendFailureArabic(code: String): String = when {
        code.contains("NO_APPROVED_REMOTE_DEVICE") -> "تعذّر الإرسال: الطرف الآخر لم يُكمل إعداد جهازه بعد"
        code.contains("DIRECTORY_FETCH_FAILED") -> "تعذّر الوصول لدليل الهوية — تحقق من الاتصال بالسيرفر"
        code.contains("PREKEY_CONSUME_FAILED") -> "تعذّر بدء جلسة التشفير — أعد المحاولة بعد لحظات"
        code.contains("GROUP") && code.contains("KEY") -> "تعذّر تجهيز مفاتيح المجموعة — أعد المحاولة"
        code.startsWith("HTTP_") -> "تعذّر الإرسال: خطأ شبكة ($code)"
        code.contains("NETWORK") -> "تعذّر الإرسال: انقطع الاتصال — ستُرسل تلقائياً عند عودته"
        else -> "فشل الإرسال ($code)"
    }

    private suspend fun storeRichOrPlainMessage(message: RedProtos.ChatMessage, plaintext: ByteArray) {
        repository.saveLocalHistory(LocalHistoryEntity(message.id, message.conversationId, message.senderId, plaintext, message.type, message.timestamp, false))
        DecryptedMessageBus.publish(DecryptedMessage(message.id, message.conversationId, message.senderId, plaintext, message.timestamp, message.sequenceNumber, type = message.type))
        val preview = decodeMessagePreview(plaintext)
        // Single source of truth for group detection (covers grp_/group-/group_/:group:).
        val isGroup = message.type == "GROUP_MESSAGE" ||
                isGroupConversation(message.conversationId)
        if (!isGroup) {
            // المحادثة الفردية تُدار في جدول conversations مع تتبع غير المقروء والمستلم الحقيقي
            val peerId = if (message.senderId == tokenStore.redId) message.conversationId else message.senderId
            runCatching { repository.onMessageStored(message.conversationId, peerId, preview.orEmpty(), message.timestamp, isIncoming = true) }
        } else {
            // المجموعات: صف محادثة بمعرف المجموعة نفسه لتتبع عدد غير المقروء عبر عمليات إعادة التشغيل.
            runCatching { repository.onMessageStored(message.conversationId, message.conversationId, preview.orEmpty(), message.timestamp, isIncoming = true) }
        }
        if (SettingsRuntime.current.messageNotifications && !isConversationMuted(message.conversationId)) {
            notifyEncryptedMessage(message.senderId, preview, message.type)
        }
    }

    /** هل المحادثة (فردية أو مجموعة) مكتومة حالياً؟ يقرأ تفضيل muted_until. */
    private suspend fun isConversationMuted(conversationId: String): Boolean = runCatching {
        val now = System.currentTimeMillis()
        // المخزن الأول: MessageStore (تفضيلات المحادثة,F الكتم من شاشة المجموعة/الخاص).
        val viaPrefs = messageStore.conversationPreference(conversationId).third > now
        if (viaPrefs) return true
        // المخزن الثاني: جدول conversations (الكتم من قائمة الدردشات/الأرشيف).
        // كان يُفحص الأول فقط فتصل إشعارات لمحادثة كُتمت من القائمة.
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dao = com.red.sovereign.core.database.RedDatabase.getInstance(applicationContext).redDao()
            (dao.getConversation(conversationId)?.mutedUntil ?: 0L) > System.currentTimeMillis()
        }
    }.getOrDefault(false)

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

    /** إشعار رسالة (فردية أو مجموعة) — يدعم التجميع والمعاينة المحسّنة والرد السريع. */
    private fun notifyEncryptedMessage(sender: String, plaintext: String?, messageType: String = "TEXT") {
        val manager = getSystemService(NotificationManager::class.java)
        val convoId = currentConversationId ?: sender
        val isGroup = isGroupConversation(convoId)
        val rawPreview = plaintext?.take(120)
        val preview = rawPreview?.takeIf { SettingsRuntime.current.notificationPreview }
        val body = preview ?: getString(com.red.sovereign.R.string.notif_new_message_body, sender)

        val builder = NotificationCompat.Builder(this, MESSAGE_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(sender, convoId))

        // ✅ إضافة زر الرد السريع (Quick Reply)
        val replyIntent = Intent(this, RedConnectionService::class.java).apply {
            action = ACTION_QUICK_REPLY
            putExtra(EXTRA_TARGET, sender)
            putExtra(EXTRA_CONVERSATION, convoId)
        }
        val replyPendingIntent = PendingIntent.getService(
            this, convoId.hashCode(), replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = androidx.core.app.RemoteInput.Builder(EXTRA_QUICK_REPLY_TEXT)
            .setLabel("اكتب رداً...")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "رد سريع", replyPendingIntent
        ).addRemoteInput(remoteInput).build()
        builder.addAction(replyAction)

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
        socket.disconnect()
        reconnectTask?.cancel(true)
        scheduler.shutdownNow()
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CONNECTION_CHANNEL = "red_connection"
        private const val MESSAGE_CHANNEL = "red_messages"
        private const val CONNECTION_NOTIFICATION = 7001
        private const val ACTION_SEND_PAYLOAD = "com.red.sovereign.SEND_PAYLOAD"
        const val ACTION_MARK_READ = "com.red.sovereign.MARK_READ"
        private const val ACTION_SEND_GROUP_TEXT = "com.red.sovereign.SEND_GROUP_TEXT"
        private const val ACTION_SEND_GROUP_PAYLOAD = "com.red.sovereign.SEND_GROUP_PAYLOAD"
        const val ACTION_SEND_TYPING = "com.red.sovereign.SEND_TYPING"
        const val ACTION_QUICK_REPLY = "com.red.sovereign.QUICK_REPLY"
        const val EXTRA_MESSAGE_ID = "msgId"
        const val EXTRA_TARGET = "target"
        const val EXTRA_CONVERSATION = "conversation"
        /** معرف العميل للعرض المتفائل: تُولّده الواجهة، تُحفظ به الرسالة فوراً، ويُعاد استخدامه سلكياً. */
        const val EXTRA_CLIENT_ID = "clientId"
        const val EXTRA_SEQUENCE = "sequence"
        const val EXTRA_IS_TYPING = "isTyping"
        const val EXTRA_QUICK_REPLY_TEXT = "quickReplyText"
        private const val EXTRA_PAYLOAD = "payload"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_GROUP = "group"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_GROUP_RICH = "groupRich"
        private val ALLOWED_MESSAGE_TYPES = setOf("TEXT", "RICH_TEXT", "FILE", "VOICE", "IMAGE", "VIDEO", "AUDIO", "STICKER")
        /** حد زمني للتعديل الوارد: 24 ساعة من إنشاء الرسالة الأصلية. */
        private const val EDIT_WINDOW_MS = 24L * 60L * 60L * 1000L
        // AUTO-FIX (message reliability): offline catch-up page size / hard page cap.
        private const val CATCHUP_PAGE_SIZE = 200
        private const val CATCHUP_MAX_PAGES = 25

        /**
         * هل المحادثة مجموعة؟ يعتمد على prefix صريح بدل magic number (length>32).
         * TODO: ربطه بـ GroupDocument/جدول groups لاحقاً (lookup صريح) بدل الاعتماد على prefix فقط.
         */
        fun isGroupConversation(conversationId: String): Boolean =
            conversationId.startsWith("grp_") ||
                conversationId.startsWith("group-", ignoreCase = true) ||
                conversationId.startsWith("group_", ignoreCase = true) ||
                conversationId.contains(":group:")

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, RedConnectionService::class.java))
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.w("RedConnectionService", "start failed: not allowed in background", e)
            }
        }
        fun sendText(context: Context, targetRedId: String, conversationId: String, text: String, clientId: String? = null) =
            sendPayload(context, targetRedId, conversationId, "TEXT", text.toByteArray(Charsets.UTF_8), clientId ?: UuidV7.next())

        fun sendRichText(context: Context, targetRedId: String, conversationId: String, message: RichMessage, clientId: String? = null) =
            sendPayload(context, targetRedId, conversationId, "RICH_TEXT", RichMessage.encode(message), clientId ?: UuidV7.next())

        fun sendPayload(context: Context, targetRedId: String, conversationId: String, type: String, payload: ByteArray, clientId: String? = null) {
            val cid = clientId ?: UuidV7.next()
            try {
                context.startForegroundService(
                    Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_PAYLOAD)
                        .putExtra(EXTRA_TARGET, targetRedId).putExtra(EXTRA_CONVERSATION, conversationId)
                        .putExtra(EXTRA_TYPE, type).putExtra(EXTRA_PAYLOAD, payload)
                        .putExtra(EXTRA_CLIENT_ID, cid)
                )
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.w("RedConnectionService", "sendPayload failed: not allowed in background", e)
            }
        }
        fun sendGroupText(context: Context, group: Group, text: String, clientId: String? = null) {
            val cid = clientId ?: UuidV7.next()
            try {
                context.startForegroundService(
                    Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_GROUP_TEXT)
                        .putExtra(EXTRA_GROUP, Json.encodeToString(group)).putExtra(EXTRA_TEXT, text)
                        .putExtra(EXTRA_CLIENT_ID, cid)
                )
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.w("RedConnectionService", "sendGroupText failed: not allowed in background", e)
            }
        }

        fun sendGroupPayload(context: Context, group: Group, type: String, payload: ByteArray, clientId: String? = null) {
            val cid = clientId ?: UuidV7.next()
            try {
                context.startForegroundService(
                    Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_GROUP_PAYLOAD)
                        .putExtra(EXTRA_GROUP, Json.encodeToString(group))
                        .putExtra(EXTRA_TYPE, type)
                        .putExtra(EXTRA_PAYLOAD, payload)
                        .putExtra(EXTRA_CLIENT_ID, cid)
                )
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.w("RedConnectionService", "sendGroupPayload failed: not allowed in background", e)
            }
        }

        /** يرسل رسالة جماعية غنية (RICH_TEXT) — تدعم الرد/الاقتباس والرسائل المؤقتة. */
        fun sendGroupRichText(context: Context, group: Group, message: RichMessage, clientId: String? = null) {
            val cid = clientId ?: UuidV7.next()
            try {
                context.startForegroundService(
                    Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_GROUP_TEXT)
                        .putExtra(EXTRA_GROUP, Json.encodeToString(group)).putExtra(EXTRA_TEXT, RichMessage.encode(message).toString(Charsets.UTF_8)).putExtra(EXTRA_GROUP_RICH, true)
                        .putExtra(EXTRA_CLIENT_ID, cid)
                )
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.w("RedConnectionService", "sendGroupRichText failed: not allowed in background", e)
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

        /**
         * تصويت على استطلاع دردشة فردي (E2EE): `optionIndex = null` تعني
         * سحب الصوت. تُجمَع الأصوات من هذه الرسائل عبر ChatPollVoteStore.
         */
        fun sendPollVote(context: Context, targetRedId: String, conversationId: String, pollId: String, optionIndex: Int?) =
            sendRichText(context, targetRedId, conversationId, RichMessage(action = "POLL_VOTE", pollVoteOf = pollId, pollVoteOption = optionIndex))

        /** تصويت على استطلاع مجموعة (E2EE بـ Sender Keys) — null لسحب الصوت. */
        fun sendGroupPollVote(context: Context, group: Group, pollId: String, optionIndex: Int?) =
            sendGroupRichText(context, group, RichMessage(action = "POLL_VOTE", pollVoteOf = pollId, pollVoteOption = optionIndex))

        /** إزالة تفاعل إيموجي على رسالة في مجموعة (E2EE بـ Sender Keys). */
        fun removeGroupReaction(context: Context, group: Group, messageId: String) =
            sendGroupRichText(context, group, RichMessage(action = "REACTION_REMOVE", reactionOf = messageId))

        fun markRead(context: Context, messageId: String, sequence: Long) {
            try {
                context.startForegroundService(
                    Intent(context, RedConnectionService::class.java).setAction(ACTION_MARK_READ)
                        .putExtra(EXTRA_MESSAGE_ID, messageId).putExtra(EXTRA_SEQUENCE, sequence)
                )
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.w("RedConnectionService", "markRead failed: not allowed in background", e)
            }
        }

        /** إرسال مؤشر كتابة — فردي (target مطلوب) أو جماعي (target فارغ). */
        fun sendTyping(context: Context, conversationId: String, targetRedId: String?, isTyping: Boolean) {
            try {
                context.startForegroundService(
                    Intent(context, RedConnectionService::class.java).setAction(ACTION_SEND_TYPING)
                        .putExtra(EXTRA_CONVERSATION, conversationId).putExtra(EXTRA_TARGET, targetRedId).putExtra(EXTRA_IS_TYPING, isTyping)
                )
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.w("RedConnectionService", "sendTyping failed: not allowed in background", e)
            }
        }

        /** اختصار للمجموعات */
        fun sendGroupTyping(context: Context, groupId: String, isTyping: Boolean) = sendTyping(context, groupId, null, isTyping)

        const val ACTION_DELETE = "com.red.sovereign.DELETE_MESSAGE"
        const val EXTRA_FOR_EVERYONE = "forEveryone"

        /** حذف للجميع — يُرسل DeleteRED عبر الخادم ليصل للطرفين */
        fun deleteForEveryone(context: Context, messageId: String, conversationId: String) {
            try {
                context.startForegroundService(
                    Intent(context, RedConnectionService::class.java).setAction(ACTION_DELETE)
                        .putExtra(EXTRA_MESSAGE_ID, messageId).putExtra(EXTRA_CONVERSATION, conversationId).putExtra(EXTRA_FOR_EVERYONE, true)
                )
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.w("RedConnectionService", "deleteForEveryone failed: not allowed in background", e)
            }
        }

        /** حذف لدي فقط — محلي */
        fun deleteForMe(context: Context, messageId: String, conversationId: String) {
            try {
                context.startForegroundService(
                    Intent(context, RedConnectionService::class.java).setAction(ACTION_DELETE)
                        .putExtra(EXTRA_MESSAGE_ID, messageId).putExtra(EXTRA_CONVERSATION, conversationId).putExtra(EXTRA_FOR_EVERYONE, false)
                )
            } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                android.util.Log.w("RedConnectionService", "deleteForMe failed: not allowed in background", e)
            }
        }

        fun stop(context: Context) = context.stopService(Intent(context, RedConnectionService::class.java))
    }
}

private data class PendingSend(val target: String, val conversation: String, val type: String, val payload: ByteArray, val clientId: String? = null)
private data class PendingGroupSend(val groupJson: String, val text: String, val isRich: Boolean = false, val clientId: String? = null)
private data class PendingGroupPayloadSend(val groupJson: String, val type: String, val payload: ByteArray, val clientId: String? = null)
