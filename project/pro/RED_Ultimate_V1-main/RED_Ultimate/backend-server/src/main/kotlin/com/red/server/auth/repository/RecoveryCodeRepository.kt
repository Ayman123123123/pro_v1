package com.red.server.auth.repository

import com.red.server.auth.model.RecoveryCode
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RecoveryCodeRepository : JpaRepository<RecoveryCode, UUID> {
    fun findAllByUserIdAndUsedAtIsNull(userId: UUID): List<RecoveryCode>
}
