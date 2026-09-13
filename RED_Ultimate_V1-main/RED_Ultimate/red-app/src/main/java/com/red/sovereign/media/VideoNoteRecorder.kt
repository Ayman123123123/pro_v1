package com.red.sovereign.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * 📹 VideoNoteRecorder — مسجل الرسائل الملاحظات المرئية الدائرية (Circular Video Notes)
 *
 * يوفر إمكانية تسجيل ملاحظة فيديو دائرية فورية بحد أقصى 60 ثانية
 * وتشفيرها بتنسيق MP4 (AAC + H264) للعرض داخل المجموعات والدردشة المباشرة.
 */
class VideoNoteRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecord(): File? {
        runCatching {
            outputFile = File(context.cacheDir, "vid_note_${System.currentTimeMillis()}.mp4")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(480, 480) // 1:1 Square/Circle aspect ratio
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(1_500_000) // 1.5 Mbps
            val outFile = outputFile ?: run {
                Log.e("VideoNoteRecorder", "STORAGE_UNAVAILABLE: output file is null")
                return null
            }
            recorder.setOutputFile(outFile.absolutePath)
            recorder.setMaxDuration(60_000) // 60 seconds limit

            recorder.prepare()
            recorder.start()
            startTimeMs = System.currentTimeMillis()
            mediaRecorder = recorder
            return outputFile
        }.onFailure { e ->
            Log.e("VideoNoteRecorder", "Failed to start video note recording", e)
            release()
        }
        return null
    }

    fun stopRecord(): Pair<File?, Long> {
        var duration = 0L
        runCatching {
            mediaRecorder?.stop()
            duration = System.currentTimeMillis() - startTimeMs
        }.onFailure { e ->
            Log.e("VideoNoteRecorder", "Error stopping recorder", e)
            outputFile?.delete()
            outputFile = null
        }
        release()
        return Pair(outputFile, duration)
    }

    fun cancelRecord() {
        runCatching {
            mediaRecorder?.stop()
        }
        release()
        outputFile?.delete()
        outputFile = null
    }

    private fun release() {
        runCatching {
            mediaRecorder?.release()
        }
        mediaRecorder = null
    }
}
