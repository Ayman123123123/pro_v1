package com.red.server.auth

import com.red.server.auth.repository.UserAccountRepository
import org.springframework.stereotype.Component
import java.security.SecureRandom

@Component
class RedIdGenerator(private val users: UserAccountRepository) {
    private val random = SecureRandom()
    private val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun next(): String {
        repeat(100) {
            val value = buildString(11) {
                repeat(4) { append(alphabet[random.nextInt(alphabet.length)]) }
                append('-')
                repeat(4) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            val redId = "YNS-$value"
            if (!users.existsByRedId(redId)) return redId
        }
        error("Unable to allocate a unique YOUNES ID")
    }
}
