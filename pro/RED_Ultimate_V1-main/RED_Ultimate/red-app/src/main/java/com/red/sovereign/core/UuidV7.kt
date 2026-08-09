package com.red.sovereign.core

import java.security.SecureRandom
import java.util.UUID

object UuidV7 {
    private val random = SecureRandom()
    fun next(): String {
        val timestamp = System.currentTimeMillis() and 0xFFFFFFFFFFFFL
        val randA = random.nextInt(1 shl 12).toLong()
        val randB = random.nextLong() and 0x3FFFFFFFFFFFFFFFL
        val most = (timestamp shl 16) or 0x7000L or randA
        val least = Long.MIN_VALUE or randB
        return UUID(most, least).toString()
    }
}
