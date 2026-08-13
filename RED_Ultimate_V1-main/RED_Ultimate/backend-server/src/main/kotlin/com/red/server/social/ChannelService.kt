package com.red.server.social

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.database.ChannelDocument
import com.red.server.database.ChannelMemberDocument
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
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
        val name: String,
        val username: String? = null,
        val description: String? = null,
        val isPublic: Boolean = true
    )

    data class ChannelResponse(
        val id: String,
        val name: String,
        val username: String?,
        val description: String?,
        val ownerId: String,
        val isPublic: Boolean,
        val subscriberCount: Int,
        val createdAt: Instant
    )

    fun create(actorId: UUID, req: CreateChannelRequest): ChannelResponse {
        val actor = users.findById(actorId).orElseThrow { NoSuchElementException("User not found") }
        require(req.name.trim().length in 2..100) { "اسم القناة 2-100 حرف" }
        req.username?.let {
            require(it.matches(Regex("^[a-zA-Z0-9_]{5,32}$"))) { "اسم المستخدم 5-32 حرف (أحرف/أرقام/_)" }
            require(!channelUsernameExists(it)) { "اسم المستخدم محجوز" }
        }
        val id = UUID.randomUUID().toString()
        val now = Instant.now()

        // PostgreSQL
        jdbc.update(
            """INSERT INTO channels(id, name, username, description, owner_id, is_public, created_at, updated_at)
               VALUES (?,?,?,?,?,?,?,?)""",
            UUID.fromString(id), req.name.trim(), req.username?.trim()?.lowercase(), req.description?.trim(),
            actorId, req.isPublic, now, now
        )
        jdbc.update(
            "INSERT INTO channel_members(channel_id, user_id, role) VALUES (?,?,?)",
            UUID.fromString(id), actorId, "OWNER"
        )

        // MongoDB مرآة
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

        return ChannelResponse(id, req.name.trim(), req.username?.lowercase(), req.description, actorId.toString(), req.isPublic, 1, now)
    }

    fun listPublic(limit: Int = 50): List<ChannelResponse> {
        return jdbc.query(
            "SELECT * FROM channels WHERE is_public=true AND is_archived=false ORDER BY subscriber_count DESC LIMIT ?",
            { rs, _ ->
                ChannelResponse(
                    rs.getObject("id", UUID::class.java).toString(),
                    rs.getString("name"),
                    rs.getString("username"),
                    rs.getString("description"),
                    rs.getObject("owner_id", UUID::class.java).toString(),
                    rs.getBoolean("is_public"),
                    rs.getInt("subscriber_count"),
                    rs.getTimestamp("created_at").toInstant()
                )
            },
            limit.coerceIn(1, 100)
        )
    }

    fun get(channelId: String): ChannelResponse? {
        return try {
            jdbc.queryForObject(
                "SELECT * FROM channels WHERE id=?",
                { rs, _ ->
                    ChannelResponse(
                        rs.getObject("id", UUID::class.java).toString(),
                        rs.getString("name"),
                        rs.getString("username"),
                        rs.getString("description"),
                        rs.getObject("owner_id", UUID::class.java).toString(),
                        rs.getBoolean("is_public"),
                        rs.getInt("subscriber_count"),
                        rs.getTimestamp("created_at").toInstant()
                    )
                },
                UUID.fromString(channelId)
            )
        } catch (_: Exception) { null }
    }

    fun join(actorId: UUID, channelId: String): Boolean {
        val channel = get(channelId) ?: throw NoSuchElementException("Channel not found")
        if (!channel.isPublic) throw IllegalAccessException("Channel is private")
        return try {
            jdbc.update(
                "INSERT INTO channel_members(channel_id, user_id, role) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                UUID.fromString(channelId), actorId, "SUBSCRIBER"
            )
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
        val role = jdbc.queryForObject(
            "SELECT role FROM channel_members WHERE channel_id=? AND user_id=?",
            String::class.java, UUID.fromString(channelId), userId
        )
        return role in listOf("OWNER", "ADMIN", "MODERATOR")
    }

    private fun channelUsernameExists(username: String): Boolean {
        val c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM channels WHERE username = ?",
            Int::class.java,
            username.trim().lowercase(),
        ) ?: 0
        return c > 0
    }
}
