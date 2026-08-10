package com.red.server.auth

import com.red.server.auth.security.DeviceCertificateService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/identity")
class IdentityAuthorityController(private val certificates: DeviceCertificateService) {
    @GetMapping("/authority")
    fun authority() = mapOf(
        "algorithm" to "ECDSA_P256_SHA256",
        "version" to "v1",
        "publicKey" to certificates.authorityPublicKey()
    )
}
