package com.red.server.calls

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * RED Master Live Stream Service
 * Tracks active streams, broadcasters, and audience counts for the running SFU session.
 * Durable stream history belongs in analytics; active WebRTC sessions cannot survive a process restart.
 *
 * Legendary V2: category/visibility/slow-mode/pin/co-hosts/moderation/analytics/VOD pointers.
 * All new fields have defaults -> old Mongo docs still load (no migration break).
 */
@Document("live_streams")
@CompoundIndexes(
    CompoundIndex(name = "broadcaster_ended_idx", def = "{'broadcasterId': 1, 'endedAt': 1}"),
    CompoundIndex(name = "visibility_started_idx", def = "{'visibility': 1, 'startedAt': -1}"),
    CompoundIndex(name = "category_ended_idx", def = "{'category': 1, 'endedAt': 1}"),
    CompoundIndex(name = "title_text_idx", def = "{'title': 'text', 'broadcasterName': 'text'}")
)
data class LiveStreamRecord(
    @Id val streamId: String,
    @Indexed val broadcasterId: String,
    val broadcasterName: String = "",
    val broadcasterRedId: String = "",
    val title: String = "",
    val isPrivate: Boolean = false,
    val passwordHash: String? = null,
    val startedAt: Instant = Instant.now(),
    var endedAt: Instant? = null,
    // ── Legendary V2 additions (defaults keep backward compat) ──
    val category: String = "عام",
    val description: String = "",
    val visibility: String = "PUBLIC",
    val thumbnailUrl: String? = null,
    val broadcasterAvatar: String? = null,
    var peakViewers: Int = 0,
    var totalMessages: Long = 0,
    var totalReactions: Long = 0,
    var slowModeSec: Int = 0,
    var pinnedChatId: String? = null,
    var pinnedText: String? = null,
    var coHostIds: MutableList<String> = mutableListOf(),
    var mutedIds: MutableList<String> = mutableListOf(),
    var bannedIds: MutableList<String> = mutableListOf(),
    var blockedWords: MutableList<String> = mutableListOf(),
    var recordingEnabled: Boolean = false,
    var hlsUrl: String? = null,
    var vodUrl: String? = null,
    var dvrWindowSec: Int = 7200
) {
    var viewerCount: Int = 0
}

