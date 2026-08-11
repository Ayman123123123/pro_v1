package com.red.server

import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import com.red.server.auth.security.DeviceCertificateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class DeviceCertificateServiceTest {
    @Test
    fun `certificate binds account device and identity fingerprint with ECDSA P-256`() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val privateFile = Files.createTempFile("red-authority", ".pk8")
        val publicFile = Files.createTempFile("red-authority", ".spki")
        Files.write(privateFile, pair.private.encoded)
        Files.write(publicFile, pair.public.encoded)
        val service = DeviceCertificateService(privateFile.toString(), publicFile.toString(), 90)
        val user = UserAccount(redId = "62908", username = "ahmed", displayName = "Ahmed")
        val device = UserDevice(user = user, identityFingerprint = "f".repeat(64))

        val certificate = service.issue(user, device)
        val parts = certificate.compact.split('.')
        assertEquals(2, parts.size)
        val decoder = Base64.getUrlDecoder()
        val payload = decoder.decode(parts[0])
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pair.public)
        verifier.update(payload)
        assertTrue(verifier.verify(decoder.decode(parts[1])))
        assertTrue(payload.toString(Charsets.UTF_8).contains(device.id.toString()))
        assertEquals(Base64.getEncoder().encodeToString(pair.public.encoded), service.authorityPublicKey())
    }
}
