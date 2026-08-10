package com.red.server.auth.security

import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Service
class DeviceCertificateService(
    @Value("\${red.identity-authority.private-key-path:}") private val privateKeyPath: String,
    @Value("\${red.identity-authority.public-key-path:}") private val publicKeyPath: String,
    @Value("\${red.identity-authority.certificate-valid-days:90}") private val validDays: Long
) {
    fun issue(user: UserAccount, device: UserDevice): IssuedDeviceCertificate {
        require(privateKeyPath.isNotBlank()) { "RED identity authority private key path is not configured" }
        val issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val expiresAt = issuedAt.plus(validDays, ChronoUnit.DAYS)
        val payload = listOf(
            "v1",
            user.id.toString(),
            user.redId,
            device.id.toString(),
            device.identityFingerprint,
            issuedAt.epochSecond.toString(),
            expiresAt.epochSecond.toString()
        ).joinToString("|")

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(loadPrivateKey())
        signer.update(payload.toByteArray(StandardCharsets.UTF_8))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return IssuedDeviceCertificate(
            compact = encoder.encodeToString(payload.toByteArray(StandardCharsets.UTF_8)) + "." +
                encoder.encodeToString(signer.sign()),
            expiresAt = expiresAt
        )
    }

    fun authorityPublicKey(): String {
        require(publicKeyPath.isNotBlank()) { "RED identity authority public key path is not configured" }
        return Base64.getEncoder().encodeToString(readPemOrDer(Path.of(publicKeyPath)))
    }

    private fun loadPrivateKey() = KeyFactory.getInstance("EC").generatePrivate(
        PKCS8EncodedKeySpec(readPemOrDer(Path.of(privateKeyPath)))
    )

    private fun readPemOrDer(path: Path): ByteArray {
        require(Files.isRegularFile(path)) { "Identity authority key file does not exist: $path" }
        val bytes = Files.readAllBytes(path)
        val text = bytes.toString(StandardCharsets.US_ASCII)
        return if (text.contains("-----BEGIN")) {
            Base64.getMimeDecoder().decode(
                text.lineSequence().filterNot { it.startsWith("-----") }.joinToString("")
            )
        } else bytes
    }
}

data class IssuedDeviceCertificate(val compact: String, val expiresAt: Instant)
