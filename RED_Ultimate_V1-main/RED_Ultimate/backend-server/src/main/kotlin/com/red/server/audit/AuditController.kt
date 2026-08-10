package com.red.server.audit

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/audit/recent")
class AuditController(private val audit: AuditService) {
    @GetMapping fun recent() = audit.recent()
}
