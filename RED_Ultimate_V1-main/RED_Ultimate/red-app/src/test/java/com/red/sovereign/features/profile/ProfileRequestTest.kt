package com.red.sovereign.features.profile

import com.red.sovereign.core.RichMessage
import org.junit.Assert.*
import org.junit.Test

/**
 * ════════════════════════════════════════════════════════════════════════
 *  ProfileRequestTest — يتحقق من عقد طلب تحديث البروفايل + حالات RichMessage
 *  UpdateProfileRequest (displayName + avatarUrl + bio) serialization
 * ════════════════════════════════════════════════════════════════════════
 */
class ProfileRequestTest {

    @Test
    fun `UpdateProfileRequest round-trip with all fields`() {
        val original = UpdateProfileRequest(
            displayName = "يونس السيادي",
            avatarUrl = "media/avatars/younes.webp",
            bio = "منصة سيادية محلية"
        )
        val encoded = JsonLenient.encodeToString(UpdateProfileRequest.serializer(), original)
        val decoded = JsonLenient.decodeFromString<UpdateProfileRequest>(encoded)
        assertEquals(original.displayName, decoded.displayName)
        assertEquals(original.avatarUrl, decoded.avatarUrl)
        assertEquals(original.bio, decoded.bio)
    }

    @Test
    fun `UpdateProfileRequest with only displayName`() {
        val original = UpdateProfileRequest(displayName = "يونس")
        val encoded = JsonLenient.encodeToString(UpdateProfileRequest.serializer(), original)
        val decoded = JsonLenient.decodeFromString<UpdateProfileRequest>(encoded)
        assertEquals("يونس", decoded.displayName)
        assertNull(decoded.avatarUrl)
        assertNull(decoded.bio)
    }

    @Test
    fun `UpdateProfileRequest decodes from backend response shape`() {
        // الـ backend يرجع { displayName, avatarUrl, bio } — يجب أن يطابق الـ request
        val json = """
        { "displayName": "يونس", "avatarUrl": "media/avatar.webp", "bio": "بايو" }
        """.trimIndent()
        val parsed = JsonLenient.decodeFromString<UpdateProfileRequest>(json)
        assertEquals("يونس", parsed.displayName)
        assertEquals("media/avatar.webp", parsed.avatarUrl)
        assertEquals("بايو", parsed.bio)
    }

    @Test
    fun `UpdateProfileRequest handles null avatar and bio`() {
        val json = """{ "displayName": "يونس" }""".trimIndent()
        val parsed = JsonLenient.decodeFromString<UpdateProfileRequest>(json)
        assertEquals("يونس", parsed.displayName)
        assertNull(parsed.avatarUrl)
        assertNull(parsed.bio)
    }

    // ─── RichMessage REACTION edge cases إضافية ───────────────────────────

    @Test
    fun `REACTION with multi-codepoint emoji round-trips`() {
        // إيموجي مركب (مثل 👨‍👩‍👧) — يتجاوز 16 حرفًا في UTF-16
        val original = RichMessage(action = "REACTION", reactionOf = "msg-1", emoji = "👨‍👩‍👧")
        val decoded = RichMessage.decode(RichMessage.encode(original))
        assertNotNull(decoded)
        assertEquals("👨‍👩‍👧", decoded!!.emoji)
    }

    @Test
    fun `REACTION with skin-tone modifier round-trips`() {
        val original = RichMessage(action = "REACTION", reactionOf = "msg-1", emoji = "👍🏾")
        val decoded = RichMessage.decode(RichMessage.encode(original))
        assertNotNull(decoded)
        assertEquals("👍🏾", decoded!!.emoji)
    }

    @Test
    fun `REACTION with arabic-text emoji substitute is valid`() {
        // إيموجي قد يكون نصًا — نتأكد أن الطول ≤ 16 مقبول
        val original = RichMessage(action = "REACTION", reactionOf = "msg-1", emoji = "❤️")
        val decoded = RichMessage.decode(RichMessage.encode(original))
        assertNotNull(decoded)
        assertEquals("❤️", decoded!!.emoji)
    }

    @Test
    fun `REACTION and EDIT do not collide`() {
        // رسالة تعديل لا يجب أن تُفسَّر كتفاعل والعكس
        val edit = RichMessage(action = "EDIT", text = "نص", editOf = "msg-1")
        val reaction = RichMessage(action = "REACTION", reactionOf = "msg-2", emoji = "👍")
        assertNotEquals(edit.action, reaction.action)
        assertNull(edit.reactionOf)
        assertNull(reaction.editOf)
    }

    @Test
    fun `REACTION_REMOVE then REACTION on same message is valid sequence`() {
        val remove = RichMessage(action = "REACTION_REMOVE", reactionOf = "msg-1")
        val reAdd = RichMessage(action = "REACTION", reactionOf = "msg-1", emoji = "❤️")
        assertEquals("msg-1", remove.reactionOf)
        assertEquals("msg-1", reAdd.reactionOf)
        assertNull(remove.emoji)
        assertEquals("❤️", reAdd.emoji)
    }
}

private val JsonLenient = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}
