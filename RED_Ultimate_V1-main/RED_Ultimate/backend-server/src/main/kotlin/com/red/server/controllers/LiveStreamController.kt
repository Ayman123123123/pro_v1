package com.red.server.controllers

import com.red.server.calls.LiveStreamService
import com.red.server.calls.LiveStreamRecord
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Live broadcast management API.
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
class LiveStreamController(private val streams: LiveStreamService) {

    @GetMapping("/streams")
    fun listStreams(): List<LiveStreamRecord> = streams.getActiveStreams()

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
    fun start(@PathVariable streamId: String, @RequestParam broadcasterId: String): LiveStreamRecord =
        streams.startStream(streamId, broadcasterId)

    @PostMapping("/admin/streams/{streamId}/stop")
    fun stop(@PathVariable streamId: String): Map<String, Any> = mapOf(
        "streamId" to streamId,
        "stopped" to streams.stopStream(streamId)
    )
}
