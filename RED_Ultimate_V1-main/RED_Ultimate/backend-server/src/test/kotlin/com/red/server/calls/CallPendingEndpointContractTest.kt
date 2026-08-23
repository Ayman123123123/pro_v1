package com.red.server.calls

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.PostMapping
import java.lang.reflect.Method

class CallPendingEndpointContractTest {
    @Test
    fun `pending offer pull is exposed as POST endpoint for Android polling`() {
        val method: Method = CallHistoryController::class.java.methods.first {
            it.name == "pullPending" && it.parameterTypes.firstOrNull() == CallHistoryController.PullPendingRequest::class.java
        }
        val mapping = method.getAnnotation(PostMapping::class.java)

        assertTrue(mapping != null, "Android polls pending offers with POST and the backend must expose PostMapping")
        assertTrue(mapping.value.contains("/pending"), "pending POST mapping must remain available")
    }
}
