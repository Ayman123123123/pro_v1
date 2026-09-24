package com.red.sovereign.dependencies

import androidx.media3.exoplayer.ExoPlayer
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.signal.core.util.billing.BillingApi
import org.signal.core.util.concurrent.DeadlockDetector
import org.signal.core.util.contentproviders.BlobProvider
import org.signal.donations.permits.DonationPermitsRepository
import org.signal.libsignal.net.Network
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations
import org.signal.network.api.ArchiveApi
import org.signal.network.api.AttachmentApi
import org.signal.network.api.CallingApi
import org.signal.network.api.CdsApi
import org.signal.network.api.CertificateApi
import org.signal.network.api.LinkDeviceApi
import org.signal.network.api.PaymentsApi
import org.signal.network.api.ProvisioningApi
import org.signal.network.api.RateLimitChallengeApi
import org.signal.network.api.RegistrationApiV2
import org.signal.network.api.RemoteConfigApi
import org.signal.network.api.SvrBApi
import org.signal.network.api.UsernameApi
import org.signal.network.config.REDServiceConfiguration
import org.signal.network.rest.REDRestClient
import org.signal.video.exo.ExoPlayerPool
import com.red.sovereign.components.TypingStatusRepository
import com.red.sovereign.components.TypingStatusSender
import com.red.sovereign.crypto.storage.REDServiceDataStoreImpl
import com.red.sovereign.database.DatabaseObserver
import com.red.sovereign.database.PendingRetryReceiptCache
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
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.util.function.Supplier

class MockApplicationDependencyProvider : AppDependencies.Provider {
  override fun providePushServiceSocket(signalServiceConfiguration: REDServiceConfiguration, groupsV2Operations: GroupsV2Operations): PushServiceSocket {
    return mockk(relaxed = true)
  }

  override fun provideREDRestClient(signalServiceConfiguration: REDServiceConfiguration): REDRestClient {
    return mockk(relaxed = true)
  }

  override fun provideOkHttpClient(): OkHttpClient {
    return mockk(relaxed = true)
  }

  override fun provideGroupsV2Operations(signalServiceConfiguration: REDServiceConfiguration): GroupsV2Operations {
    return mockk(relaxed = true)
  }

  override fun provideREDServiceAccountManager(authWebSocket: REDWebSocket.AuthenticatedWebSocket, accountApi: AccountApi, pushServiceSocket: PushServiceSocket, groupsV2Operations: GroupsV2Operations): REDServiceAccountManager {
    return mockk(relaxed = true)
  }

  override fun provideREDServiceMessageSender(
    protocolStore: REDServiceDataStore,
    pushServiceSocket: PushServiceSocket,
    messageApi: MessageApi,
    keysApi: KeysApi
  ): REDServiceMessageSender {
    return mockk(relaxed = true)
  }

  override fun provideMessageService(
    protocolStore: REDServiceDataStore,
    messageApiV2: org.signal.network.api.MessageApiV2,
    keysApiV2: org.signal.network.api.KeysApiV2
  ): org.signal.network.service.MessageService {
    return mockk(relaxed = true)
  }

  override fun provideREDServiceMessageReceiver(pushServiceSocket: PushServiceSocket): REDServiceMessageReceiver {
    return mockk(relaxed = true)
  }

  override fun provideREDServiceNetworkAccess(): REDServiceNetworkAccess {
    return mockk(relaxed = true)
  }

  override fun provideRecipientCache(): LiveRecipientCache {
    return mockk(relaxed = true)
  }

  override fun provideJobManager(configurationBuilder: JobManager.Configuration.Builder): JobManager {
    return mockk(relaxed = true)
  }

  override fun provideJobManagerConfigurationBuilder(): JobManager.Configuration.Builder {
    return mockk(relaxed = true)
  }

  override fun provideFrameRateTracker(): FrameRateTracker {
    return mockk(relaxed = true)
  }

  override fun provideMegaphoneRepository(): MegaphoneRepository {
    return mockk(relaxed = true)
  }

  override fun provideEarlyMessageCache(): EarlyMessageCache {
    return mockk(relaxed = true)
  }

  override fun provideMessageNotifier(): MessageNotifier {
    return mockk(relaxed = true)
  }

  override fun provideIncomingMessageObserver(webSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): IncomingMessageObserver {
    return mockk(relaxed = true)
  }

  override fun provideTrimThreadsByDateManager(): TrimThreadsByDateManager {
    return mockk(relaxed = true)
  }

  override fun provideViewOnceMessageManager(): ViewOnceMessageManager {
    return mockk(relaxed = true)
  }

  override fun provideExpiringStoriesManager(): ExpiringStoriesManager {
    return mockk(relaxed = true)
  }

  override fun provideExpiringArchivedStoriesManager(): ExpiringArchivedStoriesManager {
    return mockk(relaxed = true)
  }

  override fun provideExpiringMessageManager(): ExpiringMessageManager {
    return mockk(relaxed = true)
  }

  override fun provideDeletedCallEventManager(): DeletedCallEventManager {
    return mockk(relaxed = true)
  }

  override fun provideTypingStatusRepository(): TypingStatusRepository {
    return mockk(relaxed = true)
  }

  override fun provideTypingStatusSender(): TypingStatusSender {
    return mockk(relaxed = true)
  }

  override fun provideDatabaseObserver(): DatabaseObserver {
    return mockk(relaxed = true)
  }

  override fun providePayments(paymentsApi: PaymentsApi): Payments {
    return mockk(relaxed = true)
  }

