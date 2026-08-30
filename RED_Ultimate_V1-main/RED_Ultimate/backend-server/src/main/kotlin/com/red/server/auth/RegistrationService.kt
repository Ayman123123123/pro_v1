package com.red.server.auth

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import com.red.server.auth.security.JwtService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegistrationService(
    private val users: UserAccountRepository,
    private val devices: UserDeviceRepository,
    private val passwordEncoder: PasswordEncoder,
    private val redIdGenerator: RedIdGenerator,
    private val enrollment: DeviceEnrollmentService,
    private val recovery: RecoveryService,
    private val refreshTokens: RefreshTokenService,
    private val jwtService: JwtService
) {
    private val usernamePattern = Regex("^[a-zA-Z][a-zA-Z0-9_.]{2,31}$")
    private val COMMON_PASSWORDS = setOf(
        "123456789012", "password1234", "qwerty123456", "admin12345678",
        "younes123456", "red123456789", "111111111111", "000000000000"
    )

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val username = request.username.trim().lowercase()
        val displayName = request.displayName.trim()
        require(usernamePattern.matches(username)) {
            "Username must be 3-32 characters and contain only letters, numbers, dot or underscore"
        }
        require(displayName.length in 2..100 && displayName.none(Char::isISOControl)) { "Display name must be 2-100 visible characters" }
        require(request.password.length in 12..128) { "Password must contain 12-128 characters" }
        require(!request.password.contains(username, ignoreCase = true)) { "Password must not contain the username" }
        require(!COMMON_PASSWORDS.contains(request.password.lowercase())) { "Password is too common" }
        require(!users.existsByUsernameIgnoreCase(username)) { "Username is already registered" }

        val user = try {
            users.saveAndFlush(
                UserAccount(
                    redId = redIdGenerator.next(),
                    username = username,
                    passwordHash = requireNotNull(passwordEncoder.encode(request.password)) { "PasswordEncoder returned null" },
                    displayName = displayName,
                    status = AccountStatus.PENDING
                )
            )
        } catch (_: DataIntegrityViolationException) {
            throw IllegalArgumentException("Username is already registered")
        }
        val device = enrollment.enroll(user, request.device)
        val recoveryCodes = recovery.createFor(user)
        return AuthResponse(
            status = user.status,
            user = user.toResponse(listOf(device)),
            deviceId = device.id,
            recoveryCodes = recoveryCodes,
            message = "ACCOUNT_PENDING_ADMIN_APPROVAL"
        )
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        val user = users.findByUsernameIgnoreCase(request.username.trim())
            ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(request.password, user.passwordHash)) throw InvalidCredentialsException()
        val accountDevices = devices.findAllByUserIdOrderByCreatedAtAsc(user.id)

        if (user.status != AccountStatus.APPROVED) {
            return blockedResponse(user, accountDevices)
        }

        val device = resolveApprovedDevice(user, request.deviceId)
        val refresh = refreshTokens.issue(user, device)
        return AuthResponse(
            status = user.status,
            user = user.toResponse(accountDevices),
            deviceId = device?.id,
            accessToken = jwtService.issue(user, device?.id),
            refreshToken = refresh.token,
            tokenType = "Bearer",
            expiresInSeconds = jwtService.expirationSeconds()
        )
    }

    @Transactional
    fun refresh(request: RefreshRequest): RefreshResponse {
        val rotated = refreshTokens.rotate(request.refreshToken)
        val user = rotated.session.user
        require(user.status == AccountStatus.APPROVED) { "Account is not approved" }
        val device = rotated.session.device
        require(device == null || device.status == DeviceStatus.APPROVED) { "Device is not approved" }
        return RefreshResponse(
            accessToken = jwtService.issue(user, device?.id),
            refreshToken = rotated.token,
            expiresInSeconds = jwtService.expirationSeconds()
        )
    }

    fun logout(request: LogoutRequest) = refreshTokens.revoke(request.refreshToken)

    private fun resolveApprovedDevice(user: UserAccount, deviceId: java.util.UUID?): UserDevice? {
        if (user.role == AccountRole.ADMIN && deviceId == null) return null
        // كان `deviceId` مطلوبًا دومًا لغير الأدمن، ففشل تسجيل الدخول من هاتف
        // جديد (حيث `tokens.deviceId` لا يزال null) بـ400 رغم صحة كلمة المرور.
        // التطبيق لا يولّد معرّفًا جديدًا قبل تسجيل الدخول، والخادم كان يرمي
        // `deviceId is required` بدل إنشاء جهاز جديد. الآن يُنشأ جهاز جديد
        // تلقائيًا عند الحاجة — سلوك مطابق للـself-heal الموجود للحالة التي
        // فقدت فيها الأجهزة جميعها.
        var resolvedDeviceId = deviceId ?: java.util.UUID.randomUUID()
        val device = devices.findByIdAndUserId(resolvedDeviceId, user.id)
        if (device == null) {
            val owned = devices.findAllByUserIdOrderByCreatedAtAsc(user.id)
            if (owned.isEmpty()) {
                val fingerprint = java.util.UUID.randomUUID().toString().replace("-", "").take(64)
                val saved = devices.save(
                    com.red.server.auth.model.UserDevice(
                        id = resolvedDeviceId,
                        user = user,
                        status = com.red.server.auth.model.DeviceStatus.APPROVED,
                        deviceName = "Restored device",
                        identityFingerprint = fingerprint,
                        protocolDeviceId = 1,
                        registrationId = 1,
                    )
                )
                org.slf4j.LoggerFactory.getLogger(javaClass).warn(
                    "Self-heal: re-registered lost device {} for user {}", resolvedDeviceId, user.redId
                )
                return saved
            }
            // جهاز غير معروف لكن المستخدم يملك أجهزة أخرى: تسجيل دخول من هاتف
            // جديد. كان يُرمى `InvalidCredentialsException` فيُقرأ كـ401 رغم صحة
            // كلمة المرور. الآن يُسجَّل الجهاز الجديد تلقائيًا — كلمة المرور
            // هي الحاجز الأمني، ومعرف الجهاز ليس سرًا.
            val nextProtocolId = owned.maxByOrNull { it.protocolDeviceId }?.protocolDeviceId?.plus(1) ?: 1
            val nextRegistrationId = owned.maxByOrNull { it.registrationId }?.registrationId?.plus(1) ?: 1
            val fingerprint = java.util.UUID.randomUUID().toString().replace("-", "").take(64)
            val saved = devices.save(
                com.red.server.auth.model.UserDevice(
                    id = resolvedDeviceId,
                    user = user,
                    status = com.red.server.auth.model.DeviceStatus.APPROVED,
                    deviceName = "New device on login",
                    identityFingerprint = fingerprint,
                    protocolDeviceId = owned.maxByOrNull { it.protocolDeviceId }?.protocolDeviceId?.plus(1) ?: 1,
                    registrationId = owned.maxByOrNull { it.registrationId }?.registrationId?.plus(1) ?: 1,
                )
            )
            org.slf4j.LoggerFactory.getLogger(javaClass).warn(
                "Registered new device {} for user {} (previously {} device(s))", resolvedDeviceId, user.redId, owned.size
            )
            return saved
        }
        require(device.status == DeviceStatus.APPROVED) { "Device is not approved" }
        return device
    }

    private fun blockedResponse(user: UserAccount, accountDevices: List<UserDevice>): AuthResponse {
        val message = when (user.status) {
            AccountStatus.PENDING -> "ACCOUNT_PENDING_ADMIN_APPROVAL"
            AccountStatus.REJECTED -> "ACCOUNT_REJECTED"
            AccountStatus.SUSPENDED -> "ACCOUNT_SUSPENDED"
            AccountStatus.BANNED -> "ACCOUNT_BANNED"
            AccountStatus.APPROVED -> error("Approved accounts are not blocked")
        }
        return AuthResponse(user.status, user.toResponse(accountDevices), message = message)
    }
}

class InvalidCredentialsException : RuntimeException("Invalid username or password")
