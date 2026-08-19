package com.red.server.auth.security

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.net.URLDecoder

@Component
class JwtHandshakeInterceptor(
    private val jwtService: JwtService,
    private val users: UserAccountRepository,
    private val devices: UserDeviceRepository,
    private val tickets: WebSocketTicketService
) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val headerToken = request.headers.getFirst("Authorization")
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
        val authenticated = if (!headerToken.isNullOrBlank()) authenticateBearer(headerToken)
        else authenticateAdminTicket(request)
        val (user, deviceId, device) = authenticated ?: return reject(response)
        if (request.uri.path in ADMIN_ONLY_PATHS && user.role != AccountRole.ADMIN) {
            return rejectForbidden(response)
        }

        attributes["userId"] = user.redId
        attributes["accountId"] = user.id.toString()
        attributes["redId"] = user.redId
        attributes["role"] = user.role.name
        if (deviceId != null) attributes["deviceId"] = deviceId.toString()
        if (device != null) attributes["protocolDeviceId"] = device.protocolDeviceId
        return true
    }

    private fun authenticateBearer(token: String): AuthenticatedSocket? = runCatching {
        val user = users.findById(jwtService.userId(token)).orElse(null) ?: return@runCatching null
        val deviceId = jwtService.deviceId(token)
        val device = deviceId?.let { devices.findByIdAndUserId(it, user.id) }
        val deviceAllowed = if (deviceId != null) device?.status == DeviceStatus.APPROVED else user.role == AccountRole.ADMIN
        if (user.status != AccountStatus.APPROVED || !deviceAllowed) null else AuthenticatedSocket(user, deviceId, device)
    }.getOrNull()

    private fun authenticateAdminTicket(request: ServerHttpRequest): AuthenticatedSocket? {
        if (request.uri.path != "/ws/admin/logs") return null
        val ticket = request.uri.rawQuery
            ?.split('&')
            ?.firstOrNull { it.substringBefore('=') == "ticket" }
            ?.substringAfter('=', "")
            ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            ?.takeIf(String::isNotBlank)
            ?: return null
        val accountId = tickets.consume(ticket) ?: return null
        val user = users.findById(accountId).orElse(null) ?: return null
        return user.takeIf { it.status == AccountStatus.APPROVED && it.role == AccountRole.ADMIN }
            ?.let { AuthenticatedSocket(it, null, null) }
    }

    private fun reject(response: ServerHttpResponse): Boolean {
        response.setStatusCode(HttpStatus.UNAUTHORIZED)
        return false
    }

    private fun rejectForbidden(response: ServerHttpResponse): Boolean {
        response.setStatusCode(HttpStatus.FORBIDDEN)
        return false
    }

    private companion object {
        /** قنوات تعرض سجلات العتاد أو أحداث الإدارة لا يجوز فتحها بحساب مستخدم عادي. */
        val ADMIN_ONLY_PATHS = setOf("/ws/dinstar", "/ws/admin/logs")
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) = Unit
}

private data class AuthenticatedSocket(val user: UserAccount, val deviceId: java.util.UUID?, val device: UserDevice?)
