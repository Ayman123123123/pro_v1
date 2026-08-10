package com.red.server.auth.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserAccount(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "red_id", nullable = false, unique = true, length = 32)
    var redId: String = "",

    @Column(nullable = false, unique = true, length = 40)
    var username: String = "",

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = "",

    @Column(name = "full_name", nullable = false, length = 100)
    var displayName: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: AccountStatus = AccountStatus.PENDING,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: AccountRole = AccountRole.USER,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "approved_at")
    var approvedAt: Instant? = null,

    @Column(name = "approved_by")
    var approvedBy: UUID? = null,

    @Column(name = "rejection_reason", length = 500)
    var rejectionReason: String? = null,

    @Column(name = "last_seen")
    var lastSeen: Long? = null,

    @Column(name = "pstn_enabled", nullable = false)
    var pstnEnabled: Boolean = false,

    @Column(name = "pstn_daily_limit", nullable = false)
    var pstnDailyLimit: Int = 0,

    // ━━━ عمليات الأمان (الأعمدة مُنشأة في V17__User_Security_Operations.sql) ━━━
    @Column(name = "password_reset_required", nullable = false)
    var passwordResetRequired: Boolean = false,

    @Column(name = "password_reset_issued_at")
    var passwordResetIssuedAt: Instant? = null,

    @Column(name = "remote_wipe_status", nullable = false, length = 20)
    var remoteWipeStatus: String = "NONE",

    @Column(name = "remote_wipe_requested_at")
    var remoteWipeRequestedAt: Instant? = null,

    @Column(name = "remote_wipe_completed_at")
    var remoteWipeCompletedAt: Instant? = null,

    @Column(name = "managed_device_wipe_allowed", nullable = false)
    var managedDeviceWipeAllowed: Boolean = false,

    // لون عرض ثابت للمستخدم (العمود مُنشأ في V19__User_Avatar_Color.sql)
    @Column(name = "avatar_color", length = 20)
    var avatarColor: String? = null
)

enum class AccountStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED,
    BANNED
}

enum class AccountRole {
    USER,
    ADMIN
}
