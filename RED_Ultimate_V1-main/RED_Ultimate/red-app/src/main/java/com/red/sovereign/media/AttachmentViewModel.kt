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
import java.io.File

class AttachmentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EncryptedAttachmentRepository(application, AuthorizedApiClient(TokenStore(application)))
    var state: AttachmentState by mutableStateOf(AttachmentState.Idle); private set

    fun send(uri: Uri, targetRedId: String, conversationId: String) = viewModelScope.launch {
        if (state is AttachmentState.Working) return@launch
        state = AttachmentState.Working("جارٍ تشفير الملف ورفعه…")
        when (val result = repository.prepare(uri, targetRedId)) {
            is ApiResult.Error -> state = AttachmentState.Error(result.message)
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
                state = AttachmentState.Sent(result.value.name)
            }
        }
    }

    /** يرسل مرفقاً داخل مجموعة عبر مسار تشفير المجموعة (Sender Keys). */
    fun sendToGroup(uri: Uri, group: com.red.sovereign.groups.Group) = viewModelScope.launch {
        if (state is AttachmentState.Working) return@launch
        state = AttachmentState.Working("جارٍ تشفير الملف ورفعه…")
        when (val result = repository.prepare(uri, group.id)) {
            is ApiResult.Error -> state = AttachmentState.Error(result.message)
            is ApiResult.Success -> {
                val manifestJson = result.value.manifestJson
                val type = when {
                    result.value.mimeType.startsWith("image/") -> "IMAGE"
                    result.value.mimeType.startsWith("video/") -> "VIDEO"
                    result.value.mimeType.startsWith("audio/") -> "AUDIO"
                    else -> "FILE"
                }
                RedConnectionService.sendGroupPayload(getApplication(), group, type, manifestJson)
                state = AttachmentState.Sent(result.value.name)
            }
        }
    }

    fun download(manifestJson: String) = viewModelScope.launch {
        if (state is AttachmentState.Working) return@launch
        state = AttachmentState.Working("جارٍ تنزيل الملف المشفر والتحقق منه…")
        state = when (val result = repository.downloadAndDecrypt(manifestJson)) {
            is ApiResult.Error -> AttachmentState.Error(result.message)
            is ApiResult.Success -> {
                val file = result.value
                val manifest = runCatching {
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        .decodeFromString(AttachmentManifest.serializer(), manifestJson)
                }.getOrNull()
                GallerySaver.saveIfAllowed(
                    getApplication(),
                    file,
                    manifest?.mimeType.orEmpty(),
                    manifest?.name ?: file.name,
                )
                AttachmentState.Downloaded(file.absolutePath, manifest?.name ?: file.name)
            }
        }
    }

    fun exportTo(destination: Uri) = viewModelScope.launch {
        val downloaded = state as? AttachmentState.Downloaded ?: return@launch
        state = AttachmentState.Working("جارٍ حفظ نسخة يختارها المستخدم…")
        state = withContext(Dispatchers.IO) {
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

    fun clear() { state = AttachmentState.Idle }
}

sealed interface AttachmentState {
    data object Idle : AttachmentState
    data class Working(val message: String) : AttachmentState
    data class Sent(val name: String) : AttachmentState
    data class Downloaded(val path: String, val name: String) : AttachmentState
    data class Exported(val path: String, val name: String) : AttachmentState
    data class Error(val message: String) : AttachmentState
}
