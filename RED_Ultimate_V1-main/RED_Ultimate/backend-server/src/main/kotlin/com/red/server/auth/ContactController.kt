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
class ContactController(private val contacts: ContactService) {
    @GetMapping fun list(auth: Authentication) = contacts.contacts(userId(auth))
    /** Presence is visible only for an established contact, never for arbitrary directory identities. */
    @GetMapping("/presence") fun presence(@RequestParam ids: String, auth: Authentication) =
        contacts.presence(userId(auth), ids.split(',').map(String::trim).filter(String::isNotEmpty))
    /** Presence مفصّل مع آخر ظهور — يُستخدم لعرض "آخر ظهور" في الواجهة. */
    @GetMapping("/presence/detailed") fun presenceDetailed(@RequestParam ids: String, auth: Authentication) =
        contacts.presenceDetailed(userId(auth), ids.split(',').map(String::trim).filter(String::isNotEmpty))
    @GetMapping("/requests") fun requests(auth: Authentication) = contacts.incoming(userId(auth))
    @PostMapping("/requests/{redId}") fun request(@PathVariable redId: String, auth: Authentication) = contacts.request(userId(auth), redId)
    @PostMapping("/requests/{requestId}/accept") fun accept(@PathVariable requestId: UUID, auth: Authentication): ResponseEntity<Void> {
        contacts.resolve(userId(auth), requestId, true); return ResponseEntity.noContent().build()
    }
    @PostMapping("/requests/{requestId}/reject") fun reject(@PathVariable requestId: UUID, auth: Authentication): ResponseEntity<Void> {
        contacts.resolve(userId(auth), requestId, false); return ResponseEntity.noContent().build()
    }
    @DeleteMapping("/{redId}") fun remove(@PathVariable redId: String, auth: Authentication): ResponseEntity<Void> {
        contacts.remove(userId(auth), redId); return ResponseEntity.noContent().build()
    }
    @PostMapping("/{redId}/block") fun block(@PathVariable redId: String, auth: Authentication): ResponseEntity<Void> {
        contacts.block(userId(auth), redId); return ResponseEntity.noContent().build()
    }
    @DeleteMapping("/{redId}/block") fun unblock(@PathVariable redId: String, auth: Authentication): ResponseEntity<Void> {
        contacts.unblock(userId(auth), redId); return ResponseEntity.noContent().build()
    }
    @GetMapping("/blocked") fun blocked(auth: Authentication) = contacts.blocked(userId(auth))
    @PostMapping("/reports") fun report(@RequestBody request: ReportRequest, auth: Authentication) = contacts.report(userId(auth), request)

    private fun userId(auth: Authentication) = UUID.fromString(auth.name)
}
