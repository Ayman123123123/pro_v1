package com.red.features.chat

import android.media.MediaRecorder
import java.io.File

class VoiceRecorder(private val outputDir: File) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording() {
        currentFile = File(outputDir, "VOICE_${System.currentTimeMillis()}.ogg")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioSamplingRate(48000)
            setAudioEncodingBitRate(64000)
            setOutputFile(currentFile?.absolutePath)
            prepare()
            start()
        }
    }

    fun stopRecording(): File? {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        return currentFile
    }
}
