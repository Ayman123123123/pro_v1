package com.red.sovereign.util

import com.red.sovereign.BuildConfig
import androidx.tracing.Trace as AndroidTrace

object REDTrace {
  @JvmStatic
  fun beginSection(methodName: String) {
    if (!BuildConfig.TRACING_ENABLED) {
      return
    }
    AndroidTrace.beginSection(methodName)
  }

  @JvmStatic
  fun endSection() {
    if (!BuildConfig.TRACING_ENABLED) {
      return
    }
    AndroidTrace.endSection()
  }
}
