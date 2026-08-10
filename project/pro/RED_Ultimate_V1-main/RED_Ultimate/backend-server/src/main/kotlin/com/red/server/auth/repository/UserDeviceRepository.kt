package com.red.server.auth.repository

import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.model.UserDevice
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserDeviceRepository : JpaRepository<UserDevice, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): UserDevice?
    fun findAllByUserIdOrderByCreatedAtAsc(userId: UUID): List<UserDevice>
    fun findAllByUserIdAndStatus(userId: UUID, status: DeviceStatus): List<UserDevice>
}
