package com.red.server.social

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * 📢 تحكم القنوات — V26
 * POST /api/channels — إنشاء قناة
 * GET /api/channels — قائمة القنوات العامة
 * GET /api/channels/{id} — تفاصيل
 * POST /api/channels/{id}/join — انضمام
 * POST /api/channels/{id}/leave — مغادرة
 */
@RestController
@RequestMapping("/api/channels")
class ChannelController(private val channels: ChannelService) {

    @PostMapping
    fun create(@RequestBody req: ChannelService.CreateChannelRequest, auth: Authentication): ResponseEntity<Any> {
        return try {
            val res = channels.create(UUID.fromString(auth.name), req)
            ResponseEntity.ok(mapOf("success" to true, "channel" to res))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @GetMapping
    fun list(@RequestParam(required = false) limit: Int = 20): ResponseEntity<Any> {
        val list = channels.listPublic(limit)
        return ResponseEntity.ok(mapOf("channels" to list, "count" to list.size))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Any> {
        val ch = channels.get(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ch)
    }

    @PostMapping("/{id}/join")
    fun join(@PathVariable id: String, auth: Authentication): ResponseEntity<Any> {
        val ok = channels.join(UUID.fromString(auth.name), id)
        return ResponseEntity.ok(mapOf("success" to ok))
    }

    @PostMapping("/{id}/leave")
    fun leave(@PathVariable id: String, auth: Authentication): ResponseEntity<Any> {
        val ok = channels.leave(UUID.fromString(auth.name), id)
        return ResponseEntity.ok(mapOf("success" to ok))
    }
}
