package com.red.server.auth.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_devices")
class UserDevice(
    @Id
    var id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserAccount = UserAccount(),

    @Column(name = "device_name", nullable = false, length = 100)
    var deviceName: String = "Android",

    @Column(nullable = false, length = 30)
    var platform: String = "ANDROID",

    @Column(name = "registration_id", nullable = false)
    var registrationId: Int = 0,

    @Column(name = "protocol_device_id", nullable = false)
    var protocolDeviceId: Int = 0,

    @Column(name = "signed_pre_key_id", nullable = false)
    var signedPreKeyId: Int = 0,

    @Column(name = "kyber_pre_key_id", nullable = false)
    var kyberPreKeyId: Int = 0,

    @Column(name = "identity_key", nullable = false, columnDefinition = "BYTEA")
    var identityKey: ByteArray = byteArrayOf(),

    @Column(name = "signed_pre_key", nullable = false, columnDefinition = "BYTEA")
    var signedPreKey: ByteArray = byteArrayOf(),

    @Column(name = "kyber_pre_key", nullable = false, columnDefinition = "BYTEA")
    var kyberPreKey: ByteArray = byteArrayOf(),

    @Column(name = "signed_pre_key_signature", nullable = false, columnDefinition = "BYTEA")
    var signedPreKeySignature: ByteArray = byteArrayOf(),

    @Column(name = "kyber_pre_key_signature", nullable = false, columnDefinition = "BYTEA")
    var kyberPreKeySignature: ByteArray = byteArrayOf(),

    @Column(name = "identity_fingerprint", nullable = false, length = 64)
    var identityFingerprint: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: DeviceStatus = DeviceStatus.PENDING,

    @Column(name = "authorization_certificate", length = 4096)
    var authorizationCertificate: String? = null,

    @Column(name = "certificate_expires_at")
    var certificateExpiresAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "approved_at")
    var approvedAt: Instant? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
)

enum class DeviceStatus { PENDING, APPROVED, REVOKED }
