package com.red.sovereign.calls

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/**
 * مساعد مشاركة الشاشة — يعتمد أحدث تقنيات 2026:
 * • ScreenCapturerAndroid مع MediaProjection (Android 14+ MediaProjectionConfig)
 * • hardware scaler + 1080p adaptive
 * • يعمل مع كل الأنواع: جماعية / مؤتمر / بث
 */
class ScreenShareHelper(
    private val context: Context,
    private val eglContext: EglBase.Context,
    private val factory: PeerConnectionFactory
) {
    private var mediaProjection: MediaProjection? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var videoTrack: VideoTrack? = null

    val track: VideoTrack? get() = videoTrack
    val isSharing: Boolean get() = capturer != null

    fun createScreenCaptureIntent(): Intent {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return if (android.os.Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
    }

    fun start(data: Intent, width: Int = 1280, height: Int = 720, fps: Int = 24): VideoTrack? {
        if (isSharing) return videoTrack
        return try {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val resultCode = Activity.RESULT_OK
            mediaProjection = manager.getMediaProjection(resultCode, data)
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection null")
                return null
            }
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    stop()
                }
            }
            mediaProjection?.registerCallback(callback, null)
            capturer = ScreenCapturerAndroid(data, callback)
            videoSource = factory.createVideoSource(true)
            textureHelper = SurfaceTextureHelper.create("ScreenShare", eglContext)
            capturer?.initialize(textureHelper, context, videoSource?.capturerObserver)
            capturer?.startCapture(width, height, fps)
            videoTrack = factory.createVideoTrack("screen-share", videoSource).apply { setEnabled(true) }
            Log.d(TAG, "Screen share started ${width}x${height}@$fps track=${videoTrack?.id()}")
            videoTrack
        } catch (e: Exception) {
            Log.e(TAG, "Screen share start failed: ${e.message}", e)
            stop()
            null
        }
    }

    fun stop() {
        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        try { textureHelper?.dispose() } catch (_: Exception) {}
        try { videoSource?.dispose() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        capturer = null
        textureHelper = null
        videoSource = null
        videoTrack = null
        mediaProjection = null
        Log.d(TAG, "Screen share stopped")
    }

    companion object {
        private const val TAG = "ScreenShareHelper"
    }
}
