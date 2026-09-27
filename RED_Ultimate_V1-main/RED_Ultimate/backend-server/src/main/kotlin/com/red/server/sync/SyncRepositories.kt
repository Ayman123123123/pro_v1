package com.red.server.sync

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeviceSyncEventRepository : JpaRepository<DeviceSyncEvent, Long> {
    
    @Query("SELECT e FROM DeviceSyncEvent e WHERE e.userId = :userId AND e.deviceId = :deviceId AND e.id > :lastSeq ORDER BY e.id ASC LIMIT :limit")
    fun findPendingEvents(userId: UUID, deviceId: Int, lastSeq: Long, limit: Int): List<DeviceSyncEvent>

    @Modifying
    @Query("UPDATE DeviceSyncEvent e SET e.deliveredAt = CURRENT_TIMESTAMP WHERE e.userId = :userId AND e.deviceId = :deviceId AND e.id <= :seq")
    fun markAsDelivered(userId: UUID, deviceId: Int, seq: Long)
}

@Repository
interface DeviceSyncStateRepository : JpaRepository<DeviceSyncState, DeviceSyncStateId> {
    @Modifying
    @Query("UPDATE DeviceSyncState s SET s.lastSyncSeq = :seq, s.lastSyncTime = CURRENT_TIMESTAMP WHERE s.id.userId = :userId AND s.id.deviceId = :deviceId")
    fun updateSyncSeq(userId: UUID, deviceId: Int, seq: Long)
}

@Repository
interface GroupMessageReceiptRepository : JpaRepository<GroupMessageReceipt, Long> {
    fun findByMessageUuidAndStatus(messageUuid: String, status: String): List<GroupMessageReceipt>
    fun existsByMessageUuidAndUserId(messageUuid: String, userId: UUID): Boolean
}

@Repository
interface GroupMutationLogRepository : JpaRepository<GroupMutationLog, Long> {
    fun findByGroupIdOrderByIdDesc(groupId: UUID): List<GroupMutationLog>
}

@Repository
interface UserChatStateRepository : JpaRepository<UserChatState, UserChatStateId> {
    fun findByIdUserIdOrderByLastActivityTimeDesc(userId: UUID): List<UserChatState>
}
