package com.red.server.calls

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * نظام pub/sub للـ call events.
 *
 * يُستخدم لإعلام:
 * - Admin dashboard (real-time)
 * - Analytics service
 * - Audit log
 *
 * الأحداث:
 * - CallStarted: عند بدء مكالمة
 * - CallAnswered: عند القبول
 * - CallEnded: عند الإنهاء (مع duration, reason)
 * - CallMissed: عند عدم الرد
 * - CallFailed: عند فشل تقني
 */
@Component
class CallEventPublisher(private val publisher: ApplicationEventPublisher) {
    companion object { private val log = LoggerFactory.getLogger(CallEventPublisher::class.java) }

    fun callStarted(callId: String, initiatorId: String, targetId: String, type: String, route: String) {
        publisher.publishEvent(CallEvent.CallStarted(callId, initiatorId, targetId, type, route, Instant.now()))
        log.info("CALL_STARTED callId={} type={} route={} from={} to={}", callId, type, route, initiatorId, targetId)
    }

    fun callAnswered(callId: String) {
        publisher.publishEvent(CallEvent.CallAnswered(callId, Instant.now()))
    }

    fun callEnded(callId: String, durationMs: Long, reason: String) {
        publisher.publishEvent(CallEvent.CallEnded(callId, durationMs, reason, Instant.now()))
    }

    fun callMissed(callId: String) {
        publisher.publishEvent(CallEvent.CallMissed(callId, Instant.now()))
    }

    fun callFailed(callId: String, error: String) {
        publisher.publishEvent(CallEvent.CallFailed(callId, error, Instant.now()))
        log.warn("CALL_FAILED callId={} error={}", callId, error)
    }
}

sealed interface CallEvent {
    val timestamp: Instant

    data class CallStarted(
        val callId: String,
        val initiatorId: String,
        val targetId: String,
        val type: String,
        val route: String,
        override val timestamp: Instant
    ) : CallEvent

    data class CallAnswered(val callId: String, override val timestamp: Instant) : CallEvent
    data class CallEnded(val callId: String, val durationMs: Long, val reason: String, override val timestamp: Instant) : CallEvent
    data class CallMissed(val callId: String, override val timestamp: Instant) : CallEvent
    data class CallFailed(val callId: String, val error: String, override val timestamp: Instant) : CallEvent
}
