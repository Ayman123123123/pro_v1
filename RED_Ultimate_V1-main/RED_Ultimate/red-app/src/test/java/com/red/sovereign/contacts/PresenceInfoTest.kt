package com.red.sovereign.contacts

import org.junit.Assert.*
import org.junit.Test

/**
 * ════════════════════════════════════════════════════════════════════════
 *  PresenceInfoTest — يتحقق من عقود الحضور وآخر ظهور
 *  PresenceInfo (online + lastSeen) + PublicRedProfile مع avatarUrl الجديد
 * ════════════════════════════════════════════════════════════════════════
 */
class PresenceInfoTest {

    @Test
    fun `PresenceInfo parses online user`() {
        val json = """{ "online": true, "lastSeen": 1691865600000 }""".trimIndent()
        val info = JsonLenient.decodeFromString<PresenceInfo>(json)
        assertTrue(info.online)
        assertEquals(1691865600000L, info.lastSeen)
    }

    @Test
    fun `PresenceInfo parses offline user with lastSeen`() {
        val json = """{ "online": false, "lastSeen": 1691865000000 }""".trimIndent()
        val info = JsonLenient.decodeFromString<PresenceInfo>(json)
        assertFalse(info.online)
        assertEquals(1691865000000L, info.lastSeen)
    }

    @Test
    fun `PresenceInfo parses offline user without lastSeen`() {
        val json = """{ "online": false }""".trimIndent()
        val info = JsonLenient.decodeFromString<PresenceInfo>(json)
        assertFalse(info.online)
        assertNull(info.lastSeen)
    }

    @Test
    fun `PresenceInfo defaults are safe`() {
        val info = PresenceInfo(online = false)
        assertFalse(info.online)
        assertNull(info.lastSeen)
    }

    @Test
    fun `PublicRedProfile parses with avatarUrl`() {
        val json = """
        {
          "redId": "16999",
          "username": "younes",
          "displayName": "يونس السيادي",
          "avatarUrl": "media/avatars/younes.webp"
        }
        """.trimIndent()
        val profile = JsonLenient.decodeFromString<PublicRedProfile>(json)
        assertEquals("16999", profile.redId)
        assertEquals("younes", profile.username)
        assertEquals("يونس السيادي", profile.displayName)
        assertEquals("media/avatars/younes.webp", profile.avatarUrl)
    }

    @Test
    fun `PublicRedProfile parses without avatarUrl (legacy compat)`() {
        // الحسابات القديمة قد لا تحوي avatarUrl — يجب أن يبقى متوافقاً
        val json = """
        { "redId": "16999", "username": "younes", "displayName": "يونس" }
        """.trimIndent()
        val profile = JsonLenient.decodeFromString<PublicRedProfile>(json)
        assertEquals("16999", profile.redId)
        assertEquals("younes", profile.username)
        assertEquals("يونس", profile.displayName)
        assertNull(profile.avatarUrl)
    }

    @Test
    fun `PresenceInfo map parses multiple users`() {
        // الـ endpoint يرجع Map<String, PresenceInfo>
        val json = """
        {
          "16999": { "online": true, "lastSeen": 1691865600000 },
          "17000": { "online": false, "lastSeen": 1691865000000 },
          "17001": { "online": false }
        }
        """.trimIndent()
        val map = JsonLenient.decodeFromString<Map<String, PresenceInfo>>(json)
        assertEquals(3, map.size)
        assertTrue(map["16999"]!!.online)
        assertFalse(map["17000"]!!.online)
        assertEquals(1691865000000L, map["17000"]!!.lastSeen)
        assertNull(map["17001"]!!.lastSeen)
    }
}

private val JsonLenient = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}
