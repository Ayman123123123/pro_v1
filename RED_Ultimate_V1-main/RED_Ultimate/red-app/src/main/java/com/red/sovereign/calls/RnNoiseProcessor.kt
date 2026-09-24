package com.red.sovereign.calls

import android.util.Log

/**
 * RNNoise integration for real-time noise suppression.
 * Uses the native RNNoise library (librnnoise) via JNI.
 * Falls back to WebRTC NS if RNNoise is unavailable.
 */
class RnNoiseProcessor {
    companion object {
        private const val TAG = "RnNoiseProcessor"
        private var nativeLoaded = false

        init {
            try {
                System.loadLibrary("rnnoise")
                nativeLoaded = true
                Log.i(TAG, "RNNoise native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "RNNoise native library not available, falling back to WebRTC NS: ${e.message}")
                nativeLoaded = false
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load RNNoise: ${e.message}")
                nativeLoaded = false
            }
        }
    }

    private var statePtr: Long = 0
    private val frameSize = 480 // 10ms at 48kHz mono

    /**
     * Creates a new RNNoise denoiser state.
     * Must be called before process().
     */
    fun create(): Boolean {
        if (!nativeLoaded) return false
        statePtr = nativeCreate()
        return statePtr != 0L
    }

    /**
     * Processes a single frame of audio (480 samples = 10ms at 48kHz).
     * Input and output must be FloatArray of size frameSize.
     * Returns the voice activity probability (0.0 to 1.0).
     */
    fun process(input: FloatArray, output: FloatArray): Float {
        if (!nativeLoaded || statePtr == 0L) {
            // Fallback: just copy input to output
            output.fill(0f)
            input.copyInto(output, endIndex = minOf(input.size, output.size))
            return 1.0f
        }
        if (input.size != frameSize || output.size != frameSize) {
            Log.e(TAG, "Invalid frame size: input=${input.size}, output=${output.size}, expected=$frameSize")
            output.fill(0f)
            input.copyInto(output, endIndex = minOf(input.size, output.size))
            return 1.0f
        }
        return nativeProcess(statePtr, input, output)
    }

    /**
     * Destroys the RNNoise state.
     */
    fun destroy() {
        if (statePtr != 0L) {
            nativeDestroy(statePtr)
            statePtr = 0
        }
    }

    /** Checks if RNNoise is available (native library loaded). */
    fun isAvailable(): Boolean = nativeLoaded && statePtr != 0L

    // Native method declarations
    external fun nativeCreate(): Long
    external fun nativeProcess(state: Long, input: FloatArray, output: FloatArray): Float
    external fun nativeDestroy(state: Long)
}