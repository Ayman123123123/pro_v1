package com.red.server.auth.repository

import com.red.server.auth.model.RefreshSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefreshSessionRepository : JpaRepository<RefreshSession, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshSession?
    fun findAllByUserIdAndRevokedAtIsNull(userId: UUID): List<RefreshSession>
    fun findAllByDeviceIdAndRevokedAtIsNull(deviceId: UUID): List<RefreshSession>
}
