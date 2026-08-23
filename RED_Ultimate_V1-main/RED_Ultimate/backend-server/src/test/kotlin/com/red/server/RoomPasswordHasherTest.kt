package com.red.server

import com.red.server.calls.RoomPasswordHasher
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoomPasswordHasherTest {
    private val hasher = RoomPasswordHasher()

    @Test
    fun `hash format round trips the correct password`() {
        val storedHash = hasher.hash("بث-خاص-آمن")

        assertTrue(storedHash.split('$').size == 4)
        assertTrue(hasher.verify("بث-خاص-آمن", storedHash))
        assertFalse(hasher.verify("بث-مختلف", storedHash))
    }
}
