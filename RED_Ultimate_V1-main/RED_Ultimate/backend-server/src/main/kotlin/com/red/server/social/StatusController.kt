package com.red.server.social

import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * 🔴 YOUNES Sovereign Status & Privacy Controller
 * حالات المستخدم + من يستطيع رؤية ماذا
 */
@RestController
@RequestMapping("/api/social")
class StatusController(
    private val userAccountRepository: UserAccountRepository,
    private val statusService: UserStatusService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * GET /api/social/status/{userId} — جلب حالة مستخدم
     * يراعي الخصوصية: لا يرجع الحالة إذا المستخدم حجبها
     */
    @GetMapping("/status/{userId}")
    fun getStatus(
        @PathVariable userId: String,
        @AuthenticationPrincipal requester: UserAccount
    ): ResponseEntity<StatusResponse> {
        val status = statusService.getVisibleStatus(userId, requester.id)
            ?: return ResponseEntity.ok(StatusResponse(userId, "OFFLINE", null, null))

        return ResponseEntity.ok(StatusResponse(
            userId = userId,
            type = status.type,
            customText = status.customText,
            updatedAt = status.updatedAt
        ))
    }

    /**
     * PUT /api/social/status — تحديث حالتي
     */
    @PutMapping("/status")
    fun updateMyStatus(
        @RequestBody request: UpdateStatusRequest,
        @AuthenticationPrincipal user: UserAccount
    ): ResponseEntity<StatusResponse> {
        log.info("User {} updating status to {}", user.id, request.type)
        val updated = statusService.updateStatus(user.id, request.type, request.customText, request.visibleTo)
        return ResponseEntity.ok(StatusResponse(user.id, updated.type, updated.customText, updated.updatedAt))
    }

    /**
     * GET /api/social/privacy — جلب إعدادات الخصوصية
     */
    @GetMapping("/privacy")
    fun getPrivacySettings(
        @AuthenticationPrincipal user: UserAccount
    ): ResponseEntity<PrivacySettingsResponse> {
        val settings = statusService.getPrivacySettings(user.id)
        return ResponseEntity.ok(settings)
    }

    /**
     * PUT /api/social/privacy — تحديث إعدادات الخصوصية
     */
    @PutMapping("/privacy")
    fun updatePrivacySettings(
        @RequestBody request: PrivacySettingsRequest,
        @AuthenticationPrincipal user: UserAccount
    ): ResponseEntity<PrivacySettingsResponse> {
        log.info("User {} updating privacy settings", user.id)
        val updated = statusService.updatePrivacySettings(user.id, request)
        return ResponseEntity.ok(updated)
    }

    /**
     * GET /api/social/online-contacts — جلب جهات الاتصال المتصلة
     * يراعي الخصوصية لكل مستخدم
     */
    @GetMapping("/online-contacts")
    fun getOnlineContacts(
        @AuthenticationPrincipal user: UserAccount
    ): ResponseEntity<List<OnlineContact>> {
        val contacts = statusService.getOnlineContacts(user.id)
        return ResponseEntity.ok(contacts)
    }
}

// ━━━━━━━━━━━━ DTOs ━━━━━━━━━━━━

data class StatusResponse(
    val userId: String,
    val type: String,
    val customText: String?,
    val updatedAt: Instant?
)

data class UpdateStatusRequest(
    val type: String, // ONLINE, OFFLINE, BUSY, AWAY, DO_NOT_DISTURB, INVISIBLE
    val customText: String? = null,
    val visibleTo: String = "EVERYONE" // EVERYONE, CONTACTS, NOBODY
)

data class PrivacySettingsRequest(
    val lastSeen: String? = null,
    val onlineStatus: String? = null,
    val profilePhoto: String? = null,
    val about: String? = null,
    val status: String? = null,
    val readReceipts: String? = null,
    val calls: String? = null,
    val groups: String? = null,
    val liveLocation: String? = null
)

data class PrivacySettingsResponse(
    val lastSeen: String,
    val onlineStatus: String,
    val profilePhoto: String,
    val about: String,
    val status: String,
    val readReceipts: String,
    val calls: String,
    val groups: String,
    val liveLocation: String
)

data class OnlineContact(
    val userId: String,
    val name: String,
    val status: String,
    val customText: String?
)
