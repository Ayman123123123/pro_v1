package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.security.JwtService
import com.red.server.groups.GroupRole
import com.red.server.groups.GroupService
import com.red.server.websocket.ConferenceWebSocketHandler
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import com.red.server.auth.model.UserAccount
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.crypto.SecretKey

/** Issues a short-lived, group-membership-bound capability for one mediasoup room. */
@RestController
@RequestMapping("/api/sfu/groups")
class SfuTicketController(
    private val users: UserAccountRepository,
    private val groups: GroupService,
    private val jwt: JwtService,
    private val sfuTickets: SfuTicketSigner,
    private val activeCalls: ActiveCallRegistry,
    private val conferenceRooms: ConferenceRoomService,
    private val liveStreams: LiveStreamService,
    private val conferenceSignaling: ConferenceWebSocketHandler,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val aliases: RoomAliasService? = null,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val callSignaling: com.red.server.websocket.CallWebSocketHandler? = null
) {
    @GetMapping("/{groupId}/ticket")
    fun issue(@PathVariable groupId: String, authentication: Authentication): ResponseEntity<SfuTicketResponse> {
        // مسار تذكرة مجموعة: حل المستعار، والخام القديم يبقى مقبولاً.
        val effectiveGroupId = aliases?.resolve(groupId) ?: groupId.trim()
        require(effectiveGroupId.matches(ROOM_ID)) { "Invalid SFU room ID" }
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val accessToken = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        val deviceId = requireNotNull(jwt.deviceId(accessToken)) { "An approved device token is required" }
        val groupRole = groups.roleFor(accountId, effectiveGroupId)
            ?: throw NoSuchElementException("Group membership not found")
        val canProduce = groupRole in setOf(GroupRole.OWNER, GroupRole.ADMIN, GroupRole.MEMBER)
        val ticket = sfuTickets.issue(user, deviceId, effectiveGroupId, groupRole.name, canProduce)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(SfuTicketResponse(ticket, SFU_TICKET_EXPIRES_SECONDS, effectiveGroupId, groupRole.name, canProduce))
    }

    /** Conference / live rooms that are not a stored group still need a short SFU capability. */
    @GetMapping("/rooms/{roomId}/ticket")
    fun issueRoom(@PathVariable roomId: String, authentication: Authentication): ResponseEntity<SfuTicketResponse> {
        // مسار تذكرة انضمام: حل المستعار إلى القانوني، مع سقوط للخام القديم.
        val effectiveRoomId = aliases?.resolve(roomId) ?: roomId.trim()
        require(effectiveRoomId.matches(ROOM_ID)) { "Invalid SFU room ID" }
        val rawTrimmed = roomId.trim()
        // لا تذاكر لغرف عشوائية: الغرفة يجب أن تكون مؤتمراً/مساحة نشطة أو مكالمة جماعية مسجّلة.
        // هذا يمنع فحص الغرف واستنزاف موارد mediasoup عبر استدعاءات join مجهولة.
        val conferenceRoom = conferenceRooms.getRoom(effectiveRoomId)
            ?: conferenceRooms.getRoom(rawTrimmed)
            ?: if (RoomSeparationPolicy.kindOf(rawTrimmed) == RoomSeparationPolicy.RoomKind.LEGACY && RoomSeparationPolicy.isValidRoomId(rawTrimmed)) conferenceRooms.getRoom(RoomSeparationPolicy.PREFIX_CONF + rawTrimmed) else null
        // An active friends/group call (registered in the call signaling handler) also opens its
        // mediasoup room. Without this, friends/group calls could never obtain an SFU ticket and
        // were silently forced to mesh-only.
        val identifiers = listOf(effectiveRoomId, rawTrimmed).distinct()
        val groupRoomId = identifiers.firstOrNull { callSignaling?.groupCallHost(it) != null }
        val activeCallId = identifiers.firstOrNull { activeCalls.isActiveCall(it) }
        if (conferenceRoom == null && groupRoomId == null && activeCallId == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Room not open for SFU")
        }
        // Sign the actual registered room, not an unresolved alias (or a different room).
        val canonicalRoomId = conferenceRoom?.roomId ?: groupRoomId ?: activeCallId!!
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val authorized = when {
            conferenceRoom != null -> conferenceRooms.isParticipant(canonicalRoomId, authentication.name) &&
                conferenceRooms.canJoin(canonicalRoomId, authentication.name, user.redId)
            groupRoomId != null -> callSignaling?.isGroupCallParticipant(groupRoomId, user.redId) == true
            else -> activeCalls.isParticipant(activeCallId!!, user.redId)
        }
        if (!authorized) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a room participant")
        val accessToken = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        val deviceId = requireNotNull(jwt.deviceId(accessToken)) { "An approved device token is required" }
        // LEGENDARY Phase 7: مساحات الصوت — المستمع تذكرة استهلاك فقط (يمنع نشر عميل معَدَّل
        // متجاوزاً بوابة PRODUCE الخاصة بالـ mesh)؛ مؤتمرات الفيديو والمكالمات الجماعية للجميع.
        val roomRole = if (conferenceRoom != null && conferenceRoom.isSpace) {
            when {
                conferenceRoom.hostId == authentication.name -> "HOST"
                else -> conferenceSignaling.getRole(canonicalRoomId, user.redId, authentication.name)
            }
        } else "MEMBER"
        val canProduce = conferenceRoom == null || !conferenceRoom.isSpace || roomRole in setOf("HOST", "CO_HOST", "SPEAKER")
        val ticket = sfuTickets.issue(user, deviceId, canonicalRoomId, roomRole, canProduce = canProduce)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(SfuTicketResponse(ticket, SFU_TICKET_EXPIRES_SECONDS, canonicalRoomId, roomRole, canProduce))
    }

    /**
     * LIVE requires an explicit REST join before a viewer can obtain a media
     * capability. The broadcaster and approved co-hosts (max 4) receive producer permission.
     */
    @GetMapping("/live/{streamId}/ticket")
    fun issueLive(@PathVariable streamId: String, authentication: Authentication): ResponseEntity<SfuTicketResponse> {
        // مسار تذكرة بث: حل المستعار إلى القانوني، مع سقوط للخام القديم.
        val effectiveStreamId = aliases?.resolve(streamId) ?: streamId.trim()
        require(effectiveStreamId.matches(ROOM_ID)) { "Invalid live stream ID" }
        val rawTrimmed = streamId.trim()
        val record = liveStreams.getStreamRecord(effectiveStreamId)
            ?: liveStreams.getStreamRecord(rawTrimmed)
            ?: run {
                if (RoomSeparationPolicy.kindOf(rawTrimmed) == RoomSeparationPolicy.RoomKind.LEGACY && RoomSeparationPolicy.isValidRoomId(rawTrimmed)) liveStreams.getStreamRecord(RoomSeparationPolicy.PREFIX_LIVE + rawTrimmed) else null
            } ?: throw NoSuchElementException("Live stream not found or ended")
        val canonicalStreamId = record.streamId
        val accountId = UUID.fromString(authentication.name)
        val accountIdText = accountId.toString()
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val isBroadcaster = record.broadcasterId == accountIdText
        require(isBroadcaster || liveStreams.isViewerAny(canonicalStreamId, user.redId, accountIdText) || liveStreams.isViewerAny(rawTrimmed, user.redId, accountIdText)) {
            "Join the live stream before requesting media access"
        }
        // LEGENDARY Phase 6: approved co-hosts (max 4) publish to the SFU room too —
        // match either id form since approval targets arrive as redId.
        val isCoHost = !isBroadcaster && (liveStreams.getCoHosts(canonicalStreamId) + liveStreams.getCoHosts(rawTrimmed)).any { it == accountIdText || it == user.redId }
        val accessToken = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        val deviceId = requireNotNull(jwt.deviceId(accessToken)) { "An approved device token is required" }
        val ticket = sfuTickets.issue(
            user = user,
            deviceId = deviceId,
            groupId = canonicalStreamId,
            groupRole = if (isBroadcaster) "BROADCASTER" else if (isCoHost) "COHOST" else "VIEWER",
            canProduce = isBroadcaster || isCoHost
        )
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(SfuTicketResponse(ticket, SFU_TICKET_EXPIRES_SECONDS, canonicalStreamId, if (isBroadcaster) "BROADCASTER" else if (isCoHost) "COHOST" else "VIEWER", isBroadcaster || isCoHost))
    }

    companion object {
        /** معرف الغرفة موحّد مع SFU (4..128) ومع ConferenceWebSocketHandler — كان 8..128 فيسبب فشل الغرف القصيرة. */
        private val ROOM_ID = Regex("^[A-Za-z0-9_-]{4,128}$")
        /** يطابق JwtService.issueSfuTicket (10 دقائق) — كان 120 فيسبب تجديداً مبكراً خاطئاً. */
        const val SFU_TICKET_EXPIRES_SECONDS = 600L
    }
}