@Service
class LiveStreamService(
    private val passwordHasher: RoomPasswordHasher,
    private val liveStreamRepository: LiveStreamRepository,
    private val callSignaling: com.red.server.websocket.CallWebSocketHandler
) {
    companion object { private val log = LoggerFactory.getLogger(LiveStreamService::class.java) }

    // In-memory overlay for fast viewer counts
    private val liveViewers = ConcurrentHashMap<String, MutableSet<String>>()
    private val activeStreamRecords = ConcurrentHashMap<String, LiveStreamRecord>()

    /**
     * Boot rehydrate: active WebRTC sessions cannot survive a process restart
     * (SFU rooms/viewer sockets are gone), so any row left with endedAt=null
     * by a crash/restart is sealed as ended instead of being resurrected as a
     * ghost "live" stream. In-memory maps start empty; viewers rejoin fresh.
     */
    @PostConstruct
    fun rehydrateOnBoot() {
        runCatching {
            val orphans = liveStreamRepository.findByEndedAtIsNull()
            if (orphans.isEmpty()) {
                log.info("LiveStream boot rehydrate: no orphan active streams")
                return
            }
            val now = Instant.now()
            orphans.forEach { record ->
                runCatching {
                    liveStreamRepository.save(record.apply { endedAt = now })
                }.onFailure { e ->
                    log.warn("LiveStream boot rehydrate: failed to seal stream {}: {}", record.streamId, e.message)
                }
            }
            log.warn("LiveStream boot rehydrate: sealed {} orphan active stream(s) as ended", orphans.size)
        }.onFailure { e ->
            log.warn("LiveStream boot rehydrate failed: {}", e.message)
        }
    }

    fun startStream(streamId: String, broadcasterId: String): LiveStreamRecord {
        return createStream(streamId, broadcasterId, "", "", "بث مباشر", false, null)
    }

    fun createStream(
        streamId: String,
        broadcasterId: String,
        broadcasterName: String,
        broadcasterRedId: String,
        title: String,
        isPrivate: Boolean,
        password: String?,
        category: String = "عام",
        description: String = "",
        broadcasterAvatar: String? = null
    ): LiveStreamRecord {
        require(streamId.isNotBlank()) { "STREAM_ID_REQUIRED" }
        require(broadcasterId.isNotBlank()) { "BROADCASTER_ID_REQUIRED" }
        require(!isPrivate || !password.isNullOrBlank()) { "PRIVATE_STREAM_PASSWORD_REQUIRED" }
        if (liveViewers.containsKey(streamId)) {
            val existing = activeStreamRecords[streamId] ?: error("LIVE_STREAM_STATE_CORRUPT")
            require(existing.broadcasterId == broadcasterId) { "STREAM_ID_ALREADY_OWNED" }
            log.info("Stream {} already active for the same broadcaster", streamId)
            return existing
        }
        val passHash = password?.takeIf { it.isNotBlank() }?.let { passwordHasher.hash(it) }
        val record = LiveStreamRecord(
            streamId = streamId,
            broadcasterId = broadcasterId,
            broadcasterName = broadcasterName.ifBlank { "مُبث يونس" },
            broadcasterRedId = broadcasterRedId,
            title = title.ifBlank { "بث مباشر يونس 🔴" },
            isPrivate = isPrivate,
            passwordHash = passHash,
            startedAt = Instant.now(),
            category = category.ifBlank { "عام" }.take(30),
            description = description.take(200),
            visibility = if (isPrivate) "PRIVATE" else "PUBLIC",
            broadcasterAvatar = broadcasterAvatar?.take(255)
        )
        liveViewers[streamId] = ConcurrentHashMap.newKeySet()
        activeStreamRecords[streamId] = record
        
        // Save to MongoDB for persistent history
        liveStreamRepository.save(record)
        
        log.info("Stream {} created by broadcaster {} (title={}, private={})", streamId, broadcasterId, title, isPrivate)
        return record
    }

    fun verifyPassword(streamId: String, password: String?): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        if (!record.isPrivate) return true
        val passwordHash = record.passwordHash ?: return false
        if (password.isNullOrBlank()) return false
        return passwordHasher.verify(password, passwordHash)
    }

    fun searchPublicStreams(query: String?, category: String? = null): List<LiveStreamRecord> {
        val cleanQuery = query?.trim()?.lowercase().orEmpty()
        val cleanCat = category?.trim().orEmpty()
        return activeStreamRecords.values
            .filter { !it.isPrivate }
            .filter { if (cleanCat.isBlank() || cleanCat == "الكل") true else it.category == cleanCat }
            .filter { record ->
                if (cleanQuery.isBlank()) true
                else record.title.lowercase().contains(cleanQuery) ||
                     record.broadcasterName.lowercase().contains(cleanQuery) ||
                     record.broadcasterRedId.lowercase().contains(cleanQuery) ||
                     record.streamId.lowercase().contains(cleanQuery) ||
                     record.category.lowercase().contains(cleanQuery)
            }
            .onEach { record -> record.viewerCount = getViewerCount(record.streamId) }
    }

    /**
     * بحث البثوث العامة مع ترقيم صفحات (إضافة آمنة: الدالة الأصلية أعلاه
     * محفوظة التوقيع والسلوك). مرتّبة تنازليًا بعدد المشاهدين لشاشة الاكتشاف.
     * page يبدأ من 0. تُهمل القيم غير الصالحة (page<0 أو size<=0) وتُرجع الكل.
     */
    fun searchPublicStreams(query: String?, page: Int, size: Int, category: String? = null): List<LiveStreamRecord> {
        val all = searchPublicStreams(query, category).sortedByDescending { trendingScore(it) }
        if (page < 0 || size <= 0) return all
        val from = (page * size).coerceAtMost(all.size)
        val to = ((page + 1) * size).coerceAtMost(all.size)
        if (from >= to) return emptyList()
        return all.subList(from, to)
    }

    /** سجل الإعادات VOD — بثوث منتهية لها vodUrl/hlsUrl (أساس الإعادات). */
    fun getVodHistory(limit: Int = 20): List<LiveStreamRecord> {
        return runCatching {
            liveStreamRepository.findAll()
                .filter { it.endedAt != null && (!it.vodUrl.isNullOrBlank() || !it.hlsUrl.isNullOrBlank()) }
                .sortedByDescending { it.endedAt }
                .take(limit.coerceIn(1, 100))
        }.getOrDefault(emptyList())
    }

    // ── الأيادي المرفوعة خادمية Legendary V3 (تبقى بعد إعادة اتصال المذيع) ──
    private val raisedHands = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    fun addRaisedHand(streamId: String, userId: String, userName: String) {
        raisedHands.computeIfAbsent(streamId) { ConcurrentHashMap() }[userId] = userName.take(64).ifBlank { userId }
    }

    fun removeRaisedHand(streamId: String, userId: String) {
        raisedHands[streamId]?.remove(userId)
    }

    fun getRaisedHands(streamId: String): Map<String, String> =
        raisedHands[streamId]?.toMap() ?: emptyMap()

    fun clearRaisedHands(streamId: String) {
        raisedHands.remove(streamId)
    }

    /** نقاط الترند = مشاهدون×2 + رسائل + تفاعلات/2 — لاكتشاف TikTok-style. */
    fun trendingScore(r: LiveStreamRecord): Long =
        r.viewerCount.toLong() * 2 + r.totalMessages + r.totalReactions / 2

    fun getStreamRecord(streamId: String): LiveStreamRecord? = activeStreamRecords[streamId]

    fun addViewer(streamId: String, viewerId: String): Int {
        val viewers = liveViewers[streamId] ?: return -1
        // banned cannot rejoin
        val rec = activeStreamRecords[streamId]
        if (rec != null && viewerId in rec.bannedIds) return -1
        viewers.add(viewerId)
        val count = viewers.size
        rec?.viewerCount = count
        if (rec != null && count > rec.peakViewers) {
            rec.peakViewers = count
        }
        return count
    }

    fun removeViewer(streamId: String, viewerId: String) {
        liveViewers[streamId]?.remove(viewerId)
        val count = getViewerCount(streamId)
        activeStreamRecords[streamId]?.viewerCount = count
    }

    fun getViewerCount(streamId: String): Int = liveViewers[streamId]?.size ?: 0

    /** يثبت أن المشاهد مرّ عبر مسار الانضمام المعتمد قبل منحه تذكرة SFU. */
    fun isViewer(streamId: String, viewerId: String): Boolean =
        liveViewers[streamId]?.contains(viewerId) == true

    fun getActiveStreams(): List<LiveStreamRecord> {
        return activeStreamRecords.values.onEach { record -> record.viewerCount = getViewerCount(record.streamId) }.toList()
    }

    fun stopStream(streamId: String): Boolean {
        val removed = liveViewers.remove(streamId) != null
        val record = activeStreamRecords.remove(streamId)
        
        if (record != null) {
            record.endedAt = Instant.now()
            // Update MongoDB with final stats and end time
            liveStreamRepository.save(record)
        }
        // إبقاء سجل الشات لفترة قصيرة للإعادات ثم تنظيف (VOD基礎)
        // لا نحذف فوراً — يُحذف عند انتهاء النافذة أو عند الحاجة للذاكرة
        clearRaisedHands(streamId)
        
        if (removed) log.info("Stream {} ended", streamId)
        return removed
    }

    /** طرد مشاهد من البث المباشر (Kick Viewer) */
    fun kickViewer(streamId: String, broadcasterId: String, viewerIdToKick: String): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER_CAN_KICK" }
        
        val viewers = liveViewers[streamId]
        if (viewers?.remove(viewerIdToKick) == true) {
            record.viewerCount = viewers.size
            // إرسال إشارة الطرد عبر WebSocket للمشاهد
            callSignaling.deliverSignal(
                targetRedId = viewerIdToKick,
                type = "KICKED",
                roomId = streamId,
                payload = mapOf("reason" to "Kicked by broadcaster")
            )
            log.info("Viewer {} kicked from stream {} by broadcaster {}", viewerIdToKick, streamId, broadcasterId)
            return true
        }
        return false
    }

    // ── Legendary V2: moderation / slow-mode / pin / co-hosts / analytics ──

    fun setSlowMode(streamId: String, broadcasterId: String, seconds: Int): Int {
        val record = activeStreamRecords[streamId] ?: error("STREAM_NOT_FOUND")
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER" }
        val v = seconds.coerceIn(0, 60)
        record.slowModeSec = v
        runCatching { liveStreamRepository.save(record) }
        return v
    }

    fun getSlowMode(streamId: String): Int = activeStreamRecords[streamId]?.slowModeSec ?: 0

    fun setPinned(streamId: String, broadcasterId: String, chatId: String?, text: String?): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER" }
        record.pinnedChatId = chatId
        record.pinnedText = text?.take(200)
        runCatching { liveStreamRepository.save(record) }
        return true
    }

    fun muteUser(streamId: String, broadcasterId: String, targetId: String, muted: Boolean): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER" }
        if (muted) {
            if (targetId !in record.mutedIds) record.mutedIds.add(targetId)
        } else {
            record.mutedIds.remove(targetId)
        }
        return true
    }

    fun isMuted(streamId: String, userId: String): Boolean =
        activeStreamRecords[streamId]?.mutedIds?.contains(userId) == true

    fun isBanned(streamId: String, userId: String): Boolean =
        activeStreamRecords[streamId]?.bannedIds?.contains(userId) == true

    /** فحص المشاهد بتوافق مزدوج (RedId الجديد + UUID القديم) — للفترة الانتقالية. */
    fun isViewerAny(streamId: String, redId: String, uuid: String): Boolean {
        val set = liveViewers[streamId] ?: return false
        return set.contains(redId) || set.contains(uuid)
    }

    fun banUser(streamId: String, broadcasterId: String, targetId: String, banned: Boolean): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER" }
        if (banned) {
            if (targetId !in record.bannedIds) record.bannedIds.add(targetId)
            liveViewers[streamId]?.remove(targetId)
        } else {
            record.bannedIds.remove(targetId)
        }
        return true
    }

    fun updateBlockedWords(streamId: String, broadcasterId: String, words: List<String>): List<String> {
        val record = activeStreamRecords[streamId] ?: error("STREAM_NOT_FOUND")
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER" }
        record.blockedWords = words.map { it.trim().lowercase() }.filter { it.isNotBlank() }.take(200).toMutableList()
        return record.blockedWords
    }

    /** تدوير كلمة سر البث الخاص — يبطل الكلمة القديمة فوراً (المطرود لا يعود). */
    fun rotatePassword(streamId: String, broadcasterId: String, newPassword: String?): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER" }
        val copy = record.copy(
            passwordHash = newPassword?.takeIf { it.isNotBlank() }?.let { passwordHasher.hash(it) }
        )
        // حافظ على الحالة الحية (عداد/ذروة/قوائم) ثم استبدل السجل
        copy.viewerCount = record.viewerCount
        copy.peakViewers = record.peakViewers
        copy.totalMessages = record.totalMessages
        copy.totalReactions = record.totalReactions
        copy.slowModeSec = record.slowModeSec
        copy.pinnedChatId = record.pinnedChatId
        copy.pinnedText = record.pinnedText
        copy.coHostIds = record.coHostIds
        copy.mutedIds = record.mutedIds
        copy.bannedIds = record.bannedIds
        copy.blockedWords = record.blockedWords
        copy.recordingEnabled = record.recordingEnabled
        copy.hlsUrl = record.hlsUrl
        copy.vodUrl = record.vodUrl
        activeStreamRecords[streamId] = copy
        runCatching { liveStreamRepository.save(copy) }
        return true
    }

    fun getBlockedWords(streamId: String): List<String> =
        activeStreamRecords[streamId]?.blockedWords ?: emptyList()

    fun approveCoHost(streamId: String, broadcasterId: String, targetId: String): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER" }
        raisedHands[streamId]?.remove(targetId)
        if (targetId !in record.coHostIds && record.coHostIds.size < 4) {
            record.coHostIds.add(targetId)
            return true
        }
        return targetId in record.coHostIds
    }

    fun removeCoHost(streamId: String, broadcasterId: String, targetId: String): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        // broadcaster or self-leave
        if (record.broadcasterId != broadcasterId && broadcasterId != targetId) return false
        raisedHands[streamId]?.remove(targetId)
        return record.coHostIds.remove(targetId)
    }

    fun getCoHosts(streamId: String): List<String> =
        activeStreamRecords[streamId]?.coHostIds?.toList() ?: emptyList()

    fun incrementMessageCount(streamId: String) {
        activeStreamRecords[streamId]?.let { it.totalMessages++ }
    }

    fun incrementReactionCount(streamId: String) {
        activeStreamRecords[streamId]?.let { it.totalReactions++ }
    }

    fun setRecording(streamId: String, broadcasterId: String, enabled: Boolean, hlsUrl: String? = null, vodUrl: String? = null): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER" }
        record.recordingEnabled = enabled
        if (hlsUrl != null) record.hlsUrl = hlsUrl
        if (vodUrl != null) record.vodUrl = vodUrl
        runCatching { liveStreamRepository.save(record) }
        return true
    }

    fun getAnalytics(streamId: String): Map<String, Any> {
        val record = activeStreamRecords[streamId] ?: error("STREAM_NOT_FOUND")
        record.viewerCount = getViewerCount(streamId)
        return mapOf(
            "streamId" to record.streamId,
            "viewerCount" to record.viewerCount,
            "peakViewers" to record.peakViewers,
            "totalMessages" to record.totalMessages,
            "totalReactions" to record.totalReactions,
            "startedAt" to record.startedAt.toString(),
            "coHosts" to record.coHostIds.size
        )
    }

    // ── سجل الشات Legendary V2 (ذاكرة مقيدة 200 + endpoint تاريخ) ──
    data class ChatEntry(
        val id: String = java.util.UUID.randomUUID().toString(),
        val senderId: String = "",
        val senderName: String = "",
        val text: String = "",
        val replyToId: String? = null,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val chatHistory = ConcurrentHashMap<String, MutableList<ChatEntry>>()

    fun saveChat(streamId: String, senderId: String, senderName: String, text: String, replyToId: String? = null, clientChatId: String? = null): ChatEntry {
        val list = chatHistory.computeIfAbsent(streamId) { mutableListOf() }
        // معرّف المُرسِل يُعتمد فقط إن كان **فريداً** داخل البث: قبول معرّف قائم كان
        // سيسمح لعميل بتبديل رسالة غيره أو انتحال معرّفها، لذا نولّد معرّفاً جديداً
        // عند التصادم بدل الكتابة فوق رسالة قائمة.
        val id = synchronized(list) {
            clientChatId?.take(64)?.takeIf { it.isNotBlank() }
                ?.takeIf { candidate -> list.none { it.id == candidate } }
                ?: java.util.UUID.randomUUID().toString()
        }
        val entry = ChatEntry(
            id = id,
            senderId = senderId,
            senderName = senderName,
            text = text.take(200),
            replyToId = replyToId?.take(64)?.takeIf { it.isNotBlank() }
        )
        synchronized(list) {
            list.add(entry)
            while (list.size > 200) list.removeAt(0)
        }
        incrementMessageCount(streamId)
        return entry
    }

    fun getChatHistory(streamId: String, limit: Int = 30, before: Long? = null): List<ChatEntry> {
        val list = chatHistory[streamId]?.toList() ?: emptyList()
        val filtered = if (before != null) list.filter { it.createdAt < before } else list
        return filtered.takeLast(limit.coerceIn(1, 100))
    }

    /** حذف رسالة (المذيع فقط) — تُحذف من السجل ويُبث CHAT_DELETED عبر الـ handler. */
    fun deleteChat(streamId: String, broadcasterId: String, chatId: String): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER" }
        val list = chatHistory[streamId] ?: return false
        synchronized(list) {
            val removed = list.removeIf { it.id == chatId }
            if (removed && record.pinnedChatId == chatId) {
                record.pinnedChatId = null
                record.pinnedText = null
            }
            return removed
        }
    }

    fun clearChatHistory(streamId: String) {
        chatHistory.remove(streamId)
    }

}
