/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign

import android.app.Application
import org.signal.benchmark.setup.NoOpJob
import org.signal.core.util.UptimeSleepTimer
import org.signal.libsignal.net.Network
import org.signal.network.config.REDServiceConfiguration
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.dependencies.ApplicationDependencyProvider
import com.red.sovereign.jobmanager.JobManager
import com.red.sovereign.jobs.JobManagerFactories
import com.red.sovereign.net.DeviceTransferBlockingInterceptor
import org.whispersystems.signalservice.api.websocket.REDWebSocket
import org.whispersystems.signalservice.internal.websocket.BenchmarkWebSocketConnection
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

class BenchmarkApplicationContext : ApplicationContext() {

  override fun initializeAppDependencies() {
    AppDependencies.init(this, BenchmarkDependencyProvider(this, ApplicationDependencyProvider(this)))

    DeviceTransferBlockingInterceptor.getInstance().blockNetwork()
  }

  override fun onForeground() = Unit

  class BenchmarkDependencyProvider(val application: Application, private val default: ApplicationDependencyProvider) : AppDependencies.Provider by default {
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
}
