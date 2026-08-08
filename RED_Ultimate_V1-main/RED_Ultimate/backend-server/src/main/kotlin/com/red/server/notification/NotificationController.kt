package com.red.server.notification

import com.red.server.auth.model.UserAccount
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * 🔔 YOUNES Sovereign Notification Controller
 * مركز الإشعارات — تاريخ + قراءة + حذف + تفضيلات
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * GET /api/notifications — جلب إشعاراتي
     */
    @GetMapping
    fun getMyNotifications(
        @AuthenticationPrincipal user: UserAccount,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) type: String?
    ): ResponseEntity<NotificationPage> {
        val notifications = notificationService.getNotifications(user.id, page, size, type)
        val unreadCount = notificationService.getUnreadCount(user.id)
        return ResponseEntity.ok(NotificationPage(notifications, unreadCount, page))
    }

    /**
     * PUT /api/notifications/{id}/read — تعليم كـ مقروء
     */
    @PutMapping("/{id}/read")
    fun markAsRead(
        @PathVariable id: String,
        @AuthenticationPrincipal user: UserAccount
    ): ResponseEntity<Void> {
        notificationService.markAsRead(user.id, id)
        return ResponseEntity.ok().build()
    }

    /**
     * PUT /api/notifications/read-all — تعليم الكل كـ مقروء
     */
    @PutMapping("/read-all")
    fun markAllAsRead(
        @AuthenticationPrincipal user: UserAccount
    ): ResponseEntity<Void> {
        notificationService.markAllAsRead(user.id)
        return ResponseEntity.ok().build()
    }

    /**
     * DELETE /api/notifications/{id} — حذف إشعار
     */
    @DeleteMapping("/{id}")
    fun deleteNotification(
        @PathVariable id: String,
        @AuthenticationPrincipal user: UserAccount
    ): ResponseEntity<Void> {
        notificationService.delete(user.id, id)
        return ResponseEntity.ok().build()
    }

    /**
     * GET /api/notifications/unread-count — عدد غير المقروء
     */
    @GetMapping("/unread-count")
    fun getUnreadCount(
        @AuthenticationPrincipal user: UserAccount
    ): ResponseEntity<UnreadCountResponse> {
        val count = notificationService.getUnreadCount(user.id)
        return ResponseEntity.ok(UnreadCountResponse(count))
    }

    /**
     * GET /api/notifications/preferences — تفضيلات الإشعارات
     */
    @GetMapping("/preferences")
    fun getPreferences(
        @AuthenticationPrincipal user: UserAccount
    ): ResponseEntity<NotificationPreferences> {
        return ResponseEntity.ok(notificationService.getPreferences(user.id))
    }

    /**
     * PUT /api/notifications/preferences — تحديث تفضيلات الإشعارات
     */
    @PutMapping("/preferences")
    fun updatePreferences(
        @RequestBody request: NotificationPreferences,
        @AuthenticationPrincipal user: UserAccount
    ): ResponseEntity<NotificationPreferences> {
        return ResponseEntity.ok(notificationService.updatePreferences(user.id, request))
    }
}

// ━━━━━━━━━━━━ DTOs ━━━━━━━━━━━━

data class NotificationPage(
    val notifications: List<NotificationDto>,
    val unreadCount: Long,
    val page: Int
)

data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val senderId: String?,
    val senderName: String?,
    val threadId: String?,
    val isRead: Boolean,
    val createdAt: Instant
)

data class UnreadCountResponse(val count: Long)

data class NotificationPreferences(
    val messages: Boolean = true,
    val calls: Boolean = true,
    val groups: Boolean = true,
    val stories: Boolean = true,
    val live: Boolean = true,
    val system: Boolean = true,
    val dinstar: Boolean = true,
    val security: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String? = null, // "22:00"
    val quietHoursEnd: String? = null    // "08:00"
)
