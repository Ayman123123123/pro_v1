package com.red.sovereign.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.json.Json
import java.io.File

object FileTypeUtil {

    private val json = Json { ignoreUnknownKeys = true }

    fun getFileIcon(mimeType: String, fileName: String? = null): ImageVector {
        val mime = mimeType.lowercase()
        return when {
            mime.startsWith("image/") -> Icons.Default.Photo
            mime.startsWith("video/") -> Icons.Default.Videocam
            mime.startsWith("audio/") -> Icons.Default.MusicNote
            mime == "application/pdf" -> Icons.Default.PictureAsPdf
            mime in DOCUMENT_MIMES -> Icons.Default.Description
            mime == "application/zip" || mime == "application/x-rar-compressed" || mime == "application/x-7z-compressed" || mime == "application/gzip" || mime == "application/x-tar" -> Icons.Default.Archive
            mime.startsWith("text/") -> Icons.Default.Article
            mime == "application/json" -> Icons.Default.Code
            mime == "application/vnd.ms-excel" || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" || mime == "application/vnd.oasis.opendocument.spreadsheet" -> Icons.Default.TableChart
            mime == "application/vnd.ms-powerpoint" || mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" || mime == "application/vnd.oasis.opendocument.presentation" -> Icons.Default.Slideshow
            mime == "application/vnd.ms-word" || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || mime == "application/vnd.oasis.opendocument.text" || mime == "application/rtf" -> Icons.Default.Description
            mime == "application/x-apk" -> Icons.Default.Android
            else -> getIconFromExtension(fileName)
        }
    }

    private fun getIconFromExtension(fileName: String?): ImageVector {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
        return when (ext) {
            "pdf" -> Icons.Default.PictureAsPdf
            "doc", "docx", "rtf", "odt" -> Icons.Default.Description
            "xls", "xlsx", "ods", "csv" -> Icons.Default.TableChart
            "ppt", "pptx", "odp" -> Icons.Default.Slideshow
            "txt", "md", "log", "json", "xml", "yaml", "yml" -> Icons.Default.Article
            "zip", "rar", "7z", "tar", "gz", "bz2" -> Icons.Default.Archive
            "apk" -> Icons.Default.Android
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> Icons.Default.Photo
            "mp4", "mov", "avi", "mkv", "webm", "3gp", "flv" -> Icons.Default.Videocam
            "mp3", "wav", "ogg", "flac", "aac", "m4a", "opus" -> Icons.Default.MusicNote
            "exe", "msi", "deb", "rpm" -> Icons.Default.Storage
            else -> Icons.Default.InsertDriveFile
        }
    }

    fun getFileColor(mimeType: String): Color {
        val mime = mimeType.lowercase()
        return when {
            mime.startsWith("image/") -> Color(0xFF00C853)
            mime.startsWith("video/") -> Color(0xFF2962FF)
            mime.startsWith("audio/") -> Color(0xFF00BFA5)
            mime == "application/pdf" -> Color(0xFFD32F2F)
            mime in DOCUMENT_MIMES -> Color(0xFF6A1B9A)
            mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("tar") || mime.contains("gzip") -> Color(0xFFEF6C00)
            mime.startsWith("text/") || mime == "application/json" -> Color(0xFF37474F)
            else -> Color(0xFF546E7A)
        }
    }

    fun getFileCategory(mimeType: String): FileCategory {
        val mime = mimeType.lowercase()
        return when {
            mime.startsWith("image/") -> FileCategory.IMAGE
            mime.startsWith("video/") -> FileCategory.VIDEO
            mime.startsWith("audio/") -> FileCategory.AUDIO
            mime == "application/pdf" -> FileCategory.DOCUMENT
            mime in DOCUMENT_MIMES -> FileCategory.DOCUMENT
            mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("tar") || mime.contains("gzip") -> FileCategory.ARCHIVE
            mime.startsWith("text/") || mime == "application/json" -> FileCategory.DOCUMENT
            else -> FileCategory.OTHER
        }
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
        else "%d:%02d".format(minutes, seconds % 60)
    }

    fun getMimeFromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "rtf" -> "application/rtf"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "csv" -> "text/csv"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "yaml", "yml" -> "application/yaml"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz", "gzip" -> "application/gzip"
            "bz2" -> "application/x-bzip2"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "flv" -> "video/x-flv"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "opus" -> "audio/opus"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    fun isMediaFile(mimeType: String): Boolean = mimeType.lowercase().let {
        it.startsWith("image/") || it.startsWith("video/") || it.startsWith("audio/")
    }

    fun isDocumentFile(mimeType: String): Boolean = mimeType.lowercase().let {
        it == "application/pdf" || it in DOCUMENT_MIMES || it.startsWith("text/") || it == "application/json"
    }

    fun isArchiveFile(mimeType: String): Boolean = mimeType.lowercase().let {
        it.contains("zip") || it.contains("rar") || it.contains("7z") || it.contains("tar") || it.contains("gzip") || it.contains("bzip")
    }

    fun canPreviewInApp(mimeType: String): Boolean = mimeType.lowercase().let {
        it.startsWith("image/") || it.startsWith("video/") || it.startsWith("audio/") || it == "application/pdf" || it.startsWith("text/")
    }

    private val DOCUMENT_MIMES = setOf(
        "text/plain", "text/csv", "application/rtf", "application/json", "application/zip",
        "application/vnd.oasis.opendocument.text", "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.ms-word", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
        "application/vnd.oasis.opendocument.presentation"
    )

    enum class FileCategory {
        IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, OTHER
    }
}

data class FileMetadata(
    val name: String,
    val mimeType: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val pageCount: Int? = null,
    val sha256: String? = null,
    val lastModified: Long = System.currentTimeMillis()
) {
    val category: FileTypeUtil.FileCategory = FileTypeUtil.getFileCategory(mimeType)
    val icon: androidx.compose.ui.graphics.vector.ImageVector = FileTypeUtil.getFileIcon(mimeType, name)
    val color: androidx.compose.ui.graphics.Color = FileTypeUtil.getFileColor(mimeType)
    val formattedSize: String = FileTypeUtil.formatFileSize(size)
    val formattedDuration: String? = durationMs?.let { FileTypeUtil.formatDuration(it) }
}