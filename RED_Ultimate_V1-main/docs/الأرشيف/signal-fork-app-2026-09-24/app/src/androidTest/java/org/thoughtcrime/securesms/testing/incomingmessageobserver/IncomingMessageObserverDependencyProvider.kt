package com.red.sovereign.testing.incomingmessageobserver

import android.app.Application
import org.signal.benchmark.setup.NoOpJob
import org.signal.core.util.UptimeSleepTimer
import org.signal.libsignal.net.Network
import org.signal.network.config.REDServiceConfiguration
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.dependencies.ApplicationDependencyProvider
import com.red.sovereign.dependencies.InstrumentationApplicationDependencyProvider
import com.red.sovereign.jobmanager.JobManager
import com.red.sovereign.jobs.JobManagerFactories
import org.whispersystems.signalservice.api.websocket.REDWebSocket
import org.whispersystems.signalservice.internal.websocket.BenchmarkWebSocketConnection
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

/**
 * Dependency provider used by [com.red.sovereign.IncomingMessageObserverInstrumentationApplicationContext].
 * Composes [InstrumentationApplicationDependencyProvider] (so existing mocks for the account /
 * archive / donations / billing APIs are reused) and overrides:
 *
 * - the auth and unauth websocket factories with [BenchmarkWebSocketConnection], so tests can
 *   inject encrypted envelopes through the real ingest pipeline;
 * - the job manager, swapping the startup network jobs handled by [NoOpJob.replaceFactories]
 *   to no-ops so they can't fire against unstubbed mocks during a test.
 */
class IncomingMessageObserverDependencyProvider(
  private val application: Application,
  default: ApplicationDependencyProvider
) : AppDependencies.Provider by InstrumentationApplicationDependencyProvider(application, default) {

  override fun provideAuthWebSocket(
    signalServiceConfigurationSupplier: Supplier<REDServiceConfiguration>,
    libREDNetworkSupplier: Supplier<Network>
  ): REDWebSocket.AuthenticatedWebSocket {
    return REDWebSocket.AuthenticatedWebSocket(
      connectionFactory = { BenchmarkWebSocketConnection.createAuthInstance() },
      canConnect = { true },
      sleepTimer = UptimeSleepTimer(),
      disconnectTimeoutMs = 15.seconds.inWholeMilliseconds
    )
  }

  override fun provideUnauthWebSocket(
    signalServiceConfigurationSupplier: Supplier<REDServiceConfiguration>,
    libREDNetworkSupplier: Supplier<Network>
  ): REDWebSocket.UnauthenticatedWebSocket {
    return REDWebSocket.UnauthenticatedWebSocket(
      connectionFactory = { BenchmarkWebSocketConnection.createUnauthInstance() },
      canConnect = { true },
      sleepTimer = UptimeSleepTimer(),
      disconnectTimeoutMs = 15.seconds.inWholeMilliseconds
    )
  }

  override fun provideJobManager(configurationBuilder: JobManager.Configuration.Builder): JobManager {
    val config = configurationBuilder
      .setJobFactories(NoOpJob.replaceFactories(JobManagerFactories.getJobFactories(application)))
      .build()
    return JobManager(application, config)
  }
}
