package com.red.sovereign.dependencies

import android.app.Application
import io.mockk.mockk
import io.mockk.spyk
import okhttp3.OkHttpClient
import org.signal.core.util.UptimeSleepTimer
import org.signal.core.util.billing.BillingApi
import org.signal.libsignal.net.Network
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations
import org.signal.network.api.ArchiveApi
import org.signal.network.config.REDServiceConfiguration
import com.red.sovereign.push.REDServiceNetworkAccess
import com.red.sovereign.recipients.LiveRecipientCache
import com.red.sovereign.testing.endpoints.DonationTestServer
import com.red.sovereign.testing.endpoints.MockEndpoints
import com.red.sovereign.testing.endpoints.ResponderInterceptor
import com.red.sovereign.testing.endpoints.ResponderWebSocketConnection
import org.whispersystems.signalservice.api.REDServiceDataStore
import org.whispersystems.signalservice.api.REDServiceMessageSender
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.message.MessageApi
import org.whispersystems.signalservice.api.websocket.REDWebSocket
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

/**
 * Dependency provider used for instrumentation tests (aka androidTests).
 *
 * Handles setting up a mock web server for API calls, and provides mockable versions of [REDServiceNetworkAccess].
 */
class InstrumentationApplicationDependencyProvider(val application: Application, private val default: ApplicationDependencyProvider) : AppDependencies.Provider by default {

  private val recipientCache: LiveRecipientCache
  private var signalServiceMessageSender: REDServiceMessageSender? = null
  private var billingApi: BillingApi = mockk()
  private var accountApi: AccountApi = mockk()

  init {
    recipientCache = LiveRecipientCache(application) { r -> r.run() }
  }

  override fun provideBillingApi(): BillingApi = billingApi

  override fun provideAccountApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): AccountApi = accountApi

  override fun provideRecipientCache(): LiveRecipientCache {
    return recipientCache
  }

  override fun provideArchiveApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket, signalServiceConfiguration: REDServiceConfiguration): ArchiveApi {
    return mockk()
  }

  /**
   * Adds the Stripe-matching [ResponderInterceptor] on top of the default client (which supplies the
   * user agent + DNS), so `red.local/stripe` requests made by [org.signal.donations.StripeApi] are
   * answered from the shared [MockEndpoints.responder] and never hit the real network.
   */
  override fun provideOkHttpClient(): OkHttpClient {
    return default.provideOkHttpClient()
      .newBuilder()
      .addInterceptor(ResponderInterceptor(MockEndpoints.responder))
      .build()
  }

  /**
   * Backs the real [org.whispersystems.signalservice.api.donations.DonationsApi] (and any other
   * websocket API) with a [ResponderWebSocketConnection] driven by the shared [MockEndpoints.responder],
   * so RED-service requests are answered from the scenario's "world" and never hit the network.
   */
  override fun provideAuthWebSocket(
    signalServiceConfigurationSupplier: Supplier<REDServiceConfiguration>,
    libREDNetworkSupplier: Supplier<Network>
  ): REDWebSocket.AuthenticatedWebSocket {
    return REDWebSocket.AuthenticatedWebSocket(
      connectionFactory = { ResponderWebSocketConnection(MockEndpoints.responder) },
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
      connectionFactory = { ResponderWebSocketConnection(MockEndpoints.responder) },
      canConnect = { true },
      sleepTimer = UptimeSleepTimer(),
      disconnectTimeoutMs = 15.seconds.inWholeMilliseconds
    )
  }

  /**
   * Uses the test zk server's public params so credentials minted by [MockEndpoints] validate here.
   * The real [ClientZkReceiptOperations.receiveReceiptCredential] validation still runs in the
   * receipt-credential context job — only the params are swapped for test ones.
   */
  override fun provideClientZkReceiptOperations(signalServiceConfiguration: REDServiceConfiguration): ClientZkReceiptOperations {
    return DonationTestServer.clientReceiptOperations
  }

  override fun provideREDServiceMessageSender(
    protocolStore: REDServiceDataStore,
    pushServiceSocket: PushServiceSocket,
    messageApi: MessageApi,
    keysApi: KeysApi
  ): REDServiceMessageSender {
    if (signalServiceMessageSender == null) {
      signalServiceMessageSender = spyk(objToCopy = default.provideREDServiceMessageSender(protocolStore, pushServiceSocket, messageApi, keysApi))
    }
    return signalServiceMessageSender!!
  }
}