data class SfuTicketResponse(
    val token: String,
    val expiresInSeconds: Long,
    val roomId: String,
    val role: String,
    val canProduce: Boolean
)

/**
 * Issues the short-lived SFU media capability ticket.
 *
 * The signing key is derived from `red.jwt.sfu-secret` (env SFU_TICKET_SECRET — unified
 * with JwtService via application.yml `red.jwt.sfu-secret: ${SFU_TICKET_SECRET:}`),
 * and otherwise from the primary JWT secret (red.jwt.secret / JWT_SECRET). This lets operators
 * separate or rotate the media-ticket secret without touching login/refresh tokens, while keeping
 * a zero-config safe fallback that is byte-identical to the previous behaviour.
 *
 * In production (`prod` profile) the dedicated SFU secret is mandatory (fail-fast):
 * blank/weak/reused secrets refuse startup instead of silently falling back.
 *
 * The derivation (SHA-256(secret) -> HS256 key) intentionally matches media-sfu/server.js
 * authenticate() and JwtService so the SFU verifies the ticket with the same environment variable.
 */
@Service
class SfuTicketSigner(
    @Value("\${red.jwt.sfu-secret:}") private val configuredSfuSecret: String,
    @Value("\${red.jwt.secret}") private val configuredJwtSecret: String,
    @Value("\${red.jwt.issuer:red-sovereign}") private val issuer: String,
    @Value("\${red.jwt.audience:red-app}") private val audience: String
) {
    /** True when a dedicated SFU secret is configured (no secret shared with the JWT signer). */
    val dedicatedSecretInUse: Boolean
        get() = configuredSfuSecret.isNotBlank() && configuredSfuSecret != configuredJwtSecret

    @PostConstruct
    fun validateSfuSecretForProd() {
        if (!isProdEnvironment()) return
        require(configuredSfuSecret.isNotBlank()) {
            "FATAL: SFU_TICKET_SECRET (red.jwt.sfu-secret) is not set. Production cannot start with SFU fallback to JWT_SECRET."
        }
        require(configuredSfuSecret.length >= 32 && configuredSfuSecret != "change-me-in-production-please") {
            "FATAL: SFU_TICKET_SECRET must contain at least 32 random characters"
        }
        require(configuredSfuSecret != configuredJwtSecret) {
            "FATAL: SFU_TICKET_SECRET must differ from JWT_SECRET in production (SFU separation required)"
        }
    }

    private fun isProdEnvironment(): Boolean {
        val profiles = buildList {
            add(System.getProperty("spring.profiles.active", ""))
            add(System.getenv("SPRING_PROFILES_ACTIVE") ?: "")
            add(System.getenv("RED_ENV") ?: "")
            add(System.getenv("APP_ENV") ?: "")
        }.joinToString(" ").lowercase()
        return profiles.split(Regex("[,;\\s]+"))
            .any { it in setOf("prod", "production", "staging", "docker") }
    }

    private val signingKey: SecretKey by lazy {
        val secret = configuredSfuSecret.ifBlank {
            // Fail-fast in prod only — dev/test keep the safe fallback.
            check(!isProdEnvironment()) {
                "FATAL: SFU_TICKET_SECRET (red.jwt.sfu-secret) is not set. Production cannot start with SFU fallback to JWT_SECRET."
            }
            configuredJwtSecret
        }
        require(secret.length >= 32 && secret != "change-me-in-production-please") {
            "SFU_TICKET_SECRET (or JWT_SECRET) must contain at least 32 random characters"
        }
        if (isProdEnvironment()) {
            require(configuredSfuSecret.isNotBlank() && configuredSfuSecret != configuredJwtSecret) {
                "FATAL: SFU_TICKET_SECRET must differ from JWT_SECRET in production (SFU separation required)"
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(StandardCharsets.UTF_8))
        Keys.hmacShaKeyFor(digest)
    }

    fun issue(user: UserAccount, deviceId: UUID, groupId: String, groupRole: String, canProduce: Boolean): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("redId", user.redId)
            .claim("username", user.username)
            .claim("role", user.role.name)
            .claim("deviceId", deviceId.toString())
            .claim("sfuGroupId", groupId)
            .claim("sfuGroupRole", groupRole)
            .claim("sfuCanProduce", canProduce)
            .issuer(issuer)
            .audience().add(audience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(10, ChronoUnit.MINUTES)))
            .signWith(signingKey)
            .compact()
    }
}
