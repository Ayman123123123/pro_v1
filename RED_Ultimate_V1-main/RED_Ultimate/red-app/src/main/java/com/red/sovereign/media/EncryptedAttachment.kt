package com.red.sovereign.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class AttachmentManifest(
    val version: Int = 1,
    val objectKey: String,
    val url: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val sha256: String,
    val key: String,
    val nonce: String
)

data class PreparedAttachment(val manifestJson: String, val name: String, val mimeType: String, val size: Long)

class EncryptedAttachmentRepository(
    private val context: Context,
    private val client: AuthorizedApiClient
) {
    private val media = MediaApi(context, client)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val random = SecureRandom()

    suspend fun prepare(uri: Uri, targetRedIds: List<String>): ApiResult<PreparedAttachment> = withContext(Dispatchers.IO) {
        val metadata = metadata(uri) ?: return@withContext ApiResult.Error(null, "INVALID_ATTACHMENT")
        if (metadata.size !in 1..MAX_BYTES) return@withContext ApiResult.Error(413, "ATTACHMENT_TOO_LARGE")
        if (!allowedMime(metadata.mimeType)) return@withContext ApiResult.Error(415, "ATTACHMENT_TYPE_NOT_ALLOWED")
        val key = ByteArray(32).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val encrypted = File.createTempFile("attachment-", ".bin", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            }
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext ApiResult.Error(null, "ATTACHMENT_OPEN_FAILED")
            input.use { source -> CipherOutputStream(FileOutputStream(encrypted), cipher).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            }
            val shaHex = digest.digest().toHex()
            val safeName = metadata.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "attachment" }
            val outputDir = File(context.cacheDir, "decrypted_attachments").apply { mkdirs() }
            val localCache = File(outputDir, "${shaHex.take(16)}-$safeName")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(localCache).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
            }
            when (val uploaded = media.uploadEncrypted(encrypted, metadata.name.substringBeforeLast('.'))) {
                is ApiResult.Error -> uploaded
                is ApiResult.Success -> {
                    // منح الوصول لكل مستلم (فرد واحد أو أعضاء المجموعة). فشل منح عضو واحد
                    // (حساب غير معتمد مثلاً) لا يمنع الإرسال — يُتخطى، والعضو يرى الملف غير متاح.
                    var anyGranted = false
                    for (grantee in targetRedIds) {
                        if (grantee.isBlank()) continue
                        when (media.grant(uploaded.value.objectKey, grantee)) {
                            is ApiResult.Success -> anyGranted = true
                            is ApiResult.Error -> Unit
                        }
                    }
                    if (!anyGranted) {
                        media.delete(uploaded.value.url)
                        return@withContext ApiResult.Error(null, "ATTACHMENT_GRANT_FAILED")
                    }
                    val manifest = AttachmentManifest(
                        objectKey = uploaded.value.objectKey,
                        url = uploaded.value.url,
                        name = metadata.name,
                        mimeType = metadata.mimeType,
                        size = metadata.size,
                        sha256 = shaHex,
                        key = Base64.getEncoder().encodeToString(key),
                        nonce = Base64.getEncoder().encodeToString(nonce)
                    )
                    ApiResult.Success(uploaded.code, PreparedAttachment(json.encodeToString(manifest), metadata.name, metadata.mimeType, metadata.size))
                }
            }
        } catch (error: Exception) {
            ApiResult.Error(null, error.message ?: "ATTACHMENT_ENCRYPTION_FAILED")
        } finally {
            encrypted.delete()
            key.fill(0)
            nonce.fill(0)
        }
    }

    suspend fun downloadAndDecrypt(manifestJson: String): ApiResult<File> = withContext(Dispatchers.IO) {
        val manifest = runCatching { json.decodeFromString<AttachmentManifest>(manifestJson) }.getOrNull()
            ?: return@withContext ApiResult.Error(null, "INVALID_ATTACHMENT_MANIFEST")
        if (manifest.version != 1 || manifest.size !in 1..MAX_BYTES || !allowedMime(manifest.mimeType))
            return@withContext ApiResult.Error(null, "UNSUPPORTED_ATTACHMENT_MANIFEST")
        val safeName = manifest.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "attachment" }
        val outputDir = File(context.cacheDir, "decrypted_attachments").apply { mkdirs() }
        val output = File(outputDir, "${manifest.sha256.take(16)}-$safeName")
        if (output.exists() && output.length() == manifest.size) {
            return@withContext ApiResult.Success(200, output)
        }
        val key = runCatching { Base64.getDecoder().decode(manifest.key) }.getOrNull()
            ?.takeIf { it.size == 32 } ?: return@withContext ApiResult.Error(null, "INVALID_ATTACHMENT_KEY")
        val nonce = runCatching { Base64.getDecoder().decode(manifest.nonce) }.getOrNull()
            ?.takeIf { it.size == 12 } ?: return@withContext ApiResult.Error(null, "INVALID_ATTACHMENT_NONCE")
        when (val downloaded = media.downloadToPrivateCache(manifest.url, "bin")) {
            is ApiResult.Error -> downloaded
            is ApiResult.Success -> try {
                val digest = MessageDigest.getInstance("SHA-256")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                }
                CipherInputStream(FileInputStream(downloaded.value), cipher).use { input -> FileOutputStream(output).use { destination ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) error("Decrypted attachment exceeds limit")
                        digest.update(buffer, 0, count)
                        destination.write(buffer, 0, count)
                    }
                    destination.fd.sync()
                } }
                if (output.length() != manifest.size || digest.digest().toHex() != manifest.sha256.lowercase()) {
                    output.delete(); ApiResult.Error(null, "ATTACHMENT_INTEGRITY_FAILED")
                } else ApiResult.Success(downloaded.code, output)
            } catch (error: Exception) {
                output.delete(); ApiResult.Error(null, error.message ?: "ATTACHMENT_DECRYPTION_FAILED")
            } finally {
                key.fill(0); nonce.fill(0)
            }
        }
    }

    private fun metadata(uri: Uri): LocalAttachment? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase()?.substringBefore(';') ?: return null
        var name = "attachment"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0) ?: name
                size = cursor.getLong(1)
            }
        }
        return LocalAttachment(name.take(150), mime, size)
    }

    private fun allowedMime(value: String) = value.startsWith("image/") || value.startsWith("video/") ||
        value.startsWith("audio/") || value == "application/pdf" || value in DOCUMENT_MIMES

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private data class LocalAttachment(val name: String, val mimeType: String, val size: Long)

    companion object {
        const val MAX_BYTES = 99L * 1024 * 1024 // leaves room for the AES-GCM authentication tag under the 100 MiB server cap
        private val DOCUMENT_MIMES = setOf(
            "text/plain", "text/csv", "application/rtf", "application/json", "application/zip",
            "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
    }
}