  override fun provideShakeToReport(): ShakeToReport {
    return mockk(relaxed = true)
  }

  override fun provideREDCallManager(): REDCallManager {
    return mockk(relaxed = true)
  }

  override fun providePendingRetryReceiptManager(): PendingRetryReceiptManager {
    return mockk(relaxed = true)
  }

  override fun providePendingRetryReceiptCache(): PendingRetryReceiptCache {
    return mockk(relaxed = true)
  }

  override fun provideProtocolStore(): REDServiceDataStoreImpl {
    return mockk(relaxed = true)
  }

  override fun provideGiphyMp4Cache(): GiphyMp4Cache {
    return mockk(relaxed = true)
  }

  override fun provideExoPlayerPool(): ExoPlayerPool<ExoPlayer> {
    return mockk(relaxed = true)
  }

  override fun provideAndroidCallAudioManager(): AudioManagerCompat {
    return mockk(relaxed = true)
  }

  override fun provideDonationsService(donationsApi: DonationsApi): DonationsService {
    return mockk(relaxed = true)
  }

  override fun provideDonationPermitsRepository(zkGroupServerPublicParams: ByteArray): DonationPermitsRepository {
    return mockk(relaxed = true)
  }

  override fun provideProfileService(
    profileOperations: ClientZkProfileOperations,
    authWebSocket: REDWebSocket.AuthenticatedWebSocket,
    unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket
  ): ProfileService {
    return mockk(relaxed = true)
  }

  override fun provideDeadlockDetector(): DeadlockDetector {
    return mockk(relaxed = true)
  }

  override fun provideClientZkReceiptOperations(signalServiceConfiguration: REDServiceConfiguration): ClientZkReceiptOperations {
    return mockk(relaxed = true)
  }

  override fun provideScheduledMessageManager(): ScheduledMessageManager {
    return mockk(relaxed = true)
  }

  override fun providePinnedMessageManager(): PinnedMessageManager {
    return mockk(relaxed = true)
  }

  override fun provideLibsignalNetwork(config: REDServiceConfiguration): Network {
    return mockk(relaxed = true)
  }

  override fun provideBillingApi(): BillingApi {
    return mockk(relaxed = true)
  }

  override fun provideArchiveApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket, signalServiceConfiguration: REDServiceConfiguration): ArchiveApi {
    return mockk(relaxed = true)
  }

  override fun provideKeysApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): KeysApi {
    return mockk(relaxed = true)
  }

  override fun provideAttachmentApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): AttachmentApi {
    return mockk(relaxed = true)
  }

  override fun provideLinkDeviceApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): LinkDeviceApi {
    return mockk(relaxed = true)
  }

  override fun provideRegistrationApi(pushServiceSocket: PushServiceSocket): RegistrationApi {
    return mockk(relaxed = true)
  }

  override fun provideRegistrationApiV2(signalRestClient: REDRestClient): RegistrationApiV2 {
    return mockk(relaxed = true)
  }

  override fun provideStorageServiceApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): StorageServiceApi {
    return mockk(relaxed = true)
  }

  override fun provideAuthWebSocket(signalServiceConfigurationSupplier: Supplier<REDServiceConfiguration>, libREDNetworkSupplier: Supplier<Network>): REDWebSocket.AuthenticatedWebSocket {
    return mockk(relaxed = true)
  }

  override fun provideUnauthWebSocket(signalServiceConfigurationSupplier: Supplier<REDServiceConfiguration>, libREDNetworkSupplier: Supplier<Network>): REDWebSocket.UnauthenticatedWebSocket {
    return mockk(relaxed = true)
  }

  override fun provideAccountApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): AccountApi {
    return mockk(relaxed = true)
  }

  override fun provideUsernameApi(unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): UsernameApi {
    return mockk(relaxed = true)
  }

  override fun provideCallingApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket): CallingApi {
    return mockk(relaxed = true)
  }

  override fun providePaymentsApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): PaymentsApi {
    return mockk(relaxed = true)
  }

  override fun provideCdsApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): CdsApi {
    return mockk(relaxed = true)
  }

  override fun provideRateLimitChallengeApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): RateLimitChallengeApi {
    return mockk(relaxed = true)
  }

  override fun provideMessageApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): MessageApi {
    return mockk(relaxed = true)
  }

  override fun provideProvisioningApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): ProvisioningApi {
    return mockk(relaxed = true)
  }

  override fun provideCertificateApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket): CertificateApi {
    return mockk(relaxed = true)
  }

  override fun provideProfileApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket, pushServiceSocket: PushServiceSocket, clientZkProfileOperations: ClientZkProfileOperations): ProfileApi {
    return mockk(relaxed = true)
  }

  override fun provideRemoteConfigApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, pushServiceSocket: PushServiceSocket): RemoteConfigApi {
    return mockk(relaxed = true)
  }

  override fun provideDonationsApi(authWebSocket: REDWebSocket.AuthenticatedWebSocket, unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): DonationsApi {
    return mockk(relaxed = true)
  }

  override fun provideSvrBApi(libREDNetwork: Network): SvrBApi {
    return mockk(relaxed = true)
  }

  override fun provideKeyTransparencyApi(unauthWebSocket: REDWebSocket.UnauthenticatedWebSocket): KeyTransparencyApi {
    return mockk(relaxed = true)
  }

  override fun provideBlobs(): BlobProvider {
    return mockk(relaxed = true)
  }
}
