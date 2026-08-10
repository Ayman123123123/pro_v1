package com.red.sovereign.core.utils

import android.net.Uri
import com.red.sovereign.core.network.MinioUploader
import java.io.File
import javax.inject.Inject

class RedMediaTransporter @Inject constructor(private val uploader: MinioUploader) {

    /**
     * نقل أي ملف (صورة، فيديو، مستند، تطبيق) إلى MinIO المحلي
     */
    suspend fun transportFile(uri: Uri, type: String): String? {
        val file = File(uri.path!!)
        return uploader.uploadFileSync(file, "vault/$type/${System.currentTimeMillis()}")
    }

    /**
     * معيار RED للبصمة الصوتية (High Fidelity Voice)
     */
    fun getVoiceStandard() = "Opus-48kHz-64kbps"
}
