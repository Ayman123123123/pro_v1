package com.red.server.auth.security

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val users: UserAccountRepository,
    private val devices: UserDeviceRepository
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')

        if (!token.isNullOrBlank() && SecurityContextHolder.getContext().authentication == null) {
            runCatching {
                val user = users.findById(jwtService.userId(token)).orElse(null)
                val deviceId = jwtService.deviceId(token)
                val deviceAllowed = when {
                    user == null -> false
                    deviceId != null -> devices.findByIdAndUserId(deviceId, user.id)?.status == DeviceStatus.APPROVED
                    else -> user.role == AccountRole.ADMIN
                }
                if (user != null && user.status == AccountStatus.APPROVED && deviceAllowed) {
                    val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
                    SecurityContextHolder.getContext().authentication =
                        UsernamePasswordAuthenticationToken(user.id.toString(), token, authorities)
                }
            }
        }
        chain.doFilter(request, response)
    }
}
