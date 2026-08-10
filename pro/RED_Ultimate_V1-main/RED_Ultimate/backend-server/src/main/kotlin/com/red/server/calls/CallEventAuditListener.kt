package com.red.server.calls

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * يكتب كل call event للـ audit log (بدون محتوى E2EE).
 * لا يحفظ peer ID في plain text — يحفظ hash فقط للبحث.
 */
@Component
class CallEventAuditListener {
    companion object { private val log = LoggerFactory.getLogger(CallEventAuditListener::class.java) }

    @EventListener
    fun onCallStarted(event: CallEvent.CallStarted) {
        log.info("AUDIT call_started hash_initiator={} hash_target={} type={} route={}",
            event.initiatorId.hashCode(), event.targetId.hashCode(), event.type, event.route)
    }

    @EventListener
    fun onCallAnswered(event: CallEvent.CallAnswered) {
        log.info("AUDIT call_answered callId={}", event.callId)
    }

    @EventListener
    fun onCallEnded(event: CallEvent.CallEnded) {
        log.info("AUDIT call_ended callId={} duration_ms={} reason={}", event.callId, event.durationMs, event.reason)
    }

    @EventListener
    fun onCallMissed(event: CallEvent.CallMissed) {
        log.info("AUDIT call_missed callId={}", event.callId)
    }

    @EventListener
    fun onCallFailed(event: CallEvent.CallFailed) {
        log.warn("AUDIT call_failed callId={} error={}", event.callId, event.error)
    }
}
