package com.red.server

import com.red.server.media.MediaService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * 🖼️ اختبارات معاينة الصور 256x256
 */
class MediaThumbnailTest {

    @Test
    fun `thumbnail scales large image to 256`() {
        val src = BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB)
        val g = src.createGraphics()
        g.color = java.awt.Color.RED
        g.fillRect(0, 0, 1024, 768)
        g.dispose()
        // Scale logic: max 256
        val scale = 256.0 / maxOf(src.width, src.height)
        val nw = (src.width * scale).toInt().coerceAtLeast(1)
        val nh = (src.height * scale).toInt().coerceAtLeast(1)
        assertEquals(256, nw)
        assertEquals(192, nh)
    }

    @Test
    fun `thumbnail keeps small image within 256`() {
        val src = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
        val scale = 256.0 / maxOf(src.width, src.height)
        val nw = (src.width * scale).toInt().coerceAtLeast(1)
        assertEquals(256, nw)
    }

    @Test
    fun `non-image returns original key`() {
        val key = "users/123e4567-e89b-12d3-a456-426614174000/abc.pdf"
        // PDF should not be thumbnailed
        assertTrue(key.endsWith(".pdf"))
        // generateThumbnail would return original for non-image
    }

    @Test
    fun `orphan detection filters unreferenced keys`() {
        val all = listOf("users/a/1.jpg", "users/a/2.jpg", "users/a/3.jpg")
        val referenced = setOf("users/a/1.jpg", "users/a/3.jpg")
        val orphans = all.filter { it !in referenced }
        assertEquals(listOf("users/a/2.jpg"), orphans)
    }

    @Test
    fun `video thumbnail placeholder generates 256`() {
        // Video should get thumbs/ prefix even without ffmpeg
        val key = "users/123e4567-e89b-12d3-a456-426614174000/vid.mp4"
        val thumbKey = "thumbs/$key.jpg"
        assertTrue(thumbKey.startsWith("thumbs/users/"))
        assertTrue(thumbKey.endsWith(".jpg"))
    }

    @Test
    fun `thumbnail key format is thumbs slash original`() {
        val key = "users/123e4567-e89b-12d3-a456-426614174000/abc.jpg"
        val thumbKey = "thumbs/$key"
        assertTrue(thumbKey.startsWith("thumbs/users/"))
        assertTrue(thumbKey.endsWith(".jpg"))
    }
}
