package com.red.sovereign.dependencies

import android.annotation.SuppressLint
import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import io.reactivex.rxjava3.subjects.BehaviorSubject
import okhttp3.OkHttpClient
import org.signal.camera.CameraDependencies
import org.signal.core.ui.CoreUiDependencies
import org.signal.core.util.CoreUtilDependencies
import org.signal.core.util.billing.BillingApi
import org.signal.core.util.concurrent.DeadlockDetector
import org.signal.core.util.concurrent.LatestValueObservable
import org.signal.core.util.contentproviders.BlobProvider
import org.signal.core.util.orNull
import org.signal.core.util.resettableLazy
import org.signal.donations.permits.DonationPermitsRepository
import org.signal.glide.REDGlideDependencies
import org.signal.libsignal.net.Network
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations
import org.signal.mediasend.MediaSendDependencies
import org.signal.network.api.ArchiveApi
import org.signal.network.api.AttachmentApi
import org.signal.network.api.CallingApi
import org.signal.network.api.CdsApi
import org.signal.network.api.CertificateApi
import org.signal.network.api.KeysApiV2
import org.signal.network.api.LinkDeviceApi
import org.signal.network.api.MessageApiV2
import org.signal.network.api.PaymentsApi
import org.signal.network.api.ProvisioningApi
import org.signal.network.api.RateLimitChallengeApi
import org.signal.network.api.RegistrationApiV2
import org.signal.network.api.RemoteConfigApi
import org.signal.network.api.SvrBApi
import org.signal.network.api.UsernameApi
import org.signal.network.config.HttpProxy
import org.signal.network.config.REDServiceConfiguration
import org.signal.network.rest.REDRestClient
import org.signal.network.service.MessageService
import org.signal.video.exo.ExoPlayerPool
import com.red.sovereign.BuildConfig
import com.red.sovereign.components.TypingStatusRepository
import com.red.sovereign.components.TypingStatusSender
import com.red.sovereign.crypto.storage.REDServiceDataStoreImpl
import com.red.sovereign.database.DatabaseObserver
import com.red.sovereign.database.PendingRetryReceiptCache
import com.red.sovereign.dependencies.AppDependencies.authWebSocket
import com.red.sovereign.groups.GroupsV2Authorization
import com.red.sovereign.jobmanager.JobManager
import com.red.sovereign.megaphone.MegaphoneRepository
import com.red.sovereign.messages.IncomingMessageObserver
import com.red.sovereign.notifications.MessageNotifier
import com.red.sovereign.payments.Payments
import com.red.sovereign.push.REDServiceNetworkAccess
import com.red.sovereign.recipients.LiveRecipientCache
import com.red.sovereign.revealable.ViewOnceMessageManager
import com.red.sovereign.service.DeletedCallEventManager
import com.red.sovereign.service.ExpiringArchivedStoriesManager
import com.red.sovereign.service.ExpiringMessageManager
import com.red.sovereign.service.ExpiringStoriesManager
import com.red.sovereign.service.PendingRetryReceiptManager
import com.red.sovereign.service.PinnedMessageManager
import com.red.sovereign.service.ScheduledMessageManager
import com.red.sovereign.service.TrimThreadsByDateManager
import com.red.sovereign.service.webrtc.REDCallManager
import com.red.sovereign.shakereport.ShakeToReport
import com.red.sovereign.util.EarlyMessageCache
import com.red.sovereign.util.FrameRateTracker
import com.red.sovereign.video.exo.GiphyMp4Cache
import com.red.sovereign.webrtc.audio.AudioManagerCompat
import org.whispersystems.signalservice.api.REDServiceAccountManager
import org.whispersystems.signalservice.api.REDServiceDataStore
import org.whispersystems.signalservice.api.REDServiceMessageReceiver
import org.whispersystems.signalservice.api.REDServiceMessageSender
import org.whispersystems.signalservice.api.account.AccountApi
import org.whispersystems.signalservice.api.donations.DonationsApi
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations
import org.whispersystems.signalservice.api.keys.KeysApi
import org.whispersystems.signalservice.api.message.MessageApi
import org.whispersystems.signalservice.api.profiles.ProfileApi
import org.whispersystems.signalservice.api.registration.RegistrationApi
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.api.services.ProfileService
import org.whispersystems.signalservice.api.storage.StorageServiceApi
import org.whispersystems.signalservice.api.websocket.REDWebSocket
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.util.function.Supplier

