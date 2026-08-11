package com.red.server.social

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * 🔴 YOUNES Sovereign Status & Privacy Controller
 * حالات المستخدم + من يستطيع رؤية ماذا
 */
@RestController
@RequestMapping("/api/social")
class StatusController(
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
        authentication: Authentication
    ): ResponseEntity<StatusResponse> {
        val status = statusService.getVisibleStatus(userId, authentication.name)
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
        authentication: Authentication
    ): ResponseEntity<StatusResponse> {
        log.info("User {} updating status to {}", authentication.name, request.type)
        val updated = statusService.updateStatus(authentication.name, request.type, request.customText, request.visibleTo)
        return ResponseEntity.ok(StatusResponse(authentication.name, updated.type, updated.customText, updated.updatedAt))
    }

    /**
     * GET /api/social/privacy — جلب إعدادات الخصوصية
     */
    @GetMapping("/privacy")
    fun getPrivacySettings(
        authentication: Authentication
    ): ResponseEntity<PrivacySettingsResponse> {
        val settings = statusService.getPrivacySettings(authentication.name)
        return ResponseEntity.ok(settings)
    }

    /**
     * PUT /api/social/privacy — تحديث إعدادات الخصوصية
     */
    @PutMapping("/privacy")
    fun updatePrivacySettings(
        @RequestBody request: PrivacySettingsRequest,
        authentication: Authentication
    ): ResponseEntity<PrivacySettingsResponse> {
        log.info("User {} updating privacy settings", authentication.name)
        val updated = statusService.updatePrivacySettings(authentication.name, request)
        return ResponseEntity.ok(updated)
    }

    /**
     * GET /api/social/online-contacts — جلب جهات الاتصال المتصلة
     * يراعي الخصوصية لكل مستخدم
     */
    @GetMapping("/online-contacts")
    fun getOnlineContacts(
        authentication: Authentication
    ): ResponseEntity<List<OnlineContact>> {
        val contacts = statusService.getOnlineContacts(authentication.name)
        return ResponseEntity.ok(contacts)
    }
}

// ━━━━━━━━━━━━ DTOs ━━━━━━━━━━━━
// ملاحظة: PrivacySettingsRequest / PrivacySettingsResponse / OnlineContact مُعرّفة مرة واحدة
// في UserStatusService.kt (نفس الحزمة) — لا تُكرر هنا.

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
