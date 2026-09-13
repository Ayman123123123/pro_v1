package com.red.sovereign.media

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ⚡ EncryptedMediaStreamer — محرك التدفق الفوري المشفّر للوسائط (Zero-Latency Streaming)
 *
 * يتيح قراءة وفك تشفير مقاطع الفيديو والصوت المشفّرة مقسمة إلى أجزاء (64KB Chunks)
 * مع دعم طلبات النطاق (Byte-Range Requests) للبدء الفوري بالتشغيل في ExoPlayer
 * والتقديم والترجيع الفائق دون انتظار اكتمال التنزيل.
 */
class EncryptedMediaStreamer(
    private val encryptedFile: File,
    private val secretKeyBytes: ByteArray,
    private val ivBytes: ByteArray
) {
    companion object {
        private const val CHUNK_SIZE_BYTES = 64 * 1024 // 64 KB per chunk
        private const val TAG = "EncryptedMediaStreamer"
    }

    val totalEncryptedSizeBytes: Long
        get() = encryptedFile.length()

    /**
     * قراءة وفك تشفير شريحة محددة من الملف للتشغيل الفوري
     */
    fun readDecryptedRange(startByte: Long, length: Int): ByteArray? {
        if (!encryptedFile.exists() || startByte < 0 || length <= 0) return null

        return runCatching {
            RandomAccessFile(encryptedFile, "r").use { raf ->
                raf.seek(startByte)
                val bytesToRead = minOf(length.toLong(), encryptedFile.length() - startByte).toInt()
                if (bytesToRead <= 0) return@use null

                val buffer = ByteArray(bytesToRead)
                raf.readFully(buffer)

                // فك تشفير الجزء المحدد
                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                val keySpec = SecretKeySpec(secretKeyBytes, "AES")

                // حساب IV المخصص للشريحة
                val iv = computeCounterIv(ivBytes, startByte / 16)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))

                val decrypted = cipher.update(buffer) ?: ByteArray(0)
                val finalBytes = cipher.doFinal()
                if (finalBytes.isNotEmpty()) decrypted + finalBytes else decrypted
            }
        }.getOrElse { e ->
            Log.e(TAG, "Error streaming encrypted media chunk at $startByte", e)
            null
        }
    }

    private fun computeCounterIv(baseIv: ByteArray, blockIndex: Long): ByteArray {
        val iv = baseIv.clone()
        var counter = blockIndex
        for (i in 15 downTo 8) {
            counter += (iv[i].toInt() and 0xFF)
            iv[i] = counter.toByte()
            counter = counter ushr 8
        }
        return iv
    }
}
