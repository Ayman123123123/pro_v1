package com.red.sovereign.core.utils

import android.media.MediaRecorder
import android.util.Log
import java.io.File

/**
 * مسجّل صوتي (OGG/Opus) لتسجيل الرسائل الصوتية محليًا قبل التشفير والإرسال.
 * عيّنة 48 كيلوهرتز ومعدل 64 كيلوبت — جودة عالية مع حجم مناسب.
 */
class VoiceRecorder(private val outputDir: File) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording() {
        if (recorder != null) stopRecording()
        currentFile = File(outputDir, "VOICE_${System.currentTimeMillis()}.ogg")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioSamplingRate(48000)
            setAudioEncodingBitRate(64000)
            setOutputFile(currentFile?.absolutePath)
            try {
                prepare()
                start()
            } catch (e: Exception) {
                Log.e(TAG, "فشل بدء التسجيل", e)
                release()
                recorder = null
                currentFile = null
            }
        }
    }

    fun stopRecording(): File? {
        recorder?.let {
            try { it.stop() } catch (e: RuntimeException) {
                Log.w(TAG, "التسجيل لم يكتمل", e)
            } finally {
                it.release()
            }
        }
        recorder = null
        return currentFile
    }

    fun isRecording(): Boolean = recorder != null

    private companion object {
        const val TAG = "VoiceRecorder"
    }
}
