package com.red.server.admin.controller

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight admin event stream used by the dashboard log monitor.
 * The stream intentionally emits lifecycle/heartbeat events only; sensitive
 * audit data remains behind the paginated audit endpoints.
 */
@RestController
@RequestMapping("/api/admin/events")
class AdminEventsController(
    @Qualifier("adminSseScheduler") private val scheduler: ScheduledExecutorService
) {
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(): SseEmitter {
        val emitter = SseEmitter(0L)
        val closed = AtomicBoolean(false)
        var heartbeat: ScheduledFuture<*>? = null

        fun cleanup() {
            if (closed.compareAndSet(false, true)) heartbeat?.cancel(false)
        }

        fun sendEvent(type: String) {
            if (closed.get()) return
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(type.lowercase())
                        .data(mapOf("type" to type, "timestamp" to Instant.now().toString()))
                )
            } catch (error: Exception) {
                cleanup()
                emitter.completeWithError(error)
            }
        }

        emitter.onCompletion(::cleanup)
        emitter.onTimeout {
            cleanup()
            emitter.complete()
        }
        emitter.onError { cleanup() }

        sendEvent("READY")
        heartbeat = scheduler.scheduleAtFixedRate(
            { sendEvent("HEARTBEAT") },
            20,
            20,
            TimeUnit.SECONDS
        )
        return emitter
    }
}
