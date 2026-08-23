package com.red.server.api.admin

import com.red.server.auth.ApprovalActionRequest
import com.red.server.auth.RedApprovalService
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.searchForAdmin
import com.red.server.infrastructure.dinstar.DinstarMasterClient
import com.red.server.services.MasterStatsService
import com.red.server.services.RedSecurityService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RestController
@RequestMapping("/api/master/v1")
class RedMasterController(
    private val statsService: MasterStatsService,
    private val approvalService: RedApprovalService,
    private val dinstarClient: DinstarMasterClient,
    private val securityService: RedSecurityService,
    private val jdbc: JdbcTemplate,
    private val userRepository: UserAccountRepository,
    private val redis: StringRedisTemplate
) {
    @GetMapping("/stats/realtime")
    fun getGlobalStats() = ResponseEntity.ok(statsService.getLiveMetrics())

    @GetMapping("/auth/pending")
    fun listPending() = ResponseEntity.ok(approvalService.getPendingList())

    @PostMapping("/auth/action")
    fun handleUserAction(
        @RequestBody request: ApprovalActionRequest,
        authentication: Authentication
    ) = ResponseEntity.ok(
        approvalService.processAction(
            request.userId,
            request.action,
            request.reason,
            UUID.fromString(authentication.name)
        )
    )

    @GetMapping("/hardware/dinstar/slots")
    fun getSlots() = ResponseEntity.ok(dinstarClient.getPortsRealtimeStatus())

    @PostMapping("/security/wipe")
    fun initiateWipe(@RequestParam userId: String) =
        ResponseEntity.ok(securityService.sendWipeSignal(userId))

    @GetMapping("/media/active-calls")
    fun getActiveCalls() = ResponseEntity.ok(statsService.getVoipMetrics())

    /** سجل المكالمات الموحّد للوحة الأدمن (RED + DINSTAR) من Postgres. */
    @GetMapping("/calls/history")
    fun getCallHistory(
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<Any> {
        val safeLimit = limit.coerceIn(1, 500)
        val safeOffset = offset.coerceAtLeast(0)
        val where = status?.takeIf { it.isNotBlank() }?.let { " WHERE status = ?" } ?: ""
        val total = jdbc.queryForObject(
            "SELECT count(*) FROM call_history$where",
            Int::class.java,
            *if (where.isBlank()) arrayOf() else arrayOf(status!!)
        ) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT id, caller_id, callee_id, callee_phone, call_type, call_route, direction, status,
                   started_at, answered_at, ended_at, duration_ms
            FROM call_history$where
            ORDER BY started_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *if (where.isBlank()) arrayOf<Any>(safeLimit, safeOffset) else arrayOf<Any>(status!!, safeLimit, safeOffset)
        )
        return ResponseEntity.ok(mapOf("total" to total, "calls" to rows))
    }

    // ════════════════════════════════════════════════════════════════════
    // PSTN Management — إدارة خدمة PSTN للمستخدمين
    // ═══════════════════════════════════════════════════════════════════

    data class PstnSettings(
        val userId: UUID,
        val redId: String,
        val username: String,
        val displayName: String,
        val pstnEnabled: Boolean,
        val pstnDailyLimit: Int,
        val usedToday: Int,
        val accountStatus: String,
        val role: String
    )

    data class PstnUpdateRequest(
        val pstnEnabled: Boolean,
        val pstnDailyLimit: Int
    )

    data class PstnUserListItem(
        val userId: UUID,
        val redId: String,
        val username: String,
        val displayName: String,
        val pstnEnabled: Boolean,
        val pstnDailyLimit: Int,
        val usedToday: Int,
        val accountStatus: String,
        val role: String
    )

    /** قائمة المستخدمين مع حالة PSTN — للوحة الأدمن (تصفية pstnEnabled على مستوى DB قبل pagination) */
    @GetMapping("/pstn/users")
    @PreAuthorize("hasRole('ADMIN')")
    fun listPstnUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) pstnEnabled: Boolean?
    ): ResponseEntity<Any> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

        val pageResult = userRepository.searchForAdmin(
            status = null,
            role = null,
            search = search,
            pageable = pageable,
            pstnEnabled = pstnEnabled
        )

        val users = pageResult.content.map { user ->
            val usedToday = getUsedToday(user.id)
            PstnUserListItem(
                userId = user.id,
                redId = user.redId,
                username = user.username,
                displayName = user.displayName,
                pstnEnabled = user.pstnEnabled,
                pstnDailyLimit = user.pstnDailyLimit,
                usedToday = usedToday,
                accountStatus = user.status.name,
                role = user.role.name
            )
        }

        return ResponseEntity.ok(mapOf(
            "content" to users,
            "totalElements" to pageResult.totalElements,
            "totalPages" to pageResult.totalPages,
            "number" to pageResult.number,
            "size" to pageResult.size
        ))
    }

    /** إعدادات PSTN لمستخدم محدد */
    @GetMapping("/pstn/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getPstnSettings(@PathVariable userId: UUID): ResponseEntity<Any> {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }
        val usedToday = getUsedToday(user.id)
        return ResponseEntity.ok(PstnSettings(
            userId = user.id,
            redId = user.redId,
            username = user.username,
            displayName = user.displayName,
            pstnEnabled = user.pstnEnabled,
            pstnDailyLimit = user.pstnDailyLimit,
            usedToday = usedToday,
            accountStatus = user.status.name,
            role = user.role.name
        ))
    }

    /** تحديث إعدادات PSTN (تفعيل/تعطيل، حد يومي) */
    @PatchMapping("/pstn/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updatePstnSettings(
        @PathVariable userId: UUID,
        @RequestBody request: PstnUpdateRequest
    ): ResponseEntity<Any> {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }
        
        require(request.pstnDailyLimit >= 0) { "Daily limit must be >= 0" }
        require(request.pstnDailyLimit <= 1000) { "Daily limit too high (max 1000)" }
        
        user.pstnEnabled = request.pstnEnabled
        user.pstnDailyLimit = request.pstnDailyLimit
        user.updatedAt = java.time.Instant.now()
        userRepository.save(user)
        
        val usedToday = getUsedToday(user.id)
        return ResponseEntity.ok(PstnSettings(
            userId = user.id,
            redId = user.redId,
            username = user.username,
            displayName = user.displayName,
            pstnEnabled = user.pstnEnabled,
            pstnDailyLimit = user.pstnDailyLimit,
            usedToday = usedToday,
            accountStatus = user.status.name,
            role = user.role.name
        ))
    }

    /** تبديل سريع لتفعيل/تعطيل PSTN */
    @PostMapping("/pstn/users/{userId}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    fun togglePstn(@PathVariable userId: UUID): ResponseEntity<Any> {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found: $userId") }
        
        user.pstnEnabled = !user.pstnEnabled
        user.updatedAt = java.time.Instant.now()
        userRepository.save(user)
        
        val usedToday = getUsedToday(user.id)
        return ResponseEntity.ok(PstnSettings(
            userId = user.id,
            redId = user.redId,
            username = user.username,
            displayName = user.displayName,
            pstnEnabled = user.pstnEnabled,
            pstnDailyLimit = user.pstnDailyLimit,
            usedToday = usedToday,
            accountStatus = user.status.name,
            role = user.role.name
        ))
    }

    private fun getUsedToday(userId: UUID): Int {
        val today = LocalDate.now(ZoneId.of("Asia/Aden"))
        val key = "red:pstn:daily:${userId}:$today"
        return try {
            val value = redis.opsForValue().get(key)
            value?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(RedMasterController::class.java)
                .warn("Redis daily usage read failed for $userId, returning 0: ${e.message}")
            0
        }
    }
}
