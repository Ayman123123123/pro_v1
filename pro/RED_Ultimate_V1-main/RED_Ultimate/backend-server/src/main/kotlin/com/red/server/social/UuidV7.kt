package com.red.server.social

import java.security.SecureRandom
import java.util.UUID

object UuidV7 {
    private val random = SecureRandom()
    fun next(): String {
        val timestamp = System.currentTimeMillis() and 0xFFFFFFFFFFFFL
        val most = (timestamp shl 16) or 0x7000L or random.nextInt(1 shl 12).toLong()
        val least = Long.MIN_VALUE or (random.nextLong() and 0x3FFFFFFFFFFFFFFFL)
        return UUID(most, least).toString()
    }
}
