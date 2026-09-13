package com.red.core.utils

import android.util.Log

object DevelopedLogger {
    private const val TAG = "RED_System"

    fun d(message: String) = Log.d(TAG, "DEBUG: $message")
    fun e(message: String, throwable: Throwable? = null) = Log.e(TAG, "CRITICAL ERROR: $message", throwable)
    fun i(message: String) = Log.i(TAG, "INFO: $message")
}
