package com.red.sovereign.stories

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRequestSerializationTest {
    @Test
    fun `story privacy uses the server contract field names`() {
        val encoded = Json.encodeToString(
            CreateStoryRequest(
                mediaKey = "users/user-1/story.jpg",
                visibleTo = "SELECTED",
                audience = listOf("user-2", "user-3"),
            ),
        )

        assertTrue(encoded.contains("\"visibility\":\"SELECTED\""))
        assertTrue(encoded.contains("\"allowedUserIds\":[\"user-2\",\"user-3\"]"))
        assertFalse(encoded.contains("\"visibleTo\""))
        assertFalse(encoded.contains("\"audience\""))
    }
}
