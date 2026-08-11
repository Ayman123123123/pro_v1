package com.red.sovereign.media

import org.junit.Assert.*
import org.junit.Test

/**
 * ════════════════════════════════════════════════════════════════════════
 *  StickerApiTest — يتحقق من صحة DTOs الملصقات والعقود
 *  اختبارات serialization و round-trip و القيم الافتراضية
 * ════════════════════════════════════════════════════════════════════════
 */
class StickerApiTest {

    @Test
    fun `StickerPackDto parses real backend payload`() {
        val json = """
        {
          "id": "pack_001",
          "name": "حزم يونس الذهبية",
          "description": "ملصقات سيادية رسمية",
          "coverMediaKey": "media/stickers/pack001/cover.webp",
          "previewMediaKey": "media/stickers/pack001/preview.webp",
          "stickerCount": 24,
          "isOfficial": true,
          "isFree": true
        }
        """.trimIndent()
        val pack = JsonLenient.decodeFromString<StickerPackDto>(json)
        assertEquals("pack_001", pack.id)
        assertEquals("حزم يونس الذهبية", pack.name)
        assertEquals("ملصقات سيادية رسمية", pack.description)
        assertEquals("media/stickers/pack001/cover.webp", pack.coverMediaKey)
        assertEquals(24, pack.stickerCount)
        assertTrue(pack.isOfficial)
        assertTrue(pack.isFree)
    }

    @Test
    fun `StickerPackDto handles missing optional fields gracefully`() {
        val json = """
        { "id": "pack_002", "name": "حزمة بسيطة", "coverMediaKey": "key" }
        """.trimIndent()
        val pack = JsonLenient.decodeFromString<StickerPackDto>(json)
        assertEquals("pack_002", pack.id)
        assertEquals("حزمة بسيطة", pack.name)
        assertNull(pack.description)
        assertNull(pack.previewMediaKey)
        assertEquals(0, pack.stickerCount) // القيمة الافتراضية
        assertFalse(pack.isOfficial)
        assertTrue(pack.isFree)
    }

    @Test
    fun `StickerDto parses with emoji tags`() {
        val json = """
        {
          "id": "sticker_001",
          "packId": "pack_001",
          "name": "ابتسامة",
          "mediaKey": "media/stickers/pack001/s01.webp",
          "emojiTags": ["😀", "😊"],
          "displayOrder": 0
        }
        """.trimIndent()
        val sticker = JsonLenient.decodeFromString<StickerDto>(json)
        assertEquals("sticker_001", sticker.id)
        assertEquals("pack_001", sticker.packId)
        assertEquals("ابتسامة", sticker.name)
        assertEquals("media/stickers/pack001/s01.webp", sticker.mediaKey)
        assertEquals(2, sticker.emojiTags.size)
        assertEquals("😀", sticker.emojiTags[0])
        assertEquals(0, sticker.displayOrder)
    }

    @Test
    fun `StickerDto handles empty emoji tags`() {
        val json = """
        { "id": "s2", "packId": "p1", "mediaKey": "k", "displayOrder": 5 }
        """.trimIndent()
        val sticker = JsonLenient.decodeFromString<StickerDto>(json)
        assertEquals("s2", sticker.id)
        assertNull(sticker.name)
        assertTrue(sticker.emojiTags.isEmpty())
        assertEquals(5, sticker.displayOrder)
    }

    @Test
    fun `StickerMessagePayload round-trip encode decode`() {
        val original = StickerMessagePayload(
            mediaKey = "media/stickers/pack001/s01.webp",
            emoji = "😀",
            name = "ابتسامة"
        )
        val encoded = JsonLenient.encodeToString(StickerMessagePayload.serializer(), original)
        val decoded = JsonLenient.decodeFromString<StickerMessagePayload>(encoded)
        assertEquals(original.mediaKey, decoded.mediaKey)
        assertEquals(original.emoji, decoded.emoji)
        assertEquals(original.name, decoded.name)
    }

    @Test
    fun `StickerMessagePayload with null name round-trips`() {
        val original = StickerMessagePayload(mediaKey = "key", emoji = "🎨", name = null)
        val encoded = JsonLenient.encodeToString(StickerMessagePayload.serializer(), original)
        val decoded = JsonLenient.decodeFromString<StickerMessagePayload>(encoded)
        assertEquals("key", decoded.mediaKey)
        assertEquals("🎨", decoded.emoji)
        assertNull(decoded.name)
    }

    @Test
    fun `StickerMessagePayload decodes from minimal JSON`() {
        val json = """{ "mediaKey": "mk", "emoji": "👍" }""".trimIndent()
        val payload = JsonLenient.decodeFromString<StickerMessagePayload>(json)
        assertEquals("mk", payload.mediaKey)
        assertEquals("👍", payload.emoji)
        assertNull(payload.name)
    }
}

private val JsonLenient = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}
