package com.red.sovereign.video.videoconverter.utils

object Preconditions {
  @JvmStatic
  fun checkState(errorMessage: String, expression: Boolean) {
    check(expression) { errorMessage }
  }
}
