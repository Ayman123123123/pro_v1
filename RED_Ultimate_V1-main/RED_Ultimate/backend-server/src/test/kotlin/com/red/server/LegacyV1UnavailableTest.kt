package com.red.server

import com.red.server.calls.v1.CallsV1Controller
import com.red.server.media.v1.MediaV1Controller
import com.red.server.messaging.v1.MessageV1Controller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/** Do not resurrect a nonfunctional encryption, chunk upload or call facade. */
class LegacyV1UnavailableTest {
    @Test
    fun `legacy v1 operations explicitly return gone`() {
        assertEquals(HttpStatus.GONE, CallsV1Controller().gone().statusCode)
        assertEquals(HttpStatus.GONE, MessageV1Controller().gone().statusCode)
        assertEquals(HttpStatus.GONE, MediaV1Controller().gone().statusCode)
    }
}
