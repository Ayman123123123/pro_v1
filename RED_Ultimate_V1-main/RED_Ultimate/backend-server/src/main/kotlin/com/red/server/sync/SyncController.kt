package com.red.server.sync

import com.red.server.auth.repository.UserAccountRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/sync")
class SyncController(
    private val deviceSyncEvents: DeviceSyncEventRepository,
    private val deviceSyncStates: DeviceSyncStateRepository,
    private val users: UserAccountRepository
) {

    @GetMapping("/events")
    fun fetchEvents(
        @RequestParam("deviceId") deviceId: Int,
        @RequestParam("lastSeq", defaultValue = "0") lastSeq: Long,
        @RequestParam("limit", defaultValue = "100") limit: Int,
        auth: Authentication
    ): ResponseEntity<List<DeviceSyncEvent>> {
        val userUuid = UUID.fromString(auth.name)
        val events = deviceSyncEvents.findPendingEvents(userUuid, deviceId, lastSeq, limit.coerceIn(1, 500))
        return ResponseEntity.ok(events)
    }

    @PostMapping("/ack")
    fun acknowledgeEvents(
        @RequestParam("deviceId") deviceId: Int,
        @RequestParam("seq") seq: Long,
        auth: Authentication
    ): ResponseEntity<Void> {
        val userUuid = UUID.fromString(auth.name)
        
        // Update high-water mark
        val stateId = DeviceSyncStateId(userUuid, deviceId)
        val state = deviceSyncStates.findById(stateId).orElse(DeviceSyncState(id = stateId))
        if (seq > state.lastSyncSeq) {
            state.lastSyncSeq = seq
            state.lastSyncTime = java.time.Instant.now()
            deviceSyncStates.save(state)
        }
        
        // Mark events as delivered
        deviceSyncEvents.markAsDelivered(userUuid, deviceId, seq)
        
        return ResponseEntity.ok().build()
    }
}
