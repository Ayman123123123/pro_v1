package com.red.server.auth

import com.red.server.audit.AuditService
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import com.red.server.auth.security.DeviceCertificateService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class RedApprovalService(
    private val users: UserAccountRepository,
    private val devices: UserDeviceRepository,
    private val certificates: DeviceCertificateService,
    private val refreshTokens: RefreshTokenService,
    private val audit: AuditService
) {
    @Transactional(readOnly = true)
    fun getPendingList(): List<UserAccountResponse> =
        users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.PENDING).map { user ->
            user.toResponse(devices.findAllByUserIdOrderByCreatedAtAsc(user.id))
        }

    @Transactional
    fun processAction(
        userId: UUID,
        action: AccountStatus,
        reason: String? = null,
        adminId: UUID? = null
    ): UserAccountResponse {
        require(action in allowedAdminActions) { "Unsupported account action: $action" }
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        require(user.role.name != "ADMIN" || action == AccountStatus.APPROVED) {
            "Administrator accounts cannot be blocked through this endpoint"
        }
        val accountDevices = devices.findAllByUserIdOrderByCreatedAtAsc(user.id)

        user.status = action
        user.updatedAt = Instant.now()
        user.rejectionReason = reason?.trim()?.takeIf { it.isNotEmpty() }

        if (action == AccountStatus.APPROVED) {
            user.approvedAt = Instant.now()
            user.approvedBy = adminId
            user.rejectionReason = null
            accountDevices.filter { it.status == DeviceStatus.PENDING }.forEach { device ->
                val certificate = certificates.issue(user, device)
                device.authorizationCertificate = certificate.compact
                device.certificateExpiresAt = certificate.expiresAt
                device.status = DeviceStatus.APPROVED
                device.approvedAt = Instant.now()
                devices.save(device)
            }
        } else {
            refreshTokens.revokeAll(user.id)
            if (action == AccountStatus.REJECTED || action == AccountStatus.BANNED) {
                accountDevices.filter { it.status != DeviceStatus.REVOKED }.forEach {
                    it.status = DeviceStatus.REVOKED
                    it.revokedAt = Instant.now()
                    devices.save(it)
                }
            }
        }

        users.save(user)
        audit.record(adminId, "ACCOUNT_${action.name}", user.id.toString(), mapOf("redId" to user.redId, "reason" to reason))
        return user.toResponse(accountDevices)
    }

    fun approveUser(userId: String): UserAccountResponse =
        processAction(UUID.fromString(userId), AccountStatus.APPROVED)

    fun rejectUser(userId: String): UserAccountResponse =
        processAction(UUID.fromString(userId), AccountStatus.REJECTED)

    fun banUser(userId: String): UserAccountResponse =
        processAction(UUID.fromString(userId), AccountStatus.BANNED)

    /**
     * رفع الحظر لا يعيد شهادات الأجهزة الملغاة. الحساب يعود إلى PENDING
     * حتى يسجّل المستخدم جهازاً جديداً ويُعتمد من الإدارة.
     */
    @Transactional
    fun restoreBannedToPending(userId: UUID, adminId: UUID?, reason: String? = null): UserAccountResponse {
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        require(user.status == AccountStatus.BANNED || user.status == AccountStatus.REJECTED || user.status == AccountStatus.SUSPENDED) {
            "ACCOUNT_NOT_BLOCKED"
        }
        require(user.role.name != "ADMIN") { "Administrator accounts cannot be restored through this endpoint" }
        refreshTokens.revokeAll(user.id)
        devices.findAllByUserIdOrderByCreatedAtAsc(user.id)
            .filter { it.status != DeviceStatus.REVOKED }
            .forEach {
                it.status = DeviceStatus.REVOKED
                it.revokedAt = Instant.now()
                devices.save(it)
            }
        user.status = AccountStatus.PENDING
        user.updatedAt = Instant.now()
        user.approvedAt = null
        user.approvedBy = null
        user.rejectionReason = reason?.trim()?.takeIf { it.isNotEmpty() } ?: "UNBAN_REQUIRES_DEVICE_REENROLLMENT"
        users.save(user)
        audit.record(adminId, "ACCOUNT_RESTORED_PENDING", user.id.toString(), mapOf("redId" to user.redId))
        return user.toResponse(devices.findAllByUserIdOrderByCreatedAtAsc(user.id))
    }

    @Transactional(readOnly = true)
    fun isAllowed(userId: String): Boolean =
        runCatching { users.findById(UUID.fromString(userId)).orElse(null)?.status == AccountStatus.APPROVED }
            .getOrDefault(false)

    companion object {
        private val allowedAdminActions = setOf(
            AccountStatus.APPROVED,
            AccountStatus.REJECTED,
            AccountStatus.SUSPENDED,
            AccountStatus.BANNED
        )
    }
}
