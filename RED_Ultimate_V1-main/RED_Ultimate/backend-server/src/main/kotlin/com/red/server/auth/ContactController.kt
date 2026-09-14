package com.red.server.auth

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/contacts")
class ContactController(
    private val contacts: ContactService,
    private val rateLimit: RateLimitService
) {
    @GetMapping fun list(auth: Authentication) = contacts.contacts(userId(auth))
    /** Presence is visible only for an established contact, never for arbitrary directory identities. */
    @GetMapping("/presence") fun presence(@RequestParam ids: String, auth: Authentication): Map<String, Boolean> {
        val list = ids.split(',').map(String::trim).filter(String::isNotEmpty)
        require(list.size <= 100) { "TOO_MANY_IDS" }
        return contacts.presence(userId(auth), list)
    }
    /** Presence مفصّل مع آخر ظهور — يُستخدم لعرض "آخر ظهور" في الواجهة. */
    @GetMapping("/presence/detailed") fun presenceDetailed(@RequestParam ids: String, auth: Authentication): Map<String, Map<String, Any?>> {
        val list = ids.split(',').map(String::trim).filter(String::isNotEmpty)
        require(list.size <= 100) { "TOO_MANY_IDS" }
        return contacts.presenceDetailed(userId(auth), list)
            .mapValues { mapOf<String, Any?>("online" to it.value.online, "lastSeen" to it.value.lastSeen) }
    }
    @GetMapping("/requests") fun requests(auth: Authentication) = contacts.incoming(userId(auth))
    @GetMapping("/requests/outgoing") fun outgoing(auth: Authentication) = contacts.outgoing(userId(auth))
    // LEGENDARY FIX P0: حد السبام (كانت بلا أي حد فيسبب إغراق طلبات + تضخيم DB + إغراق الدفع)
    @PostMapping("/requests/{redId}") fun request(@PathVariable redId: String, auth: Authentication): Any {
        rateLimit.check("contacts-request", auth.name, 30L, java.time.Duration.ofMinutes(10))
        return contacts.request(userId(auth), redId)
    }
    @PostMapping("/requests/{requestId}/accept") fun accept(@PathVariable requestId: UUID, auth: Authentication): ResponseEntity<Void> {
        rateLimit.check("contacts-write", auth.name, 60L, java.time.Duration.ofMinutes(10))
        contacts.resolve(userId(auth), requestId, true); return ResponseEntity.noContent().build()
    }
    @PostMapping("/requests/{requestId}/reject") fun reject(@PathVariable requestId: UUID, auth: Authentication): ResponseEntity<Void> {
        rateLimit.check("contacts-write", auth.name, 60L, java.time.Duration.ofMinutes(10))
        contacts.resolve(userId(auth), requestId, false); return ResponseEntity.noContent().build()
    }
    @DeleteMapping("/requests/{requestId}") fun cancel(@PathVariable requestId: UUID, auth: Authentication): ResponseEntity<Void> {
        rateLimit.check("contacts-write", auth.name, 60L, java.time.Duration.ofMinutes(10))
        contacts.cancel(userId(auth), requestId); return ResponseEntity.noContent().build()
    }
    @DeleteMapping("/{redId}") fun remove(@PathVariable redId: String, auth: Authentication): ResponseEntity<Void> {
        rateLimit.check("contacts-write", auth.name, 60L, java.time.Duration.ofMinutes(10))
        contacts.remove(userId(auth), redId); return ResponseEntity.noContent().build()
    }
    @PostMapping("/{redId}/block") fun block(@PathVariable redId: String, auth: Authentication): ResponseEntity<Void> {
        rateLimit.check("contacts-block", auth.name, 30L, java.time.Duration.ofMinutes(10))
        contacts.block(userId(auth), redId); return ResponseEntity.noContent().build()
    }
    @DeleteMapping("/{redId}/block") fun unblock(@PathVariable redId: String, auth: Authentication): ResponseEntity<Void> {
        rateLimit.check("contacts-write", auth.name, 30L, java.time.Duration.ofMinutes(10))
        contacts.unblock(userId(auth), redId); return ResponseEntity.noContent().build()
    }
    @GetMapping("/blocked") fun blocked(auth: Authentication) = contacts.blocked(userId(auth))
    @PostMapping("/reports") fun report(@RequestBody request: ReportRequest, auth: Authentication): Any {
        // LEGENDARY: حد البلاغات 10/ساعة لمنع إغراق user_reports
        rateLimit.check("contacts-report", auth.name, 10L, java.time.Duration.ofHours(1))
        return contacts.report(userId(auth), request)
    }

    private fun userId(auth: Authentication) = UUID.fromString(auth.name)
}
