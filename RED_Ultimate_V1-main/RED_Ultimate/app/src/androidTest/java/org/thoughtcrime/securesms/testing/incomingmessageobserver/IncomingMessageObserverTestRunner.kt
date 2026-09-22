package com.red.sovereign.testing.incomingmessageobserver

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.red.sovereign.IncomingMessageObserverInstrumentationApplicationContext

/**
 * Test runner that swaps in [IncomingMessageObserverInstrumentationApplicationContext] so the
 * `IncomingMessageObserver` test harness can drive a faked websocket. Selected automatically by
 * the build when `-PimoTests` is set.
 */
@Suppress("unused")
class IncomingMessageObserverTestRunner : AndroidJUnitRunner() {
  override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
    return super.newApplication(cl, IncomingMessageObserverInstrumentationApplicationContext::class.java.name, context)
  }
}