/**
 * Location for storing and retrieving application-scoped singletons. Users must call
 * [.init] before using any of the methods, preferably early on in
 * [Application.onCreate].
 *
 * All future application-scoped singletons should be written as normal objects, then placed here
 * to manage their singleton-ness.
 */
@SuppressLint("StaticFieldLeak")
object AppDependencies {
  private lateinit var _application: Application
  private lateinit var provider: Provider

  @JvmStatic
  @Synchronized
  fun init(application: Application, provider: Provider) {
    if (this::_application.isInitialized || this::provider.isInitialized) {
      return
    }

    _application = application
    AppDependencies.provider = provider

    CoreUtilDependencies.init(
      application,
      CoreUtilDependenciesProvider,
      CoreUtilDependencies.BuildInfo(
        canonicalVersionCode = BuildConfig.CANONICAL_VERSION_CODE,
        buildTimestamp = BuildConfig.BUILD_TIMESTAMP
      )
    )
    CoreUiDependencies.init(application, CoreUiDependenciesProvider)
    REDGlideDependencies.init(application, REDGlideDependenciesProvider)
    CameraDependencies.init(application, CameraDependenciesProvider)
    MediaSendDependencies.init(application, MediaSendDependenciesProvider)
  }

  @JvmStatic
  val isInitialized: Boolean
    get() = this::_application.isInitialized

  @JvmStatic
  val application: Application
    get() = _application

  @JvmStatic
  val recipientCache: LiveRecipientCache by lazy {
    provider.provideRecipientCache()
  }

  @JvmStatic
  val jobManager: JobManager by lazy {
    provider.provideJobManager(provider.provideJobManagerConfigurationBuilder())
  }

  @JvmStatic
  val frameRateTracker: FrameRateTracker by lazy {
    provider.provideFrameRateTracker()
  }

  @JvmStatic
  val megaphoneRepository: MegaphoneRepository by lazy {
    provider.provideMegaphoneRepository()
  }

  @JvmStatic
  val earlyMessageCache: EarlyMessageCache by lazy {
    provider.provideEarlyMessageCache()
  }

  @JvmStatic
  val typingStatusRepository: TypingStatusRepository by lazy {
    provider.provideTypingStatusRepository()
  }

  @JvmStatic
  val typingStatusSender: TypingStatusSender by lazy {
    provider.provideTypingStatusSender()
  }

  @JvmStatic
  val databaseObserver: DatabaseObserver by lazy {
    provider.provideDatabaseObserver()
  }

  @JvmStatic
  val trimThreadsByDateManager: TrimThreadsByDateManager by lazy {
    provider.provideTrimThreadsByDateManager()
  }

  @JvmStatic
  val viewOnceMessageManager: ViewOnceMessageManager by lazy {
    provider.provideViewOnceMessageManager()
  }

  @JvmStatic
  val expiringMessageManager: ExpiringMessageManager by lazy {
    provider.provideExpiringMessageManager()
  }

  @JvmStatic
  val deletedCallEventManager: DeletedCallEventManager by lazy {
    provider.provideDeletedCallEventManager()
  }

  @JvmStatic
  val signalCallManager: REDCallManager by lazy {
    provider.provideREDCallManager()
  }

  @JvmStatic
  val shakeToReport: ShakeToReport by lazy {
    provider.provideShakeToReport()
  }

  @JvmStatic
  val pendingRetryReceiptManager: PendingRetryReceiptManager by lazy {
    provider.providePendingRetryReceiptManager()
  }

  @JvmStatic
  val pendingRetryReceiptCache: PendingRetryReceiptCache by lazy {
    provider.providePendingRetryReceiptCache()
  }

  @JvmStatic
  val messageNotifier: MessageNotifier by lazy {
    provider.provideMessageNotifier()
  }

  @JvmStatic
  val giphyMp4Cache: GiphyMp4Cache by lazy {
    provider.provideGiphyMp4Cache()
  }

