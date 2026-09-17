package com.red.server.social

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.database.ChannelDocument
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * 📢 خدمة القنوات — V26
 * القناة = بث أحادي، المشتركون لا يرسلون إلا إذا كانوا ADMIN
 * مدعومة بـ PostgreSQL (channels, channel_members) و MongoDB (channels collection)
 */
@Service
class ChannelService(
    private val mongo: MongoTemplate,
    private val jdbc: JdbcTemplate,
    private val users: UserAccountRepository
) {
    data class CreateChannelRequest(
        @field:jakarta.validation.constraints.NotBlank val name: String,
        val username: String? = null,
        val description: String? = null,
        val isPublic: Boolean = true,
        /** P1-G: وضع البث — true = أدمن فقط ينشر (افتراضي القنوات). */
        val isBroadcast: Boolean = true
    )

    data class ChannelResponse(
        val id: String,
        val name: String,
        val username: String?,
        val description: String?,
        val ownerId: String,
        val isPublic: Boolean,
        val subscriberCount: Int,
        val createdAt: Instant,
        /** P1-G: وضع البث (أدمن فقط يرسل + ردود Thread + تفاعلات للمشتركين). */
        val isBroadcast: Boolean = true,
        /** P1-G: Boosts lite — عدّاد فقط، بدون NFT. */
        val boostsCount: Int = 0
    ) {
        /** P1-G: المستوى مشتق دائمًا: level = boosts/5 (لا يُخزّن منفصلًا). */
        val level: Int get() = levelForBoosts(boostsCount)
    }

    /** P1-G: مزايا المستوى — إيموجي جماعي + حدود أعلى، بدون أي أصل مشفّر/NFT. */
    data class ChannelLevelPerks(
        val level: Int,
        val hasCollectiveEmoji: Boolean,
        val maxFileMb: Int,
        val maxPinnedMessages: Int,
        val maxPostLength: Int
    )

    companion object {
        /** P1-G: كل 5 boosts = مستوى واحد. */
        const val BOOSTS_PER_LEVEL: Int = 5
        const val MAX_PERK_LEVEL: Int = 5

        @JvmStatic
        fun levelForBoosts(boostsCount: Int): Int =
            boostsCount.coerceAtLeast(0) / BOOSTS_PER_LEVEL

        @JvmStatic
        fun perksForLevel(level: Int): ChannelLevelPerks {
            val saturated = level.coerceIn(0, MAX_PERK_LEVEL)
            return when {
                saturated <= 0 -> ChannelLevelPerks(0, false, 100, 3, 1024)
                saturated == 1 -> ChannelLevelPerks(1, true, 200, 5, 2048)
                saturated == 2 -> ChannelLevelPerks(2, true, 500, 10, 4096)
                saturated == 3 -> ChannelLevelPerks(3, true, 1000, 20, 4096)
                else -> ChannelLevelPerks(saturated, true, 2000, 50, 8192)
            }
        }
    }

    fun create(actorId: UUID, req: CreateChannelRequest): ChannelResponse {
        val actor = users.findById(actorId).orElseThrow { NoSuchElementException("User not found") }
        require(req.name.trim().length in 2..100) { "اسم القناة 2-100 حرف" }
        req.username?.let {
            require(it.matches(Regex("^[a-zA-Z0-9_]{5,32}$"))) { "اسم المستخدم 5-32 حرف (أحرف/أرقام/_)" }
            require(!channelUsernameExists(it)) { "اسم المستخدم محجوز" }
        }
        val id = UUID.randomUUID().toString()
        val now = Instant.now()

        // P1-G: تجهيز عمودي البث والتعزيز إن لم يكونا موجودين (self-heal، يتجاهل الفشل offline).
        ensureBroadcastBoostColumns()

        // PostgreSQL — Instant لا يُستدل عليه في JDBC، نمرر Timestamp صريح
        val nowTs = java.sql.Timestamp.from(now)
        try {
            jdbc.update(
                """INSERT INTO channels(id, name, username, description, owner_id, is_public, is_broadcast, boosts_count, subscriber_count, created_at, updated_at)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                UUID.fromString(id), req.name.trim(), req.username?.trim()?.lowercase(), req.description?.trim(),
                actorId, req.isPublic, req.isBroadcast, 0, 1, nowTs, nowTs
            )
        } catch (e: java.sql.SQLException) {
            // fallback للأعمدة القديمة (قبل ترحيل P1-G) — يعمل دون is_broadcast/boosts_count.
            // مقيّد بـ 42703 (undefined_column) وحده: أي خطأ آخر (انتهاك UNIQUE على username
            // من سباق مع channelUsernameExists، انقطاع اتصال) يجب أن ينتشر لا أن يُبتلع
            // فيتحول إلى 500 غامض أو إلى صفٍّ ناقص.
            if (e.sqlState != "42703") throw e
            jdbc.update(
                """INSERT INTO channels(id, name, username, description, owner_id, is_public, subscriber_count, created_at, updated_at)
                   VALUES (?,?,?,?,?,?,?,?,?)""",
                UUID.fromString(id), req.name.trim(), req.username?.trim()?.lowercase(), req.description?.trim(),
                actorId, req.isPublic, 1, nowTs, nowTs
            )
        }
        jdbc.update(
            "INSERT INTO channel_members(channel_id, user_id, role) VALUES (?,?,?)",
            UUID.fromString(id), actorId, "OWNER"
        )

        // MongoDB مرآة (schemaless: isBroadcast/boostsCount تُضبط عبر $set لعدم توسيع Document هنا)
        mongo.save(
            ChannelDocument(
                id = id,
                name = req.name.trim(),
                username = req.username?.trim()?.lowercase(),
                description = req.description?.trim(),
                ownerId = actorId.toString(),
                isPublic = req.isPublic,
                subscriberCount = 1,
                createdAt = now,
                updatedAt = now
            )
        )
        runCatching {
            mongo.updateFirst(
                Query(Criteria.where("id").`is`(id)),
                Update().set("isBroadcast", req.isBroadcast).set("boostsCount", 0).set("level", 0),
                ChannelDocument::class.java
            )
        }

        return ChannelResponse(id, req.name.trim(), req.username?.lowercase(), req.description, actorId.toString(), req.isPublic, 1, now, req.isBroadcast, 0)
    }

    /** P1-G: قراءة صف قناة مع تحمّل غياب عمودي البث/التعزيز (قبل الترحيل). */
    private fun mapChannelRow(rs: ResultSet): ChannelResponse {
        return ChannelResponse(
            rs.getObject("id", UUID::class.java).toString(),
            rs.getString("name"),
            rs.getString("username"),
            rs.getString("description"),
            rs.getObject("owner_id", UUID::class.java).toString(),
            rs.getBoolean("is_public"),
            rs.getInt("subscriber_count"),
            rs.getTimestamp("created_at").toInstant(),
            readBooleanColumn(rs, "is_broadcast", true),
            readIntColumn(rs, "boosts_count", 0)
        )
    }

    private fun readBooleanColumn(rs: ResultSet, column: String, default: Boolean): Boolean {
        return try {
            val meta = rs.metaData
            var found = false
            for (i in 1..meta.columnCount) {
                if (meta.getColumnLabel(i).equals(column, ignoreCase = true)) { found = true; break }
            }
            if (!found) default else {
                val v = rs.getObject(column) ?: return default
                when (v) {
                    is Boolean -> v
                    is Number -> v.toInt() != 0
                    else -> v.toString().toBooleanStrictOrNull() ?: default
                }
            }
        } catch (_: Exception) { default }
    }

    private fun readIntColumn(rs: ResultSet, column: String, default: Int): Int {
        return try {
            val meta = rs.metaData
            var found = false
            for (i in 1..meta.columnCount) {
                if (meta.getColumnLabel(i).equals(column, ignoreCase = true)) { found = true; break }
            }
            if (!found) default else (rs.getObject(column) as? Number)?.toInt() ?: default
        } catch (_: Exception) { default }
    }

    /** P1-G: إنشاء العمودين إن غابا — ADD COLUMN IF NOT EXISTS (آمن للتكرار، يُتجاهل offline). */
    private fun ensureBroadcastBoostColumns() {
        runCatching { jdbc.execute("ALTER TABLE channels ADD COLUMN IF NOT EXISTS is_broadcast BOOLEAN NOT NULL DEFAULT TRUE") }
        runCatching { jdbc.execute("ALTER TABLE channels ADD COLUMN IF NOT EXISTS boosts_count INTEGER NOT NULL DEFAULT 0") }
    }

    fun listPublic(limit: Int = 50): List<ChannelResponse> {
        return jdbc.query(
            "SELECT * FROM channels WHERE is_public=true AND is_archived=false ORDER BY subscriber_count DESC LIMIT ?",
            { rs, _ -> mapChannelRow(rs) },
            limit.coerceIn(1, 100)
        )
    }

    fun get(channelId: String): ChannelResponse? {
        return try {
            jdbc.queryForObject(
                "SELECT * FROM channels WHERE id=?",
                { rs, _ -> mapChannelRow(rs) },
                UUID.fromString(channelId)
            )
        } catch (_: Exception) { null }
    }

    fun join(actorId: UUID, channelId: String): Boolean {
        val channel = get(channelId) ?: throw NoSuchElementException("Channel not found")
        if (!channel.isPublic) throw IllegalAccessException("Channel is private")
        return try {
            // P0-F: زيادة subscriber_count فقط عند INSERT ناجح — ON CONFLICT DO NOTHING RETURNING
            val inserted = jdbc.query(
                "INSERT INTO channel_members(channel_id, user_id, role) VALUES (?,?,?) ON CONFLICT DO NOTHING RETURNING channel_id::text",
                { rs, _ -> rs.getString(1) },
                UUID.fromString(channelId), actorId, "SUBSCRIBER"
            )
            if (inserted.isEmpty()) return false
            jdbc.update("UPDATE channels SET subscriber_count = subscriber_count + 1, updated_at=NOW() WHERE id=?", UUID.fromString(channelId))
            mongo.updateFirst(
                Query(Criteria.where("id").`is`(channelId)),
                Update().inc("subscriberCount", 1).set("updatedAt", Instant.now()),
                ChannelDocument::class.java
            )
            true
        } catch (_: Exception) { false }
    }

    fun leave(actorId: UUID, channelId: String): Boolean {
        val deleted = jdbc.update("DELETE FROM channel_members WHERE channel_id=? AND user_id=?", UUID.fromString(channelId), actorId) > 0
        if (deleted) {
            jdbc.update("UPDATE channels SET subscriber_count = GREATEST(0, subscriber_count - 1) WHERE id=?", UUID.fromString(channelId))
            mongo.updateFirst(
                Query(Criteria.where("id").`is`(channelId)),
                Update().inc("subscriberCount", -1),
                ChannelDocument::class.java
            )
        }
        return deleted
    }

    fun isMember(userId: UUID, channelId: String): Boolean {
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM channel_members WHERE channel_id=? AND user_id=?",
            Int::class.java, UUID.fromString(channelId), userId
        ) ?: 0
        return count > 0
    }

    fun isAdmin(userId: UUID, channelId: String): Boolean {
        return try {
            val role = jdbc.queryForObject(
                "SELECT role FROM channel_members WHERE channel_id=? AND user_id=?",
                String::class.java, UUID.fromString(channelId), userId
            )
            role in listOf("OWNER", "ADMIN", "MODERATOR")
        } catch (_: Exception) { false }
    }

    // ════════════════════════════════════════════════════
    // P1-G: وضع البث + Boosts lite + بحث سحابي مكمّل (فقط)
    // ════════════════════════════════════════════════════

    /** P1-G: هل يحق للمستخدم نشر منشور علوي؟ — البث: أدمن فقط / المفتوح: أي عضو. */
    fun canPost(userId: UUID, channelId: String): Boolean {
        val channel = get(channelId) ?: return false
        if (!isMember(userId, channelId)) return false
        return if (channel.isBroadcast) isAdmin(userId, channelId) else true
    }

    /** P1-G: ردود Thread — مسموحة للمشتركين في البث (لا نشر علوي)، وللأعضاء في المفتوح. */
    fun canReplyInThread(userId: UUID, channelId: String): Boolean {
        if (!isMember(userId, channelId)) return false
        return true // سياسة P1-G: الـ Thread مفتوح لكل الأعضاء؛ النشر العلوي وحده مقيّد في البث
    }

    /** P1-G: التفاعلات — مسموحة لكل الأعضاء متى كانت القناة تسمح (allow_reactions، افتراضي true). */
    fun canReact(userId: UUID, channelId: String): Boolean {
        if (!isMember(userId, channelId)) return false
        return try {
            jdbc.queryForObject(
                "SELECT allow_reactions FROM channels WHERE id=?",
                Boolean::class.java, UUID.fromString(channelId)
            ) ?: true
        } catch (_: Exception) { true }
    }

    data class ChannelBoosts(val boostsCount: Int, val level: Int)

    /** P1-G: قراءة التعزيز والمستوى (level = boosts/5). */
    fun getBoosts(channelId: String): ChannelBoosts {
        val count = try {
            jdbc.queryForObject(
                "SELECT boosts_count FROM channels WHERE id=?",
                Int::class.java, UUID.fromString(channelId)
            ) ?: 0
        } catch (_: Exception) { 0 }
        return ChannelBoosts(count.coerceAtLeast(0), levelForBoosts(count))
    }

    /** P1-G: مزايا مستوى القناة (إيموجي جماعي + حدود أعلى، بدون NFT). */
    fun levelPerks(channelId: String): ChannelLevelPerks =
        perksForLevel(getBoosts(channelId).level)

    /**
     * P1-G: تعزيز القناة (Boost lite) — عدّاد فقط، بدون NFT/توكن.
     * @return العدد الجديد بعد الزيادة.
     */
    fun boost(channelId: String, by: Int = 1): Int {
        require(by in 1..100) { "by must be 1..100" }
        get(channelId) ?: throw NoSuchElementException("Channel not found")
        ensureBroadcastBoostColumns()
        try {
            jdbc.update(
                "UPDATE channels SET boosts_count = boosts_count + ?, updated_at=NOW() WHERE id=?",
                by, UUID.fromString(channelId)
            )
        } catch (e: java.sql.SQLException) {
            // عمود مفقود رغم ensure (سباق ترحيل) — أعد المحاولة مرة بعد ensure.
            // مقيّد بـ 42703 (undefined_column)؛ أي خطأ آخر ينتشر بدل أن يُبتلع
            // فتظهر زيادة تعزيزات فاشلة كأنها نجحت.
            if (e.sqlState != "42703") throw e
            ensureBroadcastBoostColumns()
            jdbc.update(
                "UPDATE channels SET boosts_count = boosts_count + ?, updated_at=NOW() WHERE id=?",
                by, UUID.fromString(channelId)
            )
        }
        val boosts = getBoosts(channelId)
        runCatching {
            mongo.updateFirst(
                Query(Criteria.where("id").`is`(channelId)),
                Update().set("boostsCount", boosts.boostsCount).set("level", boosts.level)
                    .set("updatedAt", Instant.now()),
                ChannelDocument::class.java
            )
        }
        return boosts.boostsCount
    }

    /**
     * P1-G: بحث سحابي مكمّل للبحث المحلي (FTS5/Room offline أولًا).
     * يكمل ولا يستبدل: يُستدعى عند توفر اتصال فقط، والعميل يدمج عبر mergeChannelSearch.
     * مربوط بـ `GET /api/channels?search=` في ChannelController.list، ويستهلكه
     * العميل من Features/channels/ChannelsApi.searchMerged.
     */
    fun searchCloudComplement(query: String, limit: Int = 20): List<ChannelResponse> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val like = "%${q.replace("%", "\\%").replace("_", "\\_")}%"
        return try {
            jdbc.query(
                """SELECT * FROM channels
                   WHERE is_public=true AND is_archived=false
                     AND (name ILIKE ? ESCAPE '\' OR username ILIKE ? ESCAPE '\' OR description ILIKE ? ESCAPE '\')
                   ORDER BY subscriber_count DESC LIMIT ?""",
                { rs, _ -> mapChannelRow(rs) },
                like, like, like, limit.coerceIn(1, 100)
            )
        } catch (_: Exception) { emptyList() }
    }

    private fun channelUsernameExists(username: String): Boolean {
        val c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM channels WHERE username = ?",
            Int::class.java,
            username.trim().lowercase(),
        ) ?: 0
        return c > 0
    }

    fun postMessage(
        actorId: UUID,
        channelId: String,
        content: String,
        messageType: String,
        payload: String,
        replyToMessageId: String?,
        senderDeviceId: Int,
        ciphertextType: String
    ): ChannelMessageResponse {
        val channel = get(channelId) ?: throw NoSuchElementException("Channel not found")
        require(canPost(actorId, channelId)) { "NOT_AUTHORIZED_TO_POST" }
        require(content.length in 1..levelPerks(channelId).maxPostLength) { "CONTENT_TOO_LONG" }
        
        val messageId = "chmsg_${java.util.UUID.randomUUID().toString().replace("-", "").take(12)}"
        val now = Instant.now()
        
        // Store in PostgreSQL
        jdbc.update(
            """INSERT INTO channel_messages(id, channel_id, sender_id, content, message_type, payload, 
               reply_to_message_id, sender_device_id, ciphertext_type, created_at)
               VALUES (?,?,?,?,?,?,?,?,?,?)""",
            messageId, UUID.fromString(channelId), actorId, content, messageType, payload,
            replyToMessageId, senderDeviceId, ciphertextType, java.sql.Timestamp.from(now)
        )
        
        // Mirror to MongoDB
        mongo.save(com.red.server.database.ChannelMessageDocument(
            uuid = messageId,
            channelId = channelId,
            senderId = users.findById(actorId)?.redId ?: "",
            senderDeviceId = senderDeviceId,
            payload = java.util.Base64.getDecoder().decode(payload),
            messageType = messageType,
            ciphertextType = ciphertextType.toInt(),
            sequenceNumber = nextChannelSequence(channelId),
            replyToMessageUuid = replyToMessageId,
            createdAt = now
        ))
        
        return ChannelMessageResponse(
            messageId = messageId,
            channelId = channelId,
            senderId = users.findById(actorId)?.redId ?: "",
            content = content,
            messageType = messageType,
            timestamp = now,
            sequenceNumber = nextChannelSequence(channelId) - 1
        )
    }

    fun getMessages(
        actorId: UUID,
        channelId: String,
        limit: Int,
        before: String?,
        after: String?
    ): List<ChannelMessageResponse> {
        require(isMember(actorId, channelId)) { "NOT_A_CHANNEL_MEMBER" }
        
        val baseQuery = "SELECT * FROM channel_messages WHERE channel_id=? AND deleted_at IS NULL"
        var query = baseQuery
        val params = mutableListOf<Any>()
        params.add(UUID.fromString(channelId))
        
        if (before != null) {
            query += " AND id < ?"
            params.add(before)
        }
        if (after != null) {
            query += " AND id > ?"
            params.add(after)
        }
        
        query += " ORDER BY created_at DESC LIMIT ?"
        params.add(limit.coerceIn(1, 100))
        
        return jdbc.query(query, { rs, _ ->
            ChannelMessageResponse(
                messageId = rs.getString("id"),
                channelId = rs.getObject("channel_id", UUID::class.java).toString(),
                senderId = rs.getObject("sender_id", UUID::class.java).toString(),
                content = rs.getString("content"),
                messageType = rs.getString("message_type"),
                timestamp = rs.getTimestamp("created_at").toInstant(),
                sequenceNumber = rs.getLong("sequence_number")
            )
        }, *params.toTypedArray())
    }

    fun moderate(
        actorId: UUID,
        channelId: String,
        action: String,
        targetId: String,
        value: String?
    ): Boolean {
        require(isAdmin(actorId, channelId)) { "NOT_AUTHORIZED" }
        
        return when (action.uppercase()) {
            "DELETE_MESSAGE" -> {
                val deleted = jdbc.update(
                    "UPDATE channel_messages SET deleted_at=NOW() WHERE id=? AND channel_id=?",
                    targetId, UUID.fromString(channelId)
                ) > 0
                if (deleted) {
                    mongo.updateFirst(
                        Query(Criteria.where("uuid").`is`(targetId).and("channelId").`is`(channelId)),
                        Update().set("deletedAt", Instant.now()),
                        com.red.server.database.ChannelMessageDocument::class.java
                    )
                }
                deleted
            }
            "PIN_MESSAGE" -> {
                val perks = levelPerks(channelId)
                val pinnedCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM channel_messages WHERE channel_id=? AND pinned=true",
                    Int::class.java, UUID.fromString(channelId)
                ) ?: 0
                if (pinnedCount >= perks.maxPinnedMessages) {
                    throw IllegalArgumentException("MAX_PINNED_REACHED")
                }
                jdbc.update(
                    "UPDATE channel_messages SET pinned=true, pinned_at=NOW() WHERE id=? AND channel_id=?",
                    targetId, UUID.fromString(channelId)
                ) > 0
            }
            "UNPIN_MESSAGE" -> {
                jdbc.update(
                    "UPDATE channel_messages SET pinned=false, pinned_at=NULL WHERE id=? AND channel_id=?",
                    targetId, UUID.fromString(channelId)
                ) > 0
            }
            "MUTE_USER" -> {
                // Add to muted users table
                jdbc.update(
                    "INSERT INTO channel_muted_users(channel_id, user_id, muted_by, muted_at) VALUES (?,?,?,NOW()) ON CONFLICT DO NOTHING",
                    UUID.fromString(channelId), UUID.fromString(targetId), actorId
                ) > 0
            }
            "UNMUTE_USER" -> {
                jdbc.update(
                    "DELETE FROM channel_muted_users WHERE channel_id=? AND user_id=?",
                    UUID.fromString(channelId), UUID.fromString(targetId)
                ) > 0
            }
            "BAN_USER" -> {
                jdbc.update(
                    "UPDATE channel_members SET role='BANNED' WHERE channel_id=? AND user_id=?",
                    UUID.fromString(channelId), UUID.fromString(targetId)
                ) > 0
            }
            "UNBAN_USER" -> {
                jdbc.update(
                    "UPDATE channel_members SET role='SUBSCRIBER' WHERE channel_id=? AND user_id=? AND role='BANNED'",
                    UUID.fromString(channelId), UUID.fromString(targetId)
                ) > 0
            }
            "SLOWMODE" -> {
                val seconds = value?.toIntOrNull() ?: 0
                require(seconds >= 0 && seconds <= 86400) { "INVALID_SLOWMODE" }
                jdbc.update(
                    "UPDATE channels SET slow_mode_seconds=?, updated_at=NOW() WHERE id=?",
                    seconds, UUID.fromString(channelId)
                ) > 0
            }
            "UPDATE_SETTINGS" -> {
                value?.let { settings ->
                    // Parse JSON settings
                    import com.fasterxml.jackson.databind.ObjectMapper
                    val mapper = ObjectMapper()
                    val map = mapper.readValue(settings, Map::class.java)
                    val updateParts = mutableListOf<String>()
                    val updateParams = mutableListOf<Any>()
                    
                    map["allowReactions"]?.let { v ->
                        updateParts.add("allow_reactions=?")
                        updateParams.add(v)
                    }
                    map["allowThreads"]?.let { v ->
                        updateParts.add("allow_threads=?")
                        updateParams.add(v)
                    }
                    
                    if (updateParts.isNotEmpty()) {
                        updateParams.add(UUID.fromString(channelId))
                        jdbc.update(
                            "UPDATE channels SET ${updateParts.joinToString(", ")}, updated_at=NOW() WHERE id=?",
                            *updateParams.toTypedArray()
                        ) > 0
                    } else {
                        false
                    }
                } ?: false
            }
            else -> throw IllegalArgumentException("UNKNOWN_MODERATION_ACTION")
        }
    }

    private fun nextChannelSequence(channelId: String): Long {
        val sequence = mongo.findAndModify(
            Query(Criteria.where("id").`is`("channel:$channelId")), Update().inc("sequence", 1),
            org.springframework.data.mongodb.core.FindAndModifyOptions.options().upsert(true).returnNew(true),
            com.red.server.database.ConversationSequence::class.java
        ) ?: error("Unable to allocate channel sequence")
        return sequence.sequence
    }

    /** List channels belonging to a community */
    fun listByCommunity(communityId: String, userId: UUID?): List<ChannelResponse> {
        return try {
            jdbc.query(
                """SELECT * FROM channels WHERE community_id=? AND is_archived=false ORDER BY subscriber_count DESC""",
                { rs, _ -> mapChannelRow(rs) },
                UUID.fromString(communityId)
            )
        } catch (_: Exception) { emptyList() }
    }

    data class CreateChannelRequest(
        val name: String,
        val description: String? = null,
        val isPublic: Boolean = true,
        val communityId: String
    )

    data class ChannelResponse(
        val id: String,
        val name: String,
        val username: String?,
        val description: String?,
        val ownerId: String,
        val isPublic: Boolean,
        val subscriberCount: Int,
        val createdAt: Instant,
        val isBroadcast: Boolean = true,
        val boostsCount: Int = 0
    )
}

data class ChannelMessageResponse(
    val messageId: String,
    val channelId: String,
    val senderId: String,
    val content: String,
    val messageType: String,
    val timestamp: Instant,
    val sequenceNumber: Long
)
