package com.red.sovereign.media

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.RedConnectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AttachmentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EncryptedAttachmentRepository(application, AuthorizedApiClient(TokenStore(application)))
    private val manifestJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    var sendState: AttachmentState by mutableStateOf(AttachmentState.Idle); private set
    private val downloadStates = androidx.compose.runtime.mutableStateMapOf<String, AttachmentState>()

    fun getDownloadState(messageId: String): AttachmentState = downloadStates[messageId] ?: AttachmentState.Idle

    fun send(uri: Uri, targetRedId: String, conversationId: String) = viewModelScope.launch {
        if (sendState is AttachmentState.Working) return@launch
        sendState = AttachmentState.Working("جارٍ تشفير الملف ورفعه…")
        when (val result = repository.prepare(uri, listOf(targetRedId))) {
            is ApiResult.Error -> sendState = AttachmentState.Error(result.message)
            is ApiResult.Success -> {
                RedConnectionService.sendPayload(
                    getApplication(), targetRedId, conversationId,
                    when {
                        result.value.mimeType.startsWith("image/") -> "IMAGE"
                        result.value.mimeType.startsWith("video/") -> "VIDEO"
                        result.value.mimeType.startsWith("audio/") -> "AUDIO"
                        else -> "FILE"
                    },
                    result.value.manifestJson.toByteArray(Charsets.UTF_8)
                )
                sendState = AttachmentState.Sent(result.value.name)
            }
        }
    }

    /** يرسل مرفقاً داخل مجموعة عبر مسار تشفير المجموعة (Sender Keys).
     *  منح الوصول للملف يتم لكل عضو (وليس لمعرّف المجموعة — الخادم يقبل مستخدمين فقط). */
    fun sendToGroup(uri: Uri, group: com.red.sovereign.groups.Group) = viewModelScope.launch {
        if (sendState is AttachmentState.Working) return@launch
        sendState = AttachmentState.Working("جارٍ تشفير الملف ورفعه…")
        val members = group.members.map { it.redId }
        when (val result = repository.prepare(uri, members)) {
            is ApiResult.Error -> sendState = AttachmentState.Error(result.message)
            is ApiResult.Success -> {
                val type = when {
                    result.value.mimeType.startsWith("image/") -> "IMAGE"
                    result.value.mimeType.startsWith("video/") -> "VIDEO"
                    result.value.mimeType.startsWith("audio/") -> "AUDIO"
                    else -> "FILE"
                }
                RedConnectionService.sendGroupPayload(
                    getApplication(),
                    group,
                    type,
                    result.value.manifestJson.toByteArray(Charsets.UTF_8)
                )
                sendState = AttachmentState.Sent(result.value.name)
            }
        }
    }

    fun download(messageId: String, manifestJson: String) = viewModelScope.launch {
        if (downloadStates[messageId] is AttachmentState.Working) return@launch
        downloadStates[messageId] = AttachmentState.Working("جارٍ تنزيل الملف المشفر والتحقق منه…")
        downloadStates[messageId] = when (val result = repository.downloadAndDecrypt(manifestJson)) {
            is ApiResult.Error -> AttachmentState.Error(result.message)
            is ApiResult.Success -> AttachmentState.Downloaded(result.value.absolutePath, result.value.name)
        }
    }

    /**
     * التسجيلات الصوتية تستخدم VoiceManifest الذي يحتوي بيانات إضافية مثل المدة والموجة.
     * لا يمرر JSON الخام إلى محلل AttachmentManifest، لأن تسجيلات الإصدارات السابقة قد
     * لا تتضمن mimeType الافتراضي بعد التسلسل. نحوله إلى بنية المرفق المتوافقة أولاً.
     */
    fun downloadVoice(messageId: String, voiceManifestJson: String) = viewModelScope.launch {
        if (downloadStates[messageId] is AttachmentState.Working) return@launch
        downloadStates[messageId] = AttachmentState.Working("جارٍ تنزيل الرسالة الصوتية وفك تشفيرها…")
        val voice = runCatching { manifestJson.decodeFromString<VoiceManifest>(voiceManifestJson) }.getOrNull()
        if (voice == null || voice.size <= 0L || voice.objectKey.isBlank() || voice.url.isBlank()) {
            downloadStates[messageId] = AttachmentState.Error("INVALID_VOICE_MANIFEST")
            return@launch
        }
        val attachmentManifest = com.red.sovereign.media.AttachmentManifest(
            objectKey = voice.objectKey,
            url = voice.url,
            name = voice.name,
            mimeType = voice.mimeType,
            size = voice.size,
            sha256 = voice.sha256,
            key = voice.key,
            nonce = voice.nonce
        )
        val attachmentPayload = manifestJson.encodeToString<AttachmentManifest>(attachmentManifest)
        downloadStates[messageId] = when (val result = repository.downloadAndDecrypt(attachmentPayload)) {
            is ApiResult.Error -> AttachmentState.Error(result.message)
            is ApiResult.Success -> AttachmentState.Downloaded(result.value.absolutePath, result.value.name)
        }
    }

    fun exportTo(messageId: String, destination: Uri) = viewModelScope.launch {
        val downloaded = downloadStates[messageId] as? AttachmentState.Downloaded ?: return@launch
        downloadStates[messageId] = AttachmentState.Working("جارٍ حفظ نسخة يختارها المستخدم…")
        downloadStates[messageId] = withContext(Dispatchers.IO) {
            runCatching {
                val source = File(downloaded.path)
                require(source.isFile) { "Decrypted file is unavailable" }
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(destination, "w")?.use { output -> source.inputStream().use { it.copyTo(output, 64 * 1024) } }
                    ?: error("Unable to open export destination")
                AttachmentState.Exported(downloaded.path, downloaded.name)
            }.getOrElse { AttachmentState.Error(it.message ?: "ATTACHMENT_EXPORT_FAILED") }
        }
    }

    fun clear() { sendState = AttachmentState.Idle }
}

sealed interface AttachmentState {
    data object Idle : AttachmentState
    data class Working(val message: String) : AttachmentState
    data class Sent(val name: String) : AttachmentState
    data class Downloaded(val path: String, val name: String) : AttachmentState
    data class Exported(val path: String, val name: String) : AttachmentState
    data class Error(val message: String) : AttachmentState
}