  @JvmStatic
  val exoPlayerPool: ExoPlayerPool<ExoPlayer> by lazy {
    provider.provideExoPlayerPool()
  }

  @JvmStatic
  val deadlockDetector: DeadlockDetector by lazy {
    provider.provideDeadlockDetector()
  }

  @JvmStatic
  val expireStoriesManager: ExpiringStoriesManager by lazy {
    provider.provideExpiringStoriesManager()
  }

  @JvmStatic
  val expireArchivedStoriesManager: ExpiringArchivedStoriesManager by lazy {
    provider.provideExpiringArchivedStoriesManager()
  }

  @JvmStatic
  val scheduledMessageManager: ScheduledMessageManager by lazy {
    provider.provideScheduledMessageManager()
  }

  @JvmStatic
  val pinnedMessageManager: PinnedMessageManager by lazy {
    provider.providePinnedMessageManager()
  }

  @JvmStatic
  val androidCallAudioManager: AudioManagerCompat by lazy {
    provider.provideAndroidCallAudioManager()
  }

  @JvmStatic
  val billingApi: BillingApi by lazy {
    provider.provideBillingApi()
  }

  @JvmStatic
  val blobs: BlobProvider by lazy {
    provider.provideBlobs()
  }

  private val _webSocketObserver: BehaviorSubject<WebSocketConnectionState> = BehaviorSubject.create()

  /**
   * An observable that emits the current state of the WebSocket connection across the various lifecycles
   * of the [authWebSocket].
   */
  @JvmStatic
  val webSocketObserver: LatestValueObservable<WebSocketConnectionState> = LatestValueObservable(_webSocketObserver)

  private val _networkModule = resettableLazy {
    NetworkDependenciesModule(application, provider, _webSocketObserver)
  }
  private val networkModule by _networkModule

  @JvmStatic
  val signalServiceNetworkAccess: REDServiceNetworkAccess
    get() = networkModule.signalServiceNetworkAccess

  @JvmStatic
  val protocolStore: REDServiceDataStoreImpl
    get() = networkModule.protocolStore

  @JvmStatic
  val signalServiceMessageSender: REDServiceMessageSender
    get() = networkModule.signalServiceMessageSender

  @JvmStatic
  val messageService: MessageService
    get() = networkModule.messageService

  @JvmStatic
  val signalServiceAccountManager: REDServiceAccountManager
    get() = networkModule.signalServiceAccountManager

  @JvmStatic
  val signalServiceMessageReceiver: REDServiceMessageReceiver
    get() = networkModule.signalServiceMessageReceiver

  @JvmStatic
  val incomingMessageObserver: IncomingMessageObserver
    get() = networkModule.incomingMessageObserver

  @JvmStatic
  val libsignalNetwork: Network
    get() = networkModule.libsignalNetwork

  @JvmStatic
  val authWebSocket: REDWebSocket.AuthenticatedWebSocket
    get() = networkModule.authWebSocket

  @JvmStatic
  val unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket
    get() = networkModule.unauthWebSocket

  @JvmStatic
  val groupsV2Authorization: GroupsV2Authorization
    get() = networkModule.groupsV2Authorization

  @JvmStatic
  val groupsV2Operations: GroupsV2Operations
    get() = networkModule.groupsV2Operations

  @JvmStatic
  val clientZkReceiptOperations
    get() = networkModule.clientZkReceiptOperations

  @JvmStatic
  val payments: Payments
    get() = networkModule.payments

  @JvmStatic
  val profileService: ProfileService
    get() = networkModule.profileService

  @JvmStatic
  val donationsService: DonationsService
    get() = networkModule.donationsService

  @JvmStatic
  val archiveApi: ArchiveApi
    get() = networkModule.archiveApi

  @JvmStatic
  val keysApi: KeysApi
    get() = networkModule.keysApi

  @JvmStatic
  val attachmentApi: AttachmentApi
    get() = networkModule.attachmentApi

  @JvmStatic
  val linkDeviceApi: LinkDeviceApi
    get() = networkModule.linkDeviceApi

  @JvmStatic
  val pushServiceSocket: PushServiceSocket
    get() = networkModule.pushServiceSocket

  @JvmStatic
  val signalRestClient: REDRestClient
    get() = networkModule.signalRestClient

