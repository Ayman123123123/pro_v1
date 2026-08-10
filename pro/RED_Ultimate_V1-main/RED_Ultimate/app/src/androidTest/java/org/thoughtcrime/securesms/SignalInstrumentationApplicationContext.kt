package com.red.sovereign

import android.content.Context
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.logging.REDProtocolLoggerProvider
import com.red.sovereign.database.LogDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.dependencies.ApplicationDependencyProvider
import com.red.sovereign.dependencies.InstrumentationApplicationDependencyProvider
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.logging.CustomREDProtocolLogger
import com.red.sovereign.logging.PersistentLogger
import com.red.sovereign.testing.InMemoryLogger
import com.red.sovereign.testing.TestRemoteConfig
import com.red.sovereign.util.Environment

/**
 * Application context for running instrumentation tests (aka androidTests).
 */
class REDInstrumentationApplicationContext : ApplicationContext() {

  val inMemoryLogger: InMemoryLogger = InMemoryLogger()

  override fun attachBaseContext(base: Context?) {
    Environment.IS_INSTRUMENTATION = true
    super.attachBaseContext(base)
  }

  override fun initializeAppDependencies() {
    val default = ApplicationDependencyProvider(this)
    AppDependencies.init(this, InstrumentationApplicationDependencyProvider(this, default))
    AppDependencies.deadlockDetector.start()

    // Stage any test-declared remote config into the store to be read in RemoteConfig.init().
    if (TestRemoteConfig.pending.isNotEmpty()) {
      val json = TestRemoteConfig.json
      REDStore.remoteConfig.currentConfig = json
      REDStore.remoteConfig.pendingConfig = json
    }
  }

  override fun initializeLogging() {
    Log.initialize({ true }, AndroidLogger, PersistentLogger.getInstance(this), inMemoryLogger)

    REDProtocolLoggerProvider.setProvider(CustomREDProtocolLogger())

    REDExecutors.UNBOUNDED.execute {
      Log.blockUntilAllWritesFinished()
      LogDatabase.getInstance(this).logs.trimToSize()
    }
  }

  override fun beginJobLoop() = Unit

  /**
   * Some of the jobs can interfere with some of the instrumentation tests.
   *
   * For example, we may try to create a release channel recipient while doing
   * an import/backup test.
   *
   * This can be used to start the job loop if needed for tests that rely on it.
   */
  fun beginJobLoopForTests() {
    super.beginJobLoop()
  }
}
