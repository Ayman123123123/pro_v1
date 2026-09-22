package com.red.sovereign

import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.logging.REDProtocolLoggerProvider
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.dependencies.ApplicationDependencyProvider
import com.red.sovereign.logging.CustomREDProtocolLogger
import com.red.sovereign.testing.incomingmessageobserver.IncomingMessageObserverDependencyProvider
import com.red.sovereign.testing.incomingmessageobserver.IncomingMessageObserverTestRunner

/**
 * Application used when running `IncomingMessageObserver` instrumentation tests. Installs
 * [IncomingMessageObserverDependencyProvider] so the websocket and job manager are replaced
 * with test-friendly implementations. Selected by [IncomingMessageObserverTestRunner] when
 * gradle is invoked with `-PimoTests`.
 */
class IncomingMessageObserverInstrumentationApplicationContext : ApplicationContext() {

  override fun initializeAppDependencies() {
    val default = ApplicationDependencyProvider(this)
    AppDependencies.init(this, IncomingMessageObserverDependencyProvider(this, default))
    AppDependencies.deadlockDetector.start()
  }

  override fun initializeLogging() {
    Log.initialize({ true }, AndroidLogger)
    REDProtocolLoggerProvider.setProvider(CustomREDProtocolLogger())
  }

  override fun beginJobLoop() = Unit

  fun beginJobLoopForTests() {
    super.beginJobLoop()
  }
}