  @JvmStatic
  val registrationApi: RegistrationApi
    get() = networkModule.registrationApi

  @JvmStatic
  val registrationApiV2: RegistrationApiV2
    get() = networkModule.registrationApiV2

  val storageServiceApi: StorageServiceApi
    get() = networkModule.storageServiceApi

  val accountApi: AccountApi
    get() = networkModule.accountApi

  val usernameApi: UsernameApi
    get() = networkModule.usernameApi

  val svrBApi: SvrBApi
    get() = networkModule.svrBApi

  val callingApi: CallingApi
    get() = networkModule.callingApi

  val paymentsApi: PaymentsApi
    get() = networkModule.paymentsApi

  val cdsApi: CdsApi
    get() = networkModule.cdsApi

  val rateLimitChallengeApi: RateLimitChallengeApi
    get() = networkModule.rateLimitChallengeApi

  val messageApi: MessageApi
    get() = networkModule.messageApi

  val provisioningApi: ProvisioningApi
    get() = networkModule.provisioningApi

  val certificateApi: CertificateApi
    get() = networkModule.certificateApi

  val profileApi: ProfileApi
    get() = networkModule.profileApi

  val remoteConfigApi: RemoteConfigApi
    get() = networkModule.remoteConfigApi

  val donationsApi: DonationsApi
    get() = networkModule.donationsApi

  @JvmStatic
  val donationPermitsRepository: DonationPermitsRepository by lazy {
    provider.provideDonationPermitsRepository(signalServiceNetworkAccess.getConfiguration().zkGroupServerPublicParams)
  }

  val keyTransparencyApi: KeyTransparencyApi
    get() = networkModule.keyTransparencyApi

  @JvmStatic
  val okHttpClient: OkHttpClient
    get() = networkModule.okHttpClient

  @JvmStatic
  val signalOkHttpClient: OkHttpClient
    get() = networkModule.signalOkHttpClient

  @JvmStatic
  fun resetProtocolStores() {
    networkModule.resetProtocolStores()
  }

  @JvmStatic
  fun resetNetwork() {
    networkModule.closeConnections()
    _networkModule.reset()
  }

  @JvmStatic
  fun startNetwork() {
    networkModule.openConnections()
  }

  fun onSystemHttpProxyChange(systemHttpProxy: HttpProxy?): Boolean {
    val currentSystemProxy = signalServiceNetworkAccess.getConfiguration().systemHttpProxy.orNull()
    return if (currentSystemProxy?.host != systemHttpProxy?.host || currentSystemProxy?.port != systemHttpProxy?.port) {
      resetNetwork()
      true
    } else {
      false
    }
  }

