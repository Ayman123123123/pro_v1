package com.red.sovereign.features.profile

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.media.MediaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * إدارة بروفايل المستخدم: الاسم المعروض، الصورة، البايو.
 * الصورة تُرفع مشفّرة عبر MediaApi (objectKey) ثم يُحدّث البروفايل بمرجعها.
 * الخادم لا يخزّن الصورة، فقط objectKey المشفّر.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AuthorizedApiClient(TokenStore(application))
    private val media = MediaApi(application, client)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    var displayName by mutableStateOf("")
        private set
    var username by mutableStateOf("")
        private set
    var redId by mutableStateOf("")
        private set
    var bio by mutableStateOf("")
        private set
    var avatarUrl by mutableStateOf<String?>(null)
        private set
    var avatar by mutableStateOf<ImageBitmap?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var isUploading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** يحمّل بيانات البروفايل الحالية من TokenStore. */
    fun load(currentRedId: String, currentUsername: String, currentDisplayName: String) {
        redId = currentRedId
        username = currentUsername
        displayName = currentDisplayName
        // تحميل الصورة إن وُجد مرجعها
        loadAvatar(avatarUrl)
    }

    /** يحدّث الاسم المعروض والبايو عبر PATCH /api/auth/profile. */
    fun updateProfile(newDisplayName: String, newBio: String, done: () -> Unit) = viewModelScope.launch {
        if (isSaving) return@launch
        isSaving = true
        message = null
        val body = json.encodeToString(UpdateProfileRequest(newDisplayName.trim(), avatarUrl, newBio.trim().takeIf { it.isNotBlank() }))
        when (val result = client.request("PATCH", "/api/auth/profile", body)) {
            is ApiResult.Success -> {
                displayName = newDisplayName.trim()
                bio = newBio.trim()
                message = "تم حفظ البروفايل"
                done()
            }
            is ApiResult.Error -> message = "تعذر حفظ البروفايل: ${result.message}"
        }
        isSaving = false
    }

    /** يرفع صورة مشفّرة ثم يربطها بالبروفايل. */
    fun updateAvatar(uri: Uri) = viewModelScope.launch {
        if (isUploading) return@launch
        isUploading = true
        message = null
        // 1) رفع مشفّر
        when (val uploaded = withContext(Dispatchers.IO) { media.upload(uri) }) {
            is ApiResult.Error -> { message = "تعذر رفع الصورة: ${uploaded.message}"; isUploading = false; return@launch }
            is ApiResult.Success -> {
                val objectKey = uploaded.value.objectKey
                // 2) ربط الصورة بالبروفايل
                val body = json.encodeToString(UpdateProfileRequest(displayName, objectKey, bio.ifBlank { null }))
                when (val result = client.request("PATCH", "/api/auth/profile", body)) {
                    is ApiResult.Success -> {
                        avatarUrl = objectKey
                        message = "تم تحديث الصورة"
                        loadAvatar(objectKey)
                    }
                    is ApiResult.Error -> message = "تعذر ربط الصورة: ${result.message}"
                }
            }
        }
        isUploading = false
    }

    /** يحذف صورة البروفايل. */
    fun removeAvatar() = viewModelScope.launch {
        if (isSaving) return@launch
        isSaving = true
        // لا نمرر avatarUrl في الطلب (null = لا تغيير)؛ نرسل سلسلة فارغة لإزالته
        val body = json.encodeToString(UpdateProfileRequest(displayName, "", bio.ifBlank { null }))
        when (val result = client.request("PATCH", "/api/auth/profile", body)) {
            is ApiResult.Success -> {
                avatarUrl = null
                avatar = null
                message = "تمت إزالة الصورة"
            }
            is ApiResult.Error -> message = "تعذر إزالة الصورة: ${result.message}"
        }
        isSaving = false
    }

    /** يحمّل الصورة المشفّرة من MinIO ويفك تشفيرها للعرض. */
    private fun loadAvatar(url: String?) {
        val key = url ?: return
        if (avatar != null) return
        viewModelScope.launch {
            when (val response = media.download(key, 10 * 1024 * 1024)) {
                is ApiResult.Success -> BitmapFactory.decodeByteArray(response.value, 0, response.value.size)?.let {
                    avatar = it.asImageBitmap()
                }
                is ApiResult.Error -> Unit // تجاهل صامت — الصورة الاختيارية
            }
        }
    }

    fun clearMessage() { message = null }
}

@kotlinx.serialization.Serializable
data class UpdateProfileRequest(
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null
)
