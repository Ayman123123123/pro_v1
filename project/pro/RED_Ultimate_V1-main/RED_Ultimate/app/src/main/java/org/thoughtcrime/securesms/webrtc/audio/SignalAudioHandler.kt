package com.red.sovereign.webrtc.audio

import android.os.Handler
import android.os.Looper

/**
 * Handler to run all audio/bluetooth operations. Provides current thread
 * assertion for enforcing use of the handler when necessary.
 */
class REDAudioHandler(looper: Looper) : Handler(looper) {

  fun assertHandlerThread() {
    if (!isOnHandler()) {
      throw AssertionError("Must run on audio handler thread.")
    }
  }

  fun isOnHandler(): Boolean {
    return Looper.myLooper() == looper
  }
}