  interface Provider {
    fun providePushServiceSocket(signalServiceConfiguration: REDServiceConfiguration, groupsV2Operations: GroupsV2Operations): PushServiceSocket
    fun provideREDRestClient(signalServiceConfiguration: REDServiceConfiguration): REDRestClient
    fun provideGroupsV2Operations(signalServiceConfiguration: REDServiceConfiguration): GroupsV2Operations
    fun provideREDServiceAccountManager(authWebSocket: REDWebSocket.AuthenticatedWebSocket, accountApi: AccountApi, pushServiceSocket: PushServiceSocket, groupsV2Operations: GroupsV2Operations): REDServiceAccountManager
    fun provideREDServiceMessageSender(protocolStore: REDServiceDataStore, pushServiceSocket: PushServiceSocket, messageApi: MessageApi, keysApi: KeysApi): REDServiceMessageSender
    fun provideMessageService(protocolStore: REDServiceDataStore, messageApiV2: MessageApiV2, keysApiV2: KeysApiV2): MessageService
    fun provideREDServiceMessageReceiver(pushServiceSocket: PushServiceSocket): REDServiceMessageReceiver
    fun provideREDServiceNetworkAccess(): REDServiceNetworkAccess
    fun provideRecipientCache(): LiveRecipientCache
    fun provideJobManager(configurationBuilder: JobManager.Configuration.Builder): JobManager
    fun provideJobManagerConfigurationBuilder(): JobManager.Configuration.Builder
    fun provideFrameRateTracker(): FrameRateTracker
    fun provideMegaphoneRepository(): MegaphoneRepository
    fun provideEarlyMessageCache(): EarlyMessageCache
    fun provideMessageNotifier(): MessageNotifier
    fun provideIncomingMessageObserver(webSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): IncomingMessageObserver
    fun provideTrimThreadsByDateManager(): TrimThreadsByDateManager
    fun provideViewOnceMessageManager(): ViewOnceMessageManager
    fun provideExpiringStoriesManager(): ExpiringStoriesManager
    fun provideExpiringArchivedStoriesManager(): ExpiringArchivedStoriesManager
    fun provideExpiringMessageManager(): ExpiringMessageManager
    fun provideDeletedCallEventManager(): DeletedCallEventManager
    fun provideTypingStatusRepository(): TypingStatusRepository
    fun provideTypingStatusSender(): TypingStatusSender
    fun provideDatabaseObserver(): DatabaseObserver
    fun providePayments(paymentsApi: PaymentsApi): Payments
    fun provideShakeToReport(): ShakeToReport
    fun provideREDCallManager(): REDCallManager
    fun providePendingRetryReceiptManager(): PendingRetryReceiptManager
    fun providePendingRetryReceiptCache(): PendingRetryReceiptCache
    fun provideProtocolStore(): REDServiceDataStoreImpl
    fun provideGiphyMp4Cache(): GiphyMp4Cache
    fun provideExoPlayerPool(): ExoPlayerPool<ExoPlayer>
    fun provideAndroidCallAudioManager(): AudioManagerCompat
    fun provideDonationsService(donationsApi: DonationsApi): DonationsService
    fun provideDonationPermitsRepository(zkGroupServerPublicParams: ByteArray): DonationPermitsRepository
    fun provideProfileService(profileOperations: ClientZkProfileOperations, authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): ProfileService
    fun provideDeadlockDetector(): DeadlockDetector
    fun provideClientZkReceiptOperations(signalServiceConfiguration: REDServiceConfiguration): ClientZkReceiptOperations
    fun provideOkHttpClient(): OkHttpClient
    fun provideScheduledMessageManager(): ScheduledMessageManager
    fun providePinnedMessageManager(): PinnedMessageManager
    fun provideLibsignalNetwork(config: REDServiceConfiguration): Network
    fun provideBillingApi(): BillingApi
    fun provideArchiveApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket, signalServiceConfiguration: REDServiceConfiguration): ArchiveApi
    fun provideKeysApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): KeysApi
    fun provideAttachmentApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): AttachmentApi
    fun provideLinkDeviceApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): LinkDeviceApi
    fun provideRegistrationApi(pushServiceSocket: PushServiceSocket): RegistrationApi
    fun provideRegistrationApiV2(signalRestClient: REDRestClient): RegistrationApiV2
    fun provideStorageServiceApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): StorageServiceApi
    fun provideAuthWebSocket(signalServiceConfigurationSupplier: Supplier<REDServiceConfiguration>, libREDNetworkSupplier: Supplier<Network>): REDWebSocket.AuthenticatedWebSocket
    fun provideUnauthWebSocket(signalServiceConfigurationSupplier: Supplier<REDServiceConfiguration>, libREDNetworkSupplier: Supplier<Network>): REDWebSocket.UnauthenticatedWebSocket
    fun provideAccountApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): AccountApi
    fun provideUsernameApi(unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): UsernameApi
    fun provideCallingApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket): CallingApi
    fun providePaymentsApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): PaymentsApi

    fun provideCdsApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): CdsApi
    fun provideRateLimitChallengeApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): RateLimitChallengeApi
    fun provideMessageApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): MessageApi
    fun provideProvisioningApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): ProvisioningApi
    fun provideCertificateApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): CertificateApi
    fun provideProfileApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket, clientZkProfileOperations: ClientZkProfileOperations): ProfileApi
    fun provideRemoteConfigApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): RemoteConfigApi
    fun provideDonationsApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): DonationsApi
    fun provideSvrBApi(libREDNetwork: Network): SvrBApi
    fun provideKeyTransparencyApi(unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): KeyTransparencyApi
    fun provideBlobs(): BlobProvider
  }
}
