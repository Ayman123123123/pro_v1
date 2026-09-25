package com.red.server.messaging

import com.red.server.auth.RedIdGenerator
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.database.ChannelMessageDocument
import com.red.server.database.ConversationSequence
import com.red.server.database.DisappearingSettingsDocument
import com.red.server.database.GroupMessageDocument
import com.red.server.database.MessageDocument
import com.red.server.database.MessageReaction
import com.red.server.groups.GroupMember
import com.red.server.groups.GroupDocument
import com.red.server.groups.GroupRole
import com.red.server.social.UuidV7
import com.red.sovereign.proto.RedProtos
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class MessageService(
    private val mongo: MongoTemplate,
    private val redis: RedisTemplate<String, String>,
    private val users: UserAccountRepository,
    private val jdbc: JdbcTemplate
) {
    @PostConstruct
    fun indexes() {
        // P9: كل إنشاء فهرس محمي — تعارض خيارات فهرس قديم (unique/non-unique على نفس
        // المفاتيح) أو بيانات قديمة مكررة يجب ألا يُسقط الإقلاع؛ المخصّص الذري يمنع التكرار أصلًا.
        runCatching {
            mongo.indexOps(MessageDocument::class.java).createIndex(Index().on("uuid", Sort.Direction.ASC).unique())
            // التسليم: يطابق pendingFor(receiverId,receiverDeviceId,status) مرتبًا بـ sequenceNumber
            mongo.indexOps(MessageDocument::class.java).createIndex(Index().on("receiverId", Sort.Direction.ASC).on("receiverDeviceId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("sequenceNumber", Sort.Direction.ASC))
            // التسليم (catchup/sync): receiverId+status+createdAt ليطابق pendingFor(since) قبل الفرز بـ sequenceNumber
            mongo.indexOps(MessageDocument::class.java).createIndex(Index().on("receiverId", Sort.Direction.ASC).on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.ASC))
            // P9: فرادة (المحادثة، التسلسل) — شبكة أمان تحت المخصّص الذري findAndModify
            mongo.indexOps(MessageDocument::class.java).createIndex(Index().on("conversationId", Sort.Direction.ASC).on("sequenceNumber", Sort.Direction.ASC).unique())
            // V26: فهارس إضافية للميزات الجديدة
            mongo.indexOps(MessageDocument::class.java).createIndex(Index().on("conversationId", Sort.Direction.ASC).on("isPinned", Sort.Direction.ASC).on("pinnedAt", Sort.Direction.DESC))
            mongo.indexOps(MessageDocument::class.java).createIndex(Index().on("senderId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC))
            mongo.indexOps(MessageDocument::class.java).createIndex(Index().on("disappearAt", Sort.Direction.ASC))
        }.onFailure { e -> log.warn("Failed to create message indexes: {}", e.message) }
        // فهارس المجموعات والقنوات
        try {
            mongo.indexOps(com.red.server.database.GroupMessageDocument::class.java).createIndex(Index().on("groupId", Sort.Direction.ASC).on("isPinned", Sort.Direction.ASC).on("pinnedAt", Sort.Direction.DESC))
            // P9: فرادة (المجموعة، التسلسل)
            mongo.indexOps(com.red.server.database.GroupMessageDocument::class.java).createIndex(Index().on("groupId", Sort.Direction.ASC).on("sequenceNumber", Sort.Direction.ASC).unique())
            // TTL الاختفاء للمجموعات — يطابق استعلام cleanupDisappearing
            mongo.indexOps(com.red.server.database.GroupMessageDocument::class.java).createIndex(Index().on("disappearAt", Sort.Direction.ASC))
            // P9: فرادة (القناة، التسلسل)
            mongo.indexOps(com.red.server.database.ChannelMessageDocument::class.java).createIndex(Index().on("channelId", Sort.Direction.ASC).on("sequenceNumber", Sort.Direction.ASC).unique())
            // فهرس التثبيت للقنوات — يطابق مرآة PinnedMessageService
            mongo.indexOps(com.red.server.database.ChannelMessageDocument::class.java).createIndex(Index().on("channelId", Sort.Direction.ASC).on("isPinned", Sort.Direction.ASC).on("pinnedAt", Sort.Direction.DESC))
            mongo.indexOps(com.red.server.database.PinnedMessageDocument::class.java).createIndex(Index().on("messageUuid", Sort.Direction.ASC).unique())
        } catch (e: Exception) {
            log.warn("Failed to create group/channel indexes: {}", e.message)
        }
    }

    fun processIncoming(message: RedProtos.ChatMessage): MessageDocument =
        processIncoming(message, null, null)

    /**
     * مسار الاستقبال الكامل — يدعم الرد والتحويل.
     * replyToMessageUuid يجب أن يشير لرسالة قائمة في نفس المحادثة وإلا رُفض بـ 400،
     * وforwardedFromConversationId يُثبَّت على النسخة المحوَّلة لتتبّع المصدر.
     */
    fun processIncoming(
        message: RedProtos.ChatMessage,
        replyToMessageUuid: String?,
        forwardedFromConversationId: String?
    ): MessageDocument {
        validate(message)
        enforceNotBlocked(message.senderId, message.receiverId)
        // 🔐 C4: فرض قواعد المجموعة على كل رسائل المجموعات بغض النظر عن نوع التشفير —
        // وإلا استطاع عضو عادي تجاوز onlyAdminsCanSend بإرسال RICH_TEXT/وسائط بتشفير زوجي (2/3).
        // الحاسم: وجود مستند المجموعة هو الفاصل الحقيقي، لا طول المعرف (الـ heuristic القديم
        // كان يخطئ مع محادثات 1:1 الطويلة redId1_redId2 = 77 حرفاً).
        val isGroupConversation = mongo.exists(Query(Criteria.where("_id").`is`(message.conversationId)), GroupDocument::class.java)
        if (message.type in GROUP_TYPES || isGroupConversation) enforceGroupMembership(message)
        mongo.findOne(Query(Criteria.where("uuid").`is`(message.id)), MessageDocument::class.java)?.let { existing ->
            require(existing.senderId == message.senderId && existing.receiverId == message.receiverId && existing.conversationId == message.conversationId &&
                existing.senderDeviceId == message.senderDeviceId && existing.receiverDeviceId == message.receiverDeviceId) {
                "Message UUID collision"
            }
            return existing
        }

        // الرد يجب أن يستهدف رسالة قائمة في نفس المحادثة — وإلا 400 (عبر AuthExceptionHandler).
        replyToMessageUuid?.let { validateReplyTarget(it, message.conversationId) }
        // فرض إعدادات الاختفاء المرن عند الكتابة (best-effort — لا تكسر مسار الإرسال أبدًا).
        val disappearSecs = disappearingSecondsForConversation(message.conversationId)

        val stored = MessageDocument(
            uuid = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            payload = message.payload.toByteArray(),
            messageType = message.type.ifBlank { "TEXT" },
            senderDeviceId = message.senderDeviceId,
            receiverDeviceId = message.receiverDeviceId,
            ciphertextType = message.ciphertextType,
            sequenceNumber = nextSequence(message.conversationId),
            status = "SENT",
            replyToMessageUuid = replyToMessageUuid,
            forwardedFromConversationId = forwardedFromConversationId,
            disappearAfterSeconds = disappearSecs,
            disappearAt = disappearSecs?.let { Instant.now().plusSeconds(it.toLong()) },
            voiceMetadata = if (message.type == "VOICE") extractVoiceMetadata(message.payload.toByteArray()) else null
        )
        // P9: حلّ تعارض Sequence — إعادة الإرسال بنفس UUID تُرجع الموجود (idempotent)،
        // والتعارض الحقيقي على (conversationId, sequenceNumber) يُعاد تخصيصه ويُحفظ مرة واحدة.
        val saved = try { mongo.save(stored) } catch (_: DuplicateKeyException) {
            mongo.findOne(Query(Criteria.where("uuid").`is`(message.id)), MessageDocument::class.java)
                ?: mongo.save(stored.copy(sequenceNumber = nextSequence(message.conversationId)))
        }
        // P9: النشر بعد الحفظ best-effort — عطل Redis يجب ألا يُفشل رسالة محفوظة
        // (الرمي هنا يحوّل إرسالًا ناجحًا إلى 500 وإعادة محاولة مكررة).
        runCatching {
            redis.opsForZSet().add("red:presence:index", message.senderId, System.currentTimeMillis().toDouble())
            // TTL الحضور: تقليم أعضاء ZSET الأقدم من 48 ساعة حتى لا ينمو بلا حد
            redis.opsForZSet().removeRangeByScore("red:presence:index", 0.0, (System.currentTimeMillis() - 48 * 3600_000).toDouble())
            redis.convertAndSend("red:messages:${message.receiverId}", saved.uuid)
        }.onFailure { e -> log.warn("Post-save fan-out failed for {}: {}", saved.uuid, e.message) }
        return saved
    }

    /**
     * 🎙️ استخراج VoiceMessageMetadata من الـ payload المشفّر
     * الـ payload يحتوي على plaintext JSON بعد فك التشفير على الـ client
     * هنا نطبّق best-effort: نحاول parse كـ JSON (في حالة الاختبار) أو نتجاهل
     * في الإنتاج، الـ client يضع metadata في attachment
     */
    private fun extractVoiceMetadata(payload: ByteArray): com.red.server.database.VoiceMessageMetadata? {
        return try {
            // Try to parse as JSON (test path or unencrypted debug)
            val text = String(payload, Charsets.UTF_8)
            if (text.startsWith("{")) {
                val regex = Regex("\"durationSeconds\":(\\d+)")
                val match = regex.find(text) ?: return null
                val durationSeconds = match.groupValues[1].toIntOrNull() ?: return null
                com.red.server.database.VoiceMessageMetadata(
                    durationMs = durationSeconds * 1000L,
                    waveform = "" // Waveform يُستخرج من الـ attachment الفعلي
                )
            } else null
        } catch (_: Exception) {
            null // تجاهل بصمت — voiceMetadata اختيارية
        }
    }

    // LEGENDARY FIX: حد 50 بدل 500 (500×1MiB = 500MB عند إعادة الاتصال) + ترتيب sequence بدل createdAt ليطابق الفهرس
    fun pendingFor(receiverId: String, receiverDeviceId: Int, limit: Int = 50): List<MessageDocument> = mongo.find(
        Query(Criteria.where("receiverId").`is`(receiverId).and("receiverDeviceId").`is`(receiverDeviceId).and("status").`is`("SENT").and("deletedForEveryoneAt").`is`(null))
            .with(Sort.by(Sort.Direction.ASC, "sequenceNumber")).limit(limit.coerceIn(1, 50)),
        MessageDocument::class.java
    )

    fun getMissedMessages(userId: String, conversationId: String, fromSequence: Long, toSequence: Long, limit: Int = 50): List<MessageDocument> {
        val criteria = Criteria.where("conversationId").`is`(conversationId)
            .andOperator(Criteria().orOperator(Criteria.where("senderId").`is`(userId), Criteria.where("receiverId").`is`(userId)))
            .and("sequenceNumber").gte(fromSequence.coerceAtLeast(0))
            .and("deletedForEveryoneAt").`is`(null)
        if (toSequence > 0) {
            require(toSequence - fromSequence.coerceAtLeast(0) <= 500) { "SYNC_RANGE_TOO_LARGE" }
            criteria.and("sequenceNumber").lte(toSequence)
        }
        return mongo.find(Query(criteria).with(Sort.by(Sort.Direction.ASC, "sequenceNumber")).limit(limit.coerceIn(1, 50)), MessageDocument::class.java)
    }

    /** Only the intended receiver may advance SENT -> DELIVERED -> READ. */
    fun acknowledge(receiverId: String, receiverDeviceId: Int, messageId: String, requestedStatus: String): MessageDocument {
        val status = requestedStatus.uppercase()
        require(status == "DELIVERED" || status == "READ") { "Unsupported ACK status" }
        val message = mongo.findOne(Query(Criteria.where("uuid").`is`(messageId)), MessageDocument::class.java)
            ?: throw NoSuchElementException("Message not found")
        require(message.receiverId == receiverId && message.receiverDeviceId == receiverDeviceId) { "Only the target device can acknowledge this message" }
        if (rank(status) > rank(message.status)) {
            message.status = status
            if (status == "DELIVERED" && message.deliveredAt == null) message.deliveredAt = Instant.now()
            if (status == "READ") { if (message.deliveredAt == null) message.deliveredAt = Instant.now(); message.readAt = Instant.now() }
            mongo.save(message)
        }
        return message
    }

    fun findAuthorized(messageId: String, userId: String): MessageDocument? = mongo.findOne(
        Query(Criteria.where("uuid").`is`(messageId).orOperator(Criteria.where("senderId").`is`(userId), Criteria.where("receiverId").`is`(userId))),
        MessageDocument::class.java
    )

    fun findMessage(messageId: String): MessageDocument? = mongo.findOne(
        Query(Criteria.where("uuid").`is`(messageId)),
        MessageDocument::class.java
    )

    // ════════════════════════════════════════════════════
    // 😀 التفاعلات — تخزين + بث للطرفين (إضافة وإزالة)
    // ════════════════════════════════════════════════════

    /**
     * يضيف تفاعلًا أو يزيله (remove=true هو REACTION_REMOVE) على رسالة خاصة أو
     * جماعية أو قناة، ويعيد نطاق البث (كل المشاركين — لا المرسل وحده).
     * يرمى IllegalArgumentException (→ 400) عند إيموجي غير صالح أو عدم عضوية،
     * وNoSuchElementException (→ 404) عند غياب الرسالة المستهدفة.
     */
    fun toggleReaction(
        messageUuid: String,
        reactorRedId: String,
        emoji: String,
        remove: Boolean
    ): ReactionTarget {
        if (!remove) {
            val clean = emoji.trim()
            require(clean.isNotBlank() && clean.codePointCount(0, clean.length) in 1..8) { "INVALID_EMOJI" }
        }

        // 1) محادثة خاصة
        mongo.findOne(Query(Criteria.where("uuid").`is`(messageUuid)), MessageDocument::class.java)?.let { msg ->
            require(reactorRedId == msg.senderId || reactorRedId == msg.receiverId) { "NOT_A_PARTICIPANT" }
            val updated = applyReaction(msg.reactions, reactorRedId, emoji, remove)
            mongo.updateFirst(
                Query(Criteria.where("uuid").`is`(messageUuid)),
                Update().set("reactions", updated),
                MessageDocument::class.java
            )
            mirrorReactionPostgres(messageUuid, reactorRedId, emoji, remove)
            return ReactionTarget(messageUuid, "PRIVATE", msg.conversationId, listOf(msg.senderId, msg.receiverId).distinct())
        }

        // 2) رسالة مجموعة
        mongo.findOne(Query(Criteria.where("uuid").`is`(messageUuid)), GroupMessageDocument::class.java)?.let { msg ->
            requireGroupMemberByRedId(msg.groupId, reactorRedId)
            val updated = applyReaction(msg.reactions, reactorRedId, emoji, remove)
            mongo.updateFirst(
                Query(Criteria.where("uuid").`is`(messageUuid)),
                Update().set("reactions", updated),
                GroupMessageDocument::class.java
            )
            mirrorReactionPostgres(messageUuid, reactorRedId, emoji, remove)
            return ReactionTarget(messageUuid, "GROUP", msg.groupId, groupMemberRedIds(msg.groupId))
        }

        // 3) رسالة قناة
        mongo.findOne(Query(Criteria.where("uuid").`is`(messageUuid)), ChannelMessageDocument::class.java)?.let { msg ->
            requireChannelMemberByRedId(msg.channelId, reactorRedId, msg.senderId)
            val updated = applyReaction(msg.reactions, reactorRedId, emoji, remove)
            mongo.updateFirst(
                Query(Criteria.where("uuid").`is`(messageUuid)),
                Update().set("reactions", updated),
                ChannelMessageDocument::class.java
            )
            mirrorReactionPostgres(messageUuid, reactorRedId, emoji, remove)
            return ReactionTarget(messageUuid, "CHANNEL", msg.channelId, channelSubscriberRedIds(msg.channelId, msg.senderId))
        }

        throw NoSuchElementException("REACTION_TARGET_NOT_FOUND")
    }

    private fun applyReaction(
        current: List<MessageReaction>,
        reactorRedId: String,
        emoji: String,
        remove: Boolean
    ): List<MessageReaction> {
        val kept = current.filterNot { it.userId == reactorRedId }
        if (remove) return kept
        return kept + MessageReaction(userId = reactorRedId, emoji = emoji.trim(), addedAt = Instant.now())
    }

    /** مرآة Postgres (message_reactions) — best-effort، لا تكسر مسار التفاعل. */
    private fun mirrorReactionPostgres(messageUuid: String, reactorRedId: String, emoji: String, remove: Boolean) {
        runCatching {
            val accountUuid = users.findByRedId(reactorRedId)?.id ?: return@runCatching
            if (remove) {
                jdbc.update("DELETE FROM message_reactions WHERE message_uuid=? AND user_id=?", messageUuid, accountUuid)
            } else {
                jdbc.update(
                    """INSERT INTO message_reactions(message_uuid, user_id, emoji, reacted_at)
                       VALUES (?,?,?,NOW())
                       ON CONFLICT (message_uuid, user_id) DO UPDATE SET emoji=EXCLUDED.emoji, reacted_at=NOW()""",
                    messageUuid, accountUuid, emoji.trim()
                )
            }
        }
    }

    private fun requireGroupMemberByRedId(groupId: String, redId: String) {
        val member = users.findByRedId(redId) ?: throw NoSuchElementException("USER_NOT_FOUND")
        require(mongo.exists(Query(Criteria.where("id").`is`("$groupId:${member.id}")), GroupMember::class.java)) {
            "NOT_A_GROUP_MEMBER"
        }
    }

    private fun requireChannelMemberByRedId(channelId: String, redId: String, senderRedId: String? = null) {
        // مخزن العضوية الحي هو Postgres (ChannelService) — لا Mongo (لا كاتب له هناك).
        if (senderRedId != null && redId == senderRedId) return
        val member = users.findByRedId(redId) ?: throw NoSuchElementException("USER_NOT_FOUND")
        val channelUuid = runCatching { UUID.fromString(channelId) }.getOrNull()
            ?: throw IllegalArgumentException("INVALID_CHANNEL_ID")
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM channel_members WHERE channel_id=? AND user_id=?",
            Int::class.java, channelUuid, member.id
        ) ?: 0
        require(count > 0) { "NOT_A_CHANNEL_MEMBER" }
    }

    /** معرّفات المشتركين (RED ID) للبث — بسقف 500، والبقية تلتقط عبر المزامنة. */
    private fun channelSubscriberRedIds(channelId: String, senderRedId: String): List<String> {
        return runCatching {
            val channelUuid = UUID.fromString(channelId)
            val memberUuids = jdbc.query(
                "SELECT user_id FROM channel_members WHERE channel_id=? LIMIT 500",
                { rs, _ -> rs.getObject("user_id", UUID::class.java) },
                channelUuid
            )
            val redIds = if (memberUuids.isEmpty()) emptyList()
            else users.findAllById(memberUuids).map { it.redId }
            (redIds + senderRedId).filter(String::isNotBlank).distinct()
        }.getOrDefault(listOf(senderRedId))
    }

    // ════════════════════════════════════════════════════
    // ↩️ الرد — يجب أن يستهدف رسالة قائمة في نفس النطاق
    // ════════════════════════════════════════════════════

    /** تحقق الرد في المحادثات الخاصة — الهدف بنفس conversationId وإلا 400. */
    fun validateReplyTarget(replyToMessageUuid: String, conversationId: String) {
        require(replyToMessageUuid.isNotBlank()) { "INVALID_REPLY_TARGET" }
        val target = mongo.findOne(
            Query(Criteria.where("uuid").`is`(replyToMessageUuid)),
            MessageDocument::class.java
        ) ?: throw IllegalArgumentException("REPLY_TARGET_NOT_FOUND")
        require(target.conversationId == conversationId) { "REPLY_TARGET_NOT_IN_CONVERSATION" }
        require(target.deletedForEveryoneAt == null) { "REPLY_TARGET_DELETED" }
    }

    /** تحقق الرد في المجموعات — الهدف بنفس groupId وإلا 400. */
    fun validateGroupReplyTarget(replyToMessageUuid: String, groupId: String) {
        require(replyToMessageUuid.isNotBlank()) { "INVALID_REPLY_TARGET" }
        val target = mongo.findOne(
            Query(Criteria.where("uuid").`is`(replyToMessageUuid)),
            GroupMessageDocument::class.java
        ) ?: throw IllegalArgumentException("REPLY_TARGET_NOT_FOUND")
        require(target.groupId == groupId) { "REPLY_TARGET_NOT_IN_CONVERSATION" }
        require(target.deletedForEveryoneAt == null) { "REPLY_TARGET_DELETED" }
    }

    // ════════════════════════════════════════════════════
    // ➡️ التحويل — نسخة جديدة + عدّاد المصدر
    // ════════════════════════════════════════════════════

    /**
     * يحوّل رسالة (خاصة/مجموعة/قناة) إلى محادثة خاصة قائمة أو مجموعة.
     * يُنشئ نسخة UUIDv7 جديدة تحمل forwardedFromConversationId، ويزيد forwardCount
     * في المصدر، ويطبّق إعدادات الاختفاء للهدف. التحويل إلى قناة مرفوض
     * (بث أحادي — النشر عبر مسار القنوات فقط).
     */
    fun forwardMessage(
        actorRedId: String,
        messageUuid: String,
        targetConversationId: String,
        replyToMessageUuid: String? = null
    ): ForwardedMessage {
        require(targetConversationId.length in 8..128) { "INVALID_TARGET_CONVERSATION" }
        val source = loadForwardSource(actorRedId, messageUuid)

        // الهدف مجموعة؟
        val isGroupTarget = mongo.exists(
            Query(Criteria.where("_id").`is`(targetConversationId)),
            GroupDocument::class.java
        )
        return if (isGroupTarget) {
            requireGroupMemberByRedId(targetConversationId, actorRedId)
            replyToMessageUuid?.let { validateGroupReplyTarget(it, targetConversationId) }
            val actor = users.findByRedId(actorRedId) ?: throw NoSuchElementException("USER_NOT_FOUND")
            require(actor.redId == actorRedId) { "USER_NOT_FOUND" }
            val disappearSecs = disappearingSecondsForConversation(targetConversationId)
            val copy = GroupMessageDocument(
                uuid = UuidV7.next(),
                groupId = targetConversationId,
                senderId = actorRedId,
                senderDeviceId = 1,
                payload = source.payload,
                messageType = source.messageType,
                ciphertextType = 7,
                sequenceNumber = nextSequence("group:$targetConversationId"),
                replyToMessageUuid = replyToMessageUuid,
                forwardedFromConversationId = source.scopeId,
                disappearAfterSeconds = disappearSecs,
                disappearAt = disappearSecs?.let { Instant.now().plusSeconds(it.toLong()) }
            )
            // P9: نفس سياسة حلّ التعارض لمسار المجموعات
            val saved = try { mongo.save(copy) } catch (_: DuplicateKeyException) {
                mongo.findOne(Query(Criteria.where("uuid").`is`(copy.uuid)), GroupMessageDocument::class.java)
                    ?: mongo.save(copy.copy(sequenceNumber = nextSequence("group:$targetConversationId")))
            }
            incrementSourceForwardCount(source)
            ForwardedMessage(saved.uuid, "GROUP", targetConversationId, source.scopeId, saved.sequenceNumber)
        } else {
            replyToMessageUuid?.let { validateReplyTarget(it, targetConversationId) }
            forwardIntoConversation(actorRedId, source, targetConversationId)
        }
    }

    private data class ForwardSource(
        val messageUuid: String,
        val kind: String, // PRIVATE | GROUP | CHANNEL
        val scopeId: String,
        val payload: ByteArray,
        val messageType: String,
        val forwardCount: Int
    )

    private fun loadForwardSource(actorRedId: String, messageUuid: String): ForwardSource {
        mongo.findOne(Query(Criteria.where("uuid").`is`(messageUuid)), MessageDocument::class.java)?.let { msg ->
            require(actorRedId == msg.senderId || actorRedId == msg.receiverId) { "NOT_A_PARTICIPANT" }
            require(msg.deletedForEveryoneAt == null) { "MESSAGE_DELETED" }
            return ForwardSource(msg.uuid, "PRIVATE", msg.conversationId, msg.payload, msg.messageType, msg.forwardCount)
        }
        mongo.findOne(Query(Criteria.where("uuid").`is`(messageUuid)), GroupMessageDocument::class.java)?.let { msg ->
            requireGroupMemberByRedId(msg.groupId, actorRedId)
            require(msg.deletedForEveryoneAt == null) { "MESSAGE_DELETED" }
            return ForwardSource(msg.uuid, "GROUP", msg.groupId, msg.payload, msg.messageType, msg.forwardCount)
        }
        mongo.findOne(Query(Criteria.where("uuid").`is`(messageUuid)), ChannelMessageDocument::class.java)?.let { msg ->
            requireChannelMemberByRedId(msg.channelId, actorRedId, msg.senderId)
            require(msg.deletedAt == null) { "MESSAGE_DELETED" }
            return ForwardSource(msg.uuid, "CHANNEL", msg.channelId, msg.payload, msg.messageType, msg.forwardCount)
        }
        throw NoSuchElementException("MESSAGE_NOT_FOUND")
    }

    private fun incrementSourceForwardCount(source: ForwardSource) {
        val query = Query(Criteria.where("uuid").`is`(source.messageUuid))
        val update = Update().inc("forwardCount", 1)
        when (source.kind) {
            "PRIVATE" -> mongo.updateFirst(query, update, MessageDocument::class.java)
            "GROUP" -> mongo.updateFirst(query, update, GroupMessageDocument::class.java)
            "CHANNEL" -> mongo.updateFirst(query, update, ChannelMessageDocument::class.java)
        }
    }

    private fun forwardIntoConversation(
        actorRedId: String,
        source: ForwardSource,
        targetConversationId: String
    ): ForwardedMessage {
        val latest = mongo.findOne(
            Query(Criteria.where("conversationId").`is`(targetConversationId))
                .with(Sort.by(Sort.Direction.DESC, "sequenceNumber")),
            MessageDocument::class.java
        )
        val peer = when {
            latest == null && isSelfConversation(targetConversationId, actorRedId) -> actorRedId
            latest == null -> throw IllegalArgumentException("INVALID_TARGET_CONVERSATION")
            latest.senderId == actorRedId -> latest.receiverId
            latest.receiverId == actorRedId -> latest.senderId
            else -> throw IllegalArgumentException("NOT_A_TARGET_PARTICIPANT")
        }
        enforceNotBlocked(actorRedId, peer)
        val (senderDevice, receiverDevice) = inferTargetDevices(latest, actorRedId)
        val disappearSecs = disappearingSecondsForConversation(targetConversationId)
        val copy = MessageDocument(
            uuid = UuidV7.next(),
            conversationId = targetConversationId,
            senderId = actorRedId,
            receiverId = peer,
            payload = source.payload,
            messageType = source.messageType,
            senderDeviceId = senderDevice,
            receiverDeviceId = receiverDevice,
            ciphertextType = 2,
            sequenceNumber = nextSequence(targetConversationId),
            status = "SENT",
            forwardedFromConversationId = source.scopeId,
            disappearAfterSeconds = disappearSecs,
            disappearAt = disappearSecs?.let { Instant.now().plusSeconds(it.toLong()) }
        )
        // P9: نفس سياسة حلّ التعارض لمسار التحويل الخاص
        val saved = try { mongo.save(copy) } catch (_: DuplicateKeyException) {
            mongo.findOne(Query(Criteria.where("uuid").`is`(copy.uuid)), MessageDocument::class.java)
                ?: mongo.save(copy.copy(sequenceNumber = nextSequence(targetConversationId)))
        }
        incrementSourceForwardCount(source)
        return ForwardedMessage(saved.uuid, "PRIVATE", targetConversationId, source.scopeId, saved.sequenceNumber)
    }

    private fun isSelfConversation(conversationId: String, actorRedId: String): Boolean =
        conversationId.contains("self") || conversationId.contains("note") ||
            conversationId == actorRedId || conversationId.contains(actorRedId)

    private fun inferTargetDevices(latest: MessageDocument?, actorRedId: String): Pair<Int, Int> {
        if (latest == null) return 1 to 1
        return if (latest.senderId == actorRedId) latest.senderDeviceId to latest.receiverDeviceId
        else if (latest.receiverId == actorRedId) latest.receiverDeviceId to latest.senderDeviceId
        else 1 to 1
    }

    /**
     * ثواني الاختفاء المفعّلة للنطاق (مجموعة من Mongo، خاصة من Postgres) —
     * AFTER_SEND فقط. null تعني: دائم (لا اختفاء).
     */
    private fun disappearingSecondsForConversation(conversationId: String): Int? = runCatching {
        mongo.findById(conversationId, DisappearingSettingsDocument::class.java)
            ?.takeIf { it.enabled }?.disappearAfterSeconds?.takeIf { it > 0 }
            ?: jdbc.query(
                """SELECT disappear_after_seconds FROM disappearing_settings
                   WHERE conversation_id=? AND enabled=true AND mode='AFTER_SEND'
                   ORDER BY updated_at DESC LIMIT 1""",
                { rs, _ -> rs.getInt(1) },
                conversationId
            ).firstOrNull()?.takeIf { it > 0 }
    }.getOrNull()

    fun isContact(redIdA: String, redIdB: String): Boolean {
        if (redIdA == redIdB) return true
        val a = users.findByRedId(redIdA.uppercase()) ?: return false
        val b = users.findByRedId(redIdB.uppercase()) ?: return false
        // جهة واحدة تكفي هنا (إشارات الكتابة/التوجيه) — الصداقة المتبادلة للفيد عبر AudienceGuard فقط
        // إزالة ازدواج: استعلام واحد بشرط OR بدل COUNT مكرر ذهابًا وإيابًا
        val cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM red_contacts WHERE (owner_id=? AND contact_id=?) OR (owner_id=? AND contact_id=?)",
            Int::class.java, a.id, b.id, b.id, a.id
        ) ?: 0
        return cnt > 0
    }

    /** Same block policy as messages — used by typing indicators and other pairwise signals. */
    fun requireDirectAllowed(senderRedId: String, receiverRedId: String) =
        enforceNotBlocked(senderRedId, receiverRedId)

    /** تحقق أن المستخدم عضو فعلي في المجموعة (لإشارات المجموعة مثل مؤشر الكتابة الجماعي). */
    fun requireGroupMember(conversationId: String, redId: String) {
        val member = users.findByRedId(redId) ?: throw NoSuchElementException("Sender identity not found")
        require(mongo.exists(Query(Criteria.where("id").`is`("$conversationId:${member.id}")), GroupMember::class.java)) { "Sender is not a group member" }
    }

    /** معرّفات الأعضاء النشطين للمجموعة — للبث الجماعي (مؤشر كتابة إلخ). */
    /** AUTO-FIX (message reliability): stored messages for this recipient that arrived after `since`. */
    fun pendingFor(redId: String, since: java.time.Instant, limit: Int): List<MessageDocument> {
        // إزالة ازدواج: نفس دلالة pendingFor الأساسية (SENT فقط، حد 50، ترتيب sequence ليطابق الفهرس)
        val capped = limit.coerceIn(1, 50)
        val query = Query(
            Criteria.where("receiverId").`is`(redId)
                .and("status").`is`("SENT")
                .andOperator(
                    Criteria.where("createdAt").gt(since),
                    Criteria.where("deletedForEveryoneAt").`is`(null)
                )
        ).with(Sort.by(Sort.Direction.ASC, "sequenceNumber")).limit(capped)
        return mongo.find(query, MessageDocument::class.java)
    }

    fun groupMemberRedIds(conversationId: String): List<String> =
        mongo.find(Query(Criteria.where("groupId").`is`(conversationId)), GroupMember::class.java).map { it.redId }

    private fun enforceNotBlocked(senderRedId: String, receiverRedId: String) {
        // V26: ملاحظة لنفسي — لا حظر للذات
        if (senderRedId == receiverRedId) return
        val sender = users.findByRedId(senderRedId) ?: throw NoSuchElementException("Sender identity not found")
        val receiver = users.findByRedId(receiverRedId) ?: throw NoSuchElementException("Receiver identity not found")
        // إزالة ازدواج: حارس الجمهور هو مصدر الحقيقة الوحيد للحظر الثنائي
        require(!com.red.server.social.AudienceGuard.isBlockedEitherDirection(jdbc, sender.id, receiver.id)) {
            "Messaging is not allowed between these identities"
        }
    }

    private fun enforceGroupMembership(message: RedProtos.ChatMessage) {
        val sender = users.findByRedId(message.senderId) ?: throw NoSuchElementException("Sender identity not found")
        val receiver = users.findByRedId(message.receiverId) ?: throw NoSuchElementException("Receiver identity not found")
        val senderMember = mongo.findOne(Query(Criteria.where("id").`is`("${message.conversationId}:${sender.id}")), GroupMember::class.java)
        require(senderMember != null) { "Sender is not a group member" }
        require(mongo.exists(Query(Criteria.where("id").`is`("${message.conversationId}:${receiver.id}")), GroupMember::class.java)) { "Receiver is not a group member" }
        val group = mongo.findById(message.conversationId, GroupDocument::class.java)
            ?: throw NoSuchElementException("Group not found")
        require(!group.settings.onlyAdminsCanSend || senderMember.role in setOf(GroupRole.OWNER, GroupRole.ADMIN)) {
            "Only group administrators may send messages"
        }
    }

    private fun nextSequence(conversationId: String): Long {
        val sequence = mongo.findAndModify(
            Query(Criteria.where("id").`is`(conversationId)), Update().inc("sequence", 1),
            FindAndModifyOptions.options().upsert(true).returnNew(true), ConversationSequence::class.java
        ) ?: error("Unable to allocate conversation sequence")
        return sequence.sequence
    }

    private fun validate(message: RedProtos.ChatMessage) {
        val id = runCatching { UUID.fromString(message.id) }.getOrElse { throw IllegalArgumentException("Message ID must be UUID v7") }
        require(id.version() == 7) { "Message ID must be UUID v7" }
        require(message.senderId.matches(RED_ID)) { "Invalid sender YOUNES ID" }
        require(message.receiverId.matches(RED_ID)) { "Invalid receiver YOUNES ID" }
        require(message.conversationId.length in 8..128) { "Invalid conversation ID" }
        require(message.senderDeviceId in 1..127 && message.receiverDeviceId in 1..127) { "Invalid protocol device ID" }
        // V26: السماح بملاحظة لنفسي — sender == receiver مسموح فقط لمحادثة ذاتية
        if (message.senderId == message.receiverId) {
            require(
                message.conversationId.contains("self") ||
                message.conversationId.contains("note") ||
                message.conversationId == message.senderId ||
                message.conversationId.contains(message.senderId)
            ) { "Self-message must use self conversation" }
        } else {
            // للتحقق العادي نتأكد أن المحادثة ليست ذاتية
            require(message.senderId != message.receiverId || message.conversationId.contains("self")) { "Invalid receiver YOUNES ID" }
        }
        // وسائط/ملفات المجموعة (IMAGE/VIDEO/AUDIO/VOICE/FILE/STICKER + RICH_TEXT) تُرسل
        // بنص المجموعة المشفر (SenderKey) تماماً كـ GROUP_MESSAGE — لا تُقيَّد بـ GROUP_MESSAGE فقط.
        // أي رسالة بنص المجموعة يجب أن تكون بين عضوين فعليين في المجموعة (حماية الانتحال).
        // ⚠️ ترقيم الأنواع في libsignal 0.86+ (Rust): SENDERKEY_TYPE = 7 (كان 4 في الإصدارات القديمة)،
        // وWHISPER/PREKEY تبادلا بين 2 و3 — نقبل القديم والجديد معاً لتوافق الإصدارات.
        val isGroupCiphertext = message.ciphertextType == 4 || message.ciphertextType == 7
        if (isGroupCiphertext) enforceGroupMembership(message)
        val allowedCiphertext = isGroupCiphertext || message.ciphertextType == 2 || message.ciphertextType == 3
        require(allowedCiphertext) { "Unsupported libsignal ciphertext type for ${message.type}" }
        // 🔐 E2EE Hardening: Group messages must reference existing group and members only
        if (message.type in GROUP_TYPES) {
            require(message.conversationId.length in 8..128) { "Group conversation ID must be valid group" }
        }
        require(message.payload.size() in 1..1_048_576) { "Encrypted envelope must contain 1 byte to 1 MiB" }
        require(message.type.ifBlank { "TEXT" } in TYPES) { "Unsupported message type" }
    }

    private fun rank(status: String) = when (status) { "SENT" -> 1; "DELIVERED" -> 2; "READ" -> 3; else -> 0 }

    companion object {
        private val log = LoggerFactory.getLogger(MessageService::class.java)
        // مصدر الحقيقة الوحيد للنمط: RedIdGenerator.PATTERN.
        // تكرار النمط بصياغات مختلفة هو ما سمح سابقًا بتباين القبول
        // بين الوحدات (بادئة مقبولة هنا مرفوضة هناك).
        private val RED_ID = Regex(RedIdGenerator.PATTERN)
        private val TYPES = setOf("TEXT", "RICH_TEXT", "IMAGE", "VIDEO", "AUDIO", "VOICE", "FILE", "STICKER", "POLL", "SYSTEM", "GROUP_KEY_DISTRIBUTION", "GROUP_MESSAGE")
        private val GROUP_TYPES = setOf("GROUP_KEY_DISTRIBUTION", "GROUP_MESSAGE")
    }
}

/**
 * 😀 هدف تفاعل — أين تعيش الرسالة ومن يجب أن يستلم البث.
 * kind: PRIVATE | GROUP | CHANNEL — scopeId: conversationId | groupId | channelId.
 */
data class ReactionTarget(
    val messageUuid: String,
    val kind: String,
    val scopeId: String,
    val recipients: List<String>
)

/** ➡️ نتيجة تحويل — النسخة الجديدة ونطاقها ومصدرها. */
data class ForwardedMessage(
    val messageUuid: String,
    val scopeKind: String, // PRIVATE | GROUP
    val scopeId: String,
    val forwardedFrom: String?,
    val sequenceNumber: Long
)
