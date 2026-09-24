package com.red.server.dinstar

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * 🧠 Human Behavior → Phone Number Learning (Call mode)
 * إدارة محرك سلوك المكالمات المتعلَّمة: التكوين، المجمّع، السجل، والتشغيل اليدوي.
 */
@RestController
@RequestMapping("/api/admin/dinstar/human-behavior")
class NumberLearningController(
    private val learning: NumberLearningService
) {
    @GetMapping("/number-learning")
    fun config(): ResponseEntity<*> = ResponseEntity.ok(
        learning.getConfig() + learning.stats()
    )

    @PutMapping("/number-learning/config")
    fun updateConfig(@RequestBody body: Map<String, Any?>, authentication: Authentication): ResponseEntity<*> =
        ResponseEntity.ok(learning.updateConfig(UUID.fromString(authentication.name), body))

    @GetMapping("/number-learning/pool")
    fun pool(): ResponseEntity<*> = ResponseEntity.ok(learning.listPool())

    @PostMapping("/number-learning/pool")
    fun addPool(@RequestBody body: Map<String, Any?>, authentication: Authentication): ResponseEntity<*> {
        @Suppress("UNCHECKED_CAST")
        val numbers = (body["numbers"] as? List<Map<String, String?>>)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "numbers array is required"))
        return ResponseEntity.ok(learning.addPoolNumbers(UUID.fromString(authentication.name), numbers))
    }

    @PatchMapping("/number-learning/pool/{id}")
    fun togglePool(@PathVariable id: UUID, @RequestBody body: Map<String, Any?>, authentication: Authentication): ResponseEntity<*> {
        val active = (body["active"] as? Boolean) ?: return ResponseEntity.badRequest().body(mapOf("error" to "active boolean is required"))
        return ResponseEntity.ok(learning.setPoolEntryActive(UUID.fromString(authentication.name), id, active))
    }

    @DeleteMapping("/number-learning/pool/{id}")
    fun deletePool(@PathVariable id: UUID, authentication: Authentication): ResponseEntity<*> =
        ResponseEntity.ok(learning.deletePoolEntry(UUID.fromString(authentication.name), id))

    @GetMapping("/number-learning/calls")
    fun calls(@RequestParam(required = false) limit: Int = 50): ResponseEntity<*> =
        ResponseEntity.ok(learning.listCalls(limit))

    @PostMapping("/number-learning/trigger")
    fun trigger(@RequestBody(required = false) body: Map<String, Any?>?, authentication: Authentication): ResponseEntity<*> {
        val port = (body?.get("port") as? Number)?.toInt()
        return ResponseEntity.ok(learning.triggerNow(UUID.fromString(authentication.name), port))
    }

    /** استكشاف قراءة فقط لمسارات Number Learning الأصلية على البوابة */
    @GetMapping("/probe")
    fun probe(): ResponseEntity<*> = ResponseEntity.ok(learning.probeNativeEndpoints())
}
