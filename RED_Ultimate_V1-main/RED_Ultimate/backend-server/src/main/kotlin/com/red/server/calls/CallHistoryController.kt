package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/calls")
class CallHistoryController(
    private val history: CallHistoryService,
    private val users: UserAccountRepository,
    private val notificationService: NotificationService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @GetMapping("/history")
    fun history(@RequestParam(defaultValue = "50") limit: Int, auth: Authentication): List<CallHistoryItem> {
        val user = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        return history.history(user.redId, limit)
    }

    /** FCM wake endpoint — يخزن العرض للسحب لاحقاً عند اتصال المستلم (Path 2 of Multi-Path Delivery). */
    @PostMapping("/push-notify")
    fun pushNotify(@RequestBody request: PushNotifyRequest, auth: Authentication): ResponseEntity<Any> {
        val user = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        // نخزن العرض مؤقتاً للسحب عند اتصال المستلم
        val key = "pending:${request.callId}:${request.targetRedId}"
        pendingOffers[key] = PendingOffer(
            callId = request.callId,
            targetRedId = request.targetRedId,
            callerId = request.callerId,
            mode = request.mode,
            offerSdp = request.offerSdp,
            ttlSeconds = request.ttlSeconds ?: 45,
            createdAt = Instant.now()
        )
        // أرسل إشعار FCM إذا كان الجهاز متصلاً (NotificationService يتعامل مع التوكنات)
        scope.launch { notificationService.sendVoipPushNotification(request.targetRedId, request.callerId, request.callId, request.mode) }
        return ResponseEntity.ok(mapOf("status" to "stored"))
    }

    /** سحب العرض المخزن عند اتصال المستلم (Path 3 of Multi-Path Delivery). */
    @PostMapping("/pending")
    fun pullPending(@RequestBody request: PullPendingRequest, auth: Authentication): ResponseEntity<Any> {
        val key = "pending:${request.callId}:${request.targetRedId}"
        val offer = pendingOffers.remove(key) ?: return ResponseEntity.notFound().build()
        if (offer.createdAt.plusSeconds(offer.ttlSeconds.toLong()).isBefore(Instant.now())) {
            return ResponseEntity.status(HttpStatus.GONE).body(mapOf("error" to "EXPIRED"))
        }
        return ResponseEntity.ok(mapOf(
            "callId" to offer.callId,
            "callerId" to offer.callerId,
            "mode" to offer.mode,
            "offerSdp" to offer.offerSdp,
            "ttlSeconds" to offer.ttlSeconds
        ))
    }

    data class PushNotifyRequest(
        val callId: String,
        val targetRedId: String,
        val callerId: String,
        val mode: String,
        val offerSdp: String,
        val ttlSeconds: Int? = null
    )

    data class PullPendingRequest(
        val callId: String,
        val targetRedId: String
    )

    data class PendingOffer(
        val callId: String,
        val targetRedId: String,
        val callerId: String,
        val mode: String,
        val offerSdp: String,
        val ttlSeconds: Int,
        val createdAt: Instant
    )

    companion object {
        private val pendingOffers = ConcurrentHashMap<String, PendingOffer>()
    }
}
