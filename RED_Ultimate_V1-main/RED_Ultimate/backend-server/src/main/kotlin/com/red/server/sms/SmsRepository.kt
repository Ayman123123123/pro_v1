package com.red.server.sms

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SmsMessageRepository : JpaRepository<SmsMessageEntity, UUID> {

    fun findByOwnerIdOrderByCreatedAtDesc(ownerId: UUID): List<SmsMessageEntity>

    fun findByOwnerIdIsNullOrderByCreatedAtDesc(): List<SmsMessageEntity>

    fun findByOwnerIdAndNumberOrderByCreatedAtAsc(ownerId: UUID, number: String): List<SmsMessageEntity>

    fun findByOwnerIdIsNullAndNumberOrderByCreatedAtAsc(number: String): List<SmsMessageEntity>

    fun findByStatusAndCreatedAtAfter(status: SmsStatus, after: Instant): List<SmsMessageEntity>

    @Query(
        "SELECT DISTINCT m.number FROM SmsMessageEntity m " +
            "WHERE (m.ownerId = :ownerId OR m.ownerId IS NULL) AND m.status = 'SENT' AND m.createdAt > :after"
    )
    fun findSentNumbersSince(@Param("ownerId") ownerId: UUID?, @Param("after") after: Instant): List<String>
}

interface SmsReadMarkerRepository : JpaRepository<SmsReadMarker, SmsReadMarkerKey> {
    fun findByUserIdAndNumber(userId: UUID, number: String): SmsReadMarker?
    fun findByUserId(userId: UUID): List<SmsReadMarker>
}