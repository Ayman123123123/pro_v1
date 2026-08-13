package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Dual-writes live Mongo call events into admin Postgres call_history
 * so the dashboard counters are no longer stuck at zero.
 */
@Component
class AdminCallHistorySync(
    private val jdbc: JdbcTemplate,
    private val users: UserAccountRepository
) {
    private val log = LoggerFactory.getLogger(AdminCallHistorySync::class.java)

    @EventListener
    fun onStarted(event: CallEvent.CallStarted) {
        val caller = users.findByRedId(event.initiatorId) ?: return
        val callee = users.findByRedId(event.targetId)
        val phone = event.targetId.takeIf { callee == null && AdminCallHistoryMapper.looksLikePhone(it) }
        val sqlId = AdminCallHistoryMapper.sqlId(event.callId)
        val sqlType = AdminCallHistoryMapper.sqlType(event.type, event.route)
        runCatching {
            jdbc.update(
                """
                INSERT INTO call_history
                    (id, caller_id, callee_id, callee_phone, call_type, call_route, direction, status, started_at)
                VALUES (?, ?, ?, ?, ?, ?, 'OUTGOING', 'RINGING', NOW())
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                sqlId,
                caller.id,
                callee?.id,
                phone,
                sqlType,
                if (event.route.equals("DINSTAR", ignoreCase = true)) "DINSTAR" else "RED"
            )
        }.onFailure { log.warn("admin call_history insert skipped: {}", it.message) }
    }

    @EventListener
    fun onAnswered(event: CallEvent.CallAnswered) {
        update(event.callId, "UPDATE call_history SET status = 'ACTIVE', answered_at = NOW() WHERE id = ?")
    }

    @EventListener
    fun onEnded(event: CallEvent.CallEnded) {
        runCatching {
            jdbc.update(
                "UPDATE call_history SET status = 'ENDED', ended_at = NOW(), duration_ms = ? WHERE id = ?",
                event.durationMs,
                AdminCallHistoryMapper.sqlId(event.callId)
            )
        }.onFailure { log.warn("admin call_history end skipped: {}", it.message) }
    }

    @EventListener
    fun onMissed(event: CallEvent.CallMissed) {
        update(event.callId, "UPDATE call_history SET status = 'MISSED', ended_at = NOW() WHERE id = ?")
    }

    @EventListener
    fun onFailed(event: CallEvent.CallFailed) {
        update(event.callId, "UPDATE call_history SET status = 'FAILED', ended_at = NOW() WHERE id = ?")
    }

    private fun update(callId: String, sql: String) {
        runCatching { jdbc.update(sql, AdminCallHistoryMapper.sqlId(callId)) }
            .onFailure { log.warn("admin call_history update skipped: {}", it.message) }
    }
}
