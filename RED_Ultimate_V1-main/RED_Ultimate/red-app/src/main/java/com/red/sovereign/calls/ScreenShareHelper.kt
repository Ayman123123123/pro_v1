package com.red.sovereign.calls

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Screen Share Helper — RED Sovereign 2026
 *
 * • Uses ScreenCapturerAndroid with MediaProjection (Android 14+ MediaProjectionConfig support)
 * • Hardware scaler + adaptive resolution (1080p / 720p)
 * • Compatible with group calls, conferences, and live streaming.
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 (API 34)
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
    }

    fun start(data: Intent, width: Int = 1280, height: Int = 720, fps: Int = 24): VideoTrack? {
        if (isSharing) return videoTrack
        return try {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = manager.getMediaProjection(Activity.RESULT_OK, data)
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null")
                return null
            }
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped by system")
                    stop()
                }
            }
            mediaProjection?.registerCallback(callback, null)
            capturer = ScreenCapturerAndroid(data, callback)
            videoSource = factory.createVideoSource(true)
            textureHelper = SurfaceTextureHelper.create("ScreenShareThread", eglContext)

            capturer?.initialize(textureHelper, context, videoSource?.capturerObserver)
            capturer?.startCapture(width, height, fps)

            videoTrack = factory.createVideoTrack("red-screen-share-track", videoSource).apply {
                setEnabled(true)
            }
            Log.d(TAG, "Screen share started successfully: ${width}x${height}@${fps}fps")
            videoTrack
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen share: ${e.message}", e)
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
        Log.d(TAG, "Screen share stopped and resources released")
    }

    companion object {
        private const val TAG = "ScreenShareHelper"
    }
}
