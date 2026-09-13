package com.red.server.controllers

import com.red.server.calls.LiveStreamService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Live broadcast management API. (bean: liveStreamAdminController —
 * الاسم القديم LiveStreamController كان يتصادم مع نظيره في حزمة calls
 * على اسم الـ bean الافتراضي نفسه فيُسقط Spring Boot الإقلاع كله.)
 *
 *  - GET  /api/live/streams                       -> list of active streams
 *  - GET  /api/live/streams/{streamId}/viewers    -> current viewer count
 *  - POST /api/live/streams/{streamId}/viewers/join   (viewer identity comes from JWT)
 *  - POST /api/live/streams/{streamId}/viewers/leave  (viewer identity comes from JWT)
 *  - POST /api/live/admin/streams/{streamId}/start (ADMIN, ?broadcasterId=redId)
 *  - POST /api/live/admin/streams/{streamId}/stop  (ADMIN)
 *
 * Stream state is in-memory; WebRTC media itself flows through the SFU.
 */
@RestController
@RequestMapping("/api/live")
class LiveStreamAdminController(private val streams: LiveStreamService) {

    /** DTO آمن — لا يُسرّب passwordHash أبداً (إصلاح حرج). */
    data class SafeStreamDto(
        val streamId: String,
        val broadcasterId: String,
        val broadcasterName: String,
        val broadcasterRedId: String,
        val title: String,
        val category: String,
        val visibility: String,
        val isPrivate: Boolean,
        val startedAt: String,
        val endedAt: String?,
        val viewerCount: Int,
        val peakViewers: Int,
        val slowModeSec: Int,
        val recordingEnabled: Boolean,
        val broadcasterAvatar: String? = null
    )

    private fun sanitize(r: com.red.server.calls.LiveStreamRecord) = SafeStreamDto(
        streamId = r.streamId,
        broadcasterId = r.broadcasterId,
        broadcasterName = r.broadcasterName,
        broadcasterRedId = r.broadcasterRedId,
        title = r.title,
        category = r.category,
        visibility = r.visibility,
        isPrivate = r.isPrivate,
        startedAt = r.startedAt.toString(),
        endedAt = r.endedAt?.toString(),
        viewerCount = r.viewerCount,
        peakViewers = r.peakViewers,
        slowModeSec = r.slowModeSec,
        recordingEnabled = r.recordingEnabled,
        broadcasterAvatar = r.broadcasterAvatar
    )

    @GetMapping("/streams")
    fun listStreams(): List<SafeStreamDto> = streams.getActiveStreams().map(::sanitize)

    @GetMapping("/streams/{streamId}/viewers")
    fun viewerCount(@PathVariable streamId: String): Map<String, Any> = mapOf(
        "streamId" to streamId,
        "viewerCount" to streams.getViewerCount(streamId),
        "timestamp" to Instant.now().toEpochMilli()
    )

    @PostMapping("/streams/{streamId}/viewers/join")
    fun join(@PathVariable streamId: String, authentication: org.springframework.security.core.Authentication): Map<String, Any> {
        streams.addViewer(streamId, authentication.name)
        return mapOf("streamId" to streamId, "viewerCount" to streams.getViewerCount(streamId))
    }

    @PostMapping("/streams/{streamId}/viewers/leave")
    fun leave(@PathVariable streamId: String, authentication: org.springframework.security.core.Authentication): Map<String, Any> {
        streams.removeViewer(streamId, authentication.name)
        return mapOf("streamId" to streamId, "viewerCount" to streams.getViewerCount(streamId))
    }

    @PostMapping("/admin/streams/{streamId}/start")
    fun start(@PathVariable streamId: String, @RequestParam broadcasterId: String): SafeStreamDto =
        sanitize(streams.startStream(streamId, broadcasterId))

    @PostMapping("/admin/streams/{streamId}/stop")
    fun stop(@PathVariable streamId: String): Map<String, Any> = mapOf(
        "streamId" to streamId,
        "stopped" to streams.stopStream(streamId)
    )
}
