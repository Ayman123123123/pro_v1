package com.red.server.auth.repository

import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.model.UserDevice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserDeviceRepository : JpaRepository<UserDevice, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): UserDevice?

    @Query("SELECT d FROM UserDevice d WHERE d.user.id = :userId ORDER BY d.createdAt ASC")
    fun findAllByUserIdOrderByCreatedAtAsc(@Param("userId") userId: UUID): List<UserDevice>

    fun findAllByUserIdAndStatus(userId: UUID, status: DeviceStatus): List<UserDevice>
}
