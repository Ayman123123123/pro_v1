package com.red.sovereign.dependencies;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.media3.exoplayer.ExoPlayer;

import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.signal.billing.BillingFactory;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.ServiceId.PNI;
import org.signal.core.util.AppForegroundObserver;
import org.signal.core.util.ByteUnit;
import org.signal.core.util.SleepTimer;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.UptimeSleepTimer;
import org.signal.core.util.billing.BillingApi;
import org.signal.core.util.concurrent.DeadlockDetector;
import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.contentproviders.BlobProvider;
import org.signal.donations.permits.DonationPermitsRepository;
import org.signal.libsignal.net.Network;
import org.signal.libsignal.protocol.REDProtocolAddress;
import org.signal.libsignal.zkgroup.GenericServerPublicParams;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.ServerPublicParams;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations;
import org.signal.network.api.ArchiveApi;
import org.signal.network.api.AttachmentApi;
import org.signal.network.api.CallingApi;
import org.signal.network.api.CdsApi;
import org.signal.network.api.CertificateApi;
import org.signal.network.api.KeysApiV2;
import org.signal.network.api.LinkDeviceApi;
import org.signal.network.api.MessageApiV2;
import org.signal.network.api.PaymentsApi;
import org.signal.network.api.ProvisioningApi;
import org.signal.network.api.RateLimitChallengeApi;
import org.signal.network.api.RegistrationApiV2;
import org.signal.network.api.RemoteConfigApi;
import org.signal.network.api.SvrBApi;
import org.signal.network.api.UsernameApi;
import org.signal.network.rest.REDRestClient;
import org.signal.network.service.MessageService;
import org.signal.video.exo.ExoPlayerPool;
import com.red.sovereign.BuildConfig;
import com.red.sovereign.components.TypingStatusRepository;
import com.red.sovereign.components.TypingStatusSender;
import com.red.sovereign.components.settings.app.subscription.permits.DonationPermits;
import com.red.sovereign.components.settings.app.subscription.permits.NetworkDonationPermitIssuer;
import com.red.sovereign.crypto.AppAttachmentSecretStore;
import com.red.sovereign.crypto.ReentrantSessionLock;
import com.red.sovereign.crypto.storage.REDBaseIdentityKeyStore;
import com.red.sovereign.crypto.storage.REDIdentityKeyStore;
import com.red.sovereign.crypto.storage.REDKyberPreKeyStore;
import com.red.sovereign.crypto.storage.REDSenderKeyStore;
import com.red.sovereign.crypto.storage.REDServiceAccountDataStoreImpl;
import com.red.sovereign.crypto.storage.REDServiceDataStoreImpl;
import com.red.sovereign.crypto.storage.TextSecurePreKeyStore;
import com.red.sovereign.crypto.storage.TextSecureSessionStore;
import com.red.sovereign.database.DatabaseObserver;
import com.red.sovereign.database.JobDatabase;
import com.red.sovereign.database.PendingRetryReceiptCache;
import com.red.sovereign.jobmanager.JobManager;
import com.red.sovereign.jobmanager.JobMigrator;
import com.red.sovereign.jobmanager.impl.FactoryJobPredicate;
import com.red.sovereign.jobs.AttachmentCompressionJob;
import com.red.sovereign.jobs.AttachmentUploadJob;
import com.red.sovereign.jobs.FastJobStorage;
import com.red.sovereign.jobs.GroupCallUpdateSendJob;
import com.red.sovereign.jobs.IndividualSendJob;
import com.red.sovereign.jobs.JobManagerFactories;
import com.red.sovereign.jobs.MarkerJob;
import com.red.sovereign.jobs.PreKeysSyncJob;
import com.red.sovereign.jobs.PushGroupSendJob;
import com.red.sovereign.jobs.PushProcessMessageJob;
import com.red.sovereign.jobs.ReactionSendJob;
import com.red.sovereign.jobs.SendDeliveryReceiptJob;
import com.red.sovereign.jobs.TypingSendJob;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.megaphone.MegaphoneRepository;
import com.red.sovereign.messages.IncomingMessageObserver;
import com.red.sovereign.net.DeviceTransferBlockingInterceptor;
import com.red.sovereign.net.REDWebSocketHealthMonitor;
import com.red.sovereign.net.StandardUserAgentInterceptor;
import com.red.sovereign.notifications.MessageNotifier;
import com.red.sovereign.notifications.OptimizedMessageNotifier;
import com.red.sovereign.payments.MobileCoinConfig;
import com.red.sovereign.payments.Payments;
import com.red.sovereign.push.SecurityEventListener;
import com.red.sovereign.push.REDServiceNetworkAccess;
import com.red.sovereign.recipients.LiveRecipientCache;
import com.red.sovereign.revealable.ViewOnceMessageManager;
import com.red.sovereign.service.DeletedCallEventManager;
import com.red.sovereign.service.ExpiringArchivedStoriesManager;
import com.red.sovereign.service.ExpiringMessageManager;
import com.red.sovereign.service.ExpiringStoriesManager;
import com.red.sovereign.service.PendingRetryReceiptManager;
import com.red.sovereign.service.PinnedMessageManager;
import com.red.sovereign.service.ScheduledMessageManager;
import com.red.sovereign.service.TrimThreadsByDateManager;
import com.red.sovereign.service.webrtc.REDCallManager;
import com.red.sovereign.shakereport.ShakeToReport;
import com.red.sovereign.stories.Stories;
import com.red.sovereign.util.AlarmSleepTimer;
import com.red.sovereign.util.EarlyMessageCache;
import com.red.sovereign.util.Environment;
import com.red.sovereign.util.FrameRateTracker;
import com.red.sovereign.util.PreKeyBatcher;
import com.red.sovereign.util.RemoteConfig;
import com.red.sovereign.util.TextSecurePreferences;
import com.red.sovereign.video.exo.GiphyMp4Cache;
import com.red.sovereign.video.exo.SimpleExoPlayerPool;
import com.red.sovereign.webrtc.audio.AudioManagerCompat;
import org.whispersystems.signalservice.api.REDServiceAccountDataStore;
import org.whispersystems.signalservice.api.REDServiceAccountManager;
import org.whispersystems.signalservice.api.REDServiceDataStore;
import org.whispersystems.signalservice.api.REDServiceMessageReceiver;
import org.whispersystems.signalservice.api.REDServiceMessageSender;
import org.whispersystems.signalservice.api.account.AccountApi;
import org.whispersystems.signalservice.api.crypto.REDServiceCipher;
import org.whispersystems.signalservice.api.donations.DonationsApi;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.keys.KeysApi;
import org.whispersystems.signalservice.api.keys.PreKeyRepository;
import org.whispersystems.signalservice.api.message.MessageApi;
import org.whispersystems.signalservice.api.profiles.ProfileApi;
import org.whispersystems.signalservice.api.push.REDServiceAddress;
import org.whispersystems.signalservice.api.registration.RegistrationApi;
import org.whispersystems.signalservice.api.services.DonationsService;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.api.storage.StorageServiceApi;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.websocket.REDWebSocket;
import org.whispersystems.signalservice.api.websocket.WebSocketFactory;
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException;
import org.signal.network.config.REDServiceConfiguration;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.websocket.LibREDChatConnection;
import org.whispersystems.signalservice.internal.websocket.LibREDNetworkExtensions;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Implementation of {@link AppDependencies.Provider} that provides real app dependencies.
 */
public class ApplicationDependencyProvider implements AppDependencies.Provider {

  private final Application context;

  public ApplicationDependencyProvider(@NonNull Application context) {
    this.context = context;
  }

  private @NonNull ClientZkOperations provideClientZkOperations(@NonNull REDServiceConfiguration signalServiceConfiguration) {
    return ClientZkOperations.create(signalServiceConfiguration);
  }

  @Override
  public @NonNull PushServiceSocket providePushServiceSocket(@NonNull REDServiceConfiguration signalServiceConfiguration, @NonNull GroupsV2Operations groupsV2Operations) {
    return new PushServiceSocket(signalServiceConfiguration,
                                 new DynamicCredentialsProvider(),
                                 BuildConfig.SIGNAL_AGENT,
                                 RemoteConfig.okHttpAutomaticRetry());
  }

  @Override
  public @NonNull REDRestClient provideREDRestClient(@NonNull REDServiceConfiguration signalServiceConfiguration) {
    return new REDRestClient(signalServiceConfiguration,
                                BuildConfig.SIGNAL_AGENT,
                                new DynamicCredentialsProvider(),
                                RemoteConfig.okHttpAutomaticRetry());
  }

  @Override
  public @NonNull GroupsV2Operations provideGroupsV2Operations(@NonNull REDServiceConfiguration signalServiceConfiguration) {
    return new GroupsV2Operations(provideClientZkOperations(signalServiceConfiguration), RemoteConfig.groupLimits().getHardLimit());
  }

  @Override
  public @NonNull REDServiceAccountManager provideREDServiceAccountManager(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull AccountApi accountApi, @NonNull PushServiceSocket pushServiceSocket, @NonNull GroupsV2Operations groupsV2Operations) {
    return new REDServiceAccountManager(authWebSocket, accountApi, pushServiceSocket, groupsV2Operations);
  }

  @Override
  public @NonNull REDServiceMessageSender provideREDServiceMessageSender(@NonNull REDServiceDataStore protocolStore,
                                                                               @NonNull PushServiceSocket pushServiceSocket,
                                                                               @NonNull MessageApi messageApi,
                                                                               @NonNull KeysApi keysApi) {
      return new REDServiceMessageSender(pushServiceSocket,
                                            protocolStore,
                                            ReentrantSessionLock.INSTANCE,
                                            messageApi,
                                            keysApi,
                                            Optional.of(new SecurityEventListener(context)),
                                            REDExecutors.newCachedBoundedExecutor("signal-messages", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD, 1, 16, 30),
                                            RemoteConfig.maxEnvelopeSizeBytes(),
                                            RemoteConfig.maxIncrementalMacsPerEnvelope(),
                                            RemoteConfig::useMessageSendRestFallback,
                                            new PreKeyRepository(
                                                keysApi,
                                                protocolStore.aci(),
                                                new REDProtocolAddress(pushServiceSocket.getCredentialsProvider().getAci().getLibREDServiceId(),
                                                                          pushServiceSocket.getCredentialsProvider().getDeviceId()),
                                                ReentrantSessionLock.INSTANCE,
                                                PreKeyBatcher.INSTANCE
                                              )
                                            );
  }

  @Override
  public @NonNull MessageService provideMessageService(@NonNull REDServiceDataStore protocolStore,
                                                       @NonNull MessageApiV2 messageApiV2,
                                                       @NonNull KeysApiV2 keysApiV2) {
    REDServiceAddress          localAddress  = new REDServiceAddress(REDStore.account().requireAci(), REDStore.account().getE164());
    int                           localDeviceId = REDStore.account().getDeviceId();
    REDServiceAccountDataStore aciStore      = protocolStore.aci();
    REDServiceCipher           cipher        = new REDServiceCipher(localAddress, localDeviceId, aciStore, ReentrantSessionLock.INSTANCE, null);

    return new MessageService(localAddress, localDeviceId, messageApiV2, keysApiV2, aciStore, ReentrantSessionLock.INSTANCE, cipher, RemoteConfig.maxEnvelopeSizeBytes());
  }

  @Override
  public @NonNull REDServiceMessageReceiver provideREDServiceMessageReceiver(@NonNull PushServiceSocket pushServiceSocket) {
    return new REDServiceMessageReceiver(pushServiceSocket);
  }

  @Override
  public @NonNull REDServiceNetworkAccess provideREDServiceNetworkAccess() {
    return new REDServiceNetworkAccess(context);
  }

  @Override
  public @NonNull LiveRecipientCache provideRecipientCache() {
    return new LiveRecipientCache(context);
  }

  @Override
  public @NonNull JobManager provideJobManager(@NonNull JobManager.Configuration.Builder configurationBuilder) {
    return new JobManager(context, configurationBuilder.build());
  }

  @Override
  public @NonNull JobManager.Configuration.Builder provideJobManagerConfigurationBuilder() {
    return new JobManager.Configuration.Builder()
                                       .setJobFactories(JobManagerFactories.getJobFactories(context))
                                       .setConstraintFactories(JobManagerFactories.getConstraintFactories(context))
                                       .setConstraintObservers(JobManagerFactories.getConstraintObservers(context))
                                       .setJobStorage(new FastJobStorage(JobDatabase.getInstance(context)))
                                       .setJobMigrator(new JobMigrator(TextSecurePreferences.getJobManagerVersion(context), JobManager.CURRENT_VERSION, JobManagerFactories.getJobMigrations(context)))
                                       .addReservedJobRunner(new FactoryJobPredicate(PushProcessMessageJob.KEY, MarkerJob.KEY))
                                       .addReservedJobRunner(new FactoryJobPredicate(AttachmentUploadJob.KEY, AttachmentCompressionJob.KEY))
                                       .addReservedJobRunner(new FactoryJobPredicate(
                                           IndividualSendJob.KEY,
                                           PushGroupSendJob.KEY,
                                           ReactionSendJob.KEY,
                                           TypingSendJob.KEY,
                                           GroupCallUpdateSendJob.KEY,
                                           SendDeliveryReceiptJob.KEY
                                       ));
  }

  @Override
  public @NonNull FrameRateTracker provideFrameRateTracker() {
    return new FrameRateTracker(context);
  }

  @SuppressLint("DiscouragedApi")
  public @NonNull MegaphoneRepository provideMegaphoneRepository() {
    return new MegaphoneRepository(context);
  }

  @Override
  public @NonNull EarlyMessageCache provideEarlyMessageCache() {
    return new EarlyMessageCache();
  }

  @Override
  public @NonNull MessageNotifier provideMessageNotifier() {
    return new OptimizedMessageNotifier(context);
  }

  @Override
  public @NonNull IncomingMessageObserver provideIncomingMessageObserver(@NonNull REDWebSocket.AuthenticatedWebSocket webSocket, @NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new IncomingMessageObserver(context, webSocket, unauthWebSocket);
  }

  @Override
  public @NonNull TrimThreadsByDateManager provideTrimThreadsByDateManager() {
    return new TrimThreadsByDateManager(context);
  }

  @Override
  public @NonNull ViewOnceMessageManager provideViewOnceMessageManager() {
    return new ViewOnceMessageManager(context);
  }

  @Override
  public @NonNull ExpiringStoriesManager provideExpiringStoriesManager() {
    return new ExpiringStoriesManager(context);
  }

  @Override
  public @NonNull ExpiringArchivedStoriesManager provideExpiringArchivedStoriesManager() {
    return new ExpiringArchivedStoriesManager(context);
  }

  @Override
  public @NonNull ExpiringMessageManager provideExpiringMessageManager() {
    return new ExpiringMessageManager(context);
  }

  @Override
  public @NonNull DeletedCallEventManager provideDeletedCallEventManager() {
    return new DeletedCallEventManager(context);
  }

  @Override
  public @NonNull ScheduledMessageManager provideScheduledMessageManager() {
    return new ScheduledMessageManager(context);
  }

  @Override
  public @NonNull PinnedMessageManager providePinnedMessageManager() {
    return new PinnedMessageManager(context);
  }

  @Override
  public @NonNull Network provideLibsignalNetwork(@NonNull REDServiceConfiguration config) {
    Network network = new Network(BuildConfig.LIBSIGNAL_NET_ENV, StandardUserAgentInterceptor.USER_AGENT, RemoteConfig.getLibsignalConfigs(), Network.BuildVariant.PRODUCTION);
    LibREDNetworkExtensions.applyConfiguration(network, config);

    return network;
  }

  @Override
  public @NonNull TypingStatusRepository provideTypingStatusRepository() {
    return new TypingStatusRepository();
  }

  @Override
  public @NonNull TypingStatusSender provideTypingStatusSender() {
    return new TypingStatusSender();
  }

  @Override
  public @NonNull DatabaseObserver provideDatabaseObserver() {
    return new DatabaseObserver();
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public @NonNull Payments providePayments(@NonNull PaymentsApi paymentsApi) {
    MobileCoinConfig network;

    if      (BuildConfig.MOBILE_COIN_ENVIRONMENT.equals("mainnet")) network = MobileCoinConfig.getMainNet(paymentsApi);
    else if (BuildConfig.MOBILE_COIN_ENVIRONMENT.equals("testnet")) network = MobileCoinConfig.getTestNet(paymentsApi);
    else throw new AssertionError("Unknown network " + BuildConfig.MOBILE_COIN_ENVIRONMENT);

    return new Payments(network);
  }

  @Override
  public @NonNull ShakeToReport provideShakeToReport() {
    return new ShakeToReport(context);
  }

  @Override
  public @NonNull REDCallManager provideREDCallManager() {
    return new REDCallManager(context);
  }

  @Override
  public @NonNull PendingRetryReceiptManager providePendingRetryReceiptManager() {
    return new PendingRetryReceiptManager(context);
  }

  @Override
  public @NonNull PendingRetryReceiptCache providePendingRetryReceiptCache() {
    return new PendingRetryReceiptCache();
  }

  @Override
  public @NonNull REDWebSocket.AuthenticatedWebSocket provideAuthWebSocket(@NonNull Supplier<REDServiceConfiguration> signalServiceConfigurationSupplier, @NonNull Supplier<Network> libREDNetworkSupplier) {
    SleepTimer                   sleepTimer    = !REDStore.account().isFcmEnabled() || REDStore.settings().getForceWebsocketMode().isEnabled() ? new AlarmSleepTimer(context) : new UptimeSleepTimer();
    REDWebSocketHealthMonitor healthMonitor = new REDWebSocketHealthMonitor(sleepTimer, true);

    WebSocketFactory authFactory = () -> {
      DynamicCredentialsProvider credentialsProvider = new DynamicCredentialsProvider();

      if (credentialsProvider.isInvalid()) {
        throw new WebSocketUnavailableException("Invalid auth credentials");
      }

      Network network = libREDNetworkSupplier.get();
      return new LibREDChatConnection("libsignal-auth",
                                         network,
                                         credentialsProvider,
                                         Stories.isFeatureEnabled(),
                                         healthMonitor);
    };

    REDWebSocket.AuthenticatedWebSocket webSocket = new REDWebSocket.AuthenticatedWebSocket(authFactory,
                                                                                                  () -> !REDStore.misc().isClientDeprecated() && REDStore.account().isRegistered() && !TextSecurePreferences.isUnauthorizedReceived(context) && !DeviceTransferBlockingInterceptor.getInstance().isBlockingNetwork() && !Environment.IS_INSTRUMENTATION,
                                                                                                  sleepTimer,
                                                                                                  TimeUnit.SECONDS.toMillis(30));
    if (AppForegroundObserver.isForegrounded()) {
      webSocket.registerKeepAliveToken(REDWebSocket.FOREGROUND_KEEPALIVE);
    }

    healthMonitor.monitor(webSocket);

    return webSocket;
  }

  @Override
  public @NonNull REDWebSocket.UnauthenticatedWebSocket provideUnauthWebSocket(@NonNull Supplier<REDServiceConfiguration> signalServiceConfigurationSupplier, @NonNull Supplier<Network> libREDNetworkSupplier) {
    SleepTimer                   sleepTimer    = !REDStore.account().isFcmEnabled() || REDStore.settings().getForceWebsocketMode().isEnabled() ? new AlarmSleepTimer(context) : new UptimeSleepTimer();
    REDWebSocketHealthMonitor healthMonitor = new REDWebSocketHealthMonitor(sleepTimer, false);

    WebSocketFactory unauthFactory = () -> {
      Network network = libREDNetworkSupplier.get();
      return new LibREDChatConnection("libsignal-unauth",
                                         network,
                                         null,
                                         Stories.isFeatureEnabled(),
                                         healthMonitor);
    };

    REDWebSocket.UnauthenticatedWebSocket webSocket = new REDWebSocket.UnauthenticatedWebSocket(unauthFactory,
                                                                                                      () -> !REDStore.misc().isClientDeprecated() && !DeviceTransferBlockingInterceptor.getInstance().isBlockingNetwork() && !Environment.IS_INSTRUMENTATION,
                                                                                                      sleepTimer,
                                                                                                      TimeUnit.SECONDS.toMillis(30));
    if (AppForegroundObserver.isForegrounded()) {
      webSocket.registerKeepAliveToken(REDWebSocket.FOREGROUND_KEEPALIVE);
    }

    healthMonitor.monitor(webSocket);
    return webSocket;
  }

  @Override
  public @NonNull REDServiceDataStoreImpl provideProtocolStore() {
    ACI localAci = REDStore.account().getAci();
    PNI localPni = REDStore.account().getPni();

    if (localAci == null) {
      throw new IllegalStateException("No ACI set!");
    }

    if (localPni == null) {
      throw new IllegalStateException("No PNI set!");
    }

    boolean needsPreKeyJob = false;

    if (!REDStore.account().hasAciIdentityKey()) {
      REDStore.account().generateAciIdentityKeyIfNecessary();
      needsPreKeyJob = true;
    }

    if (!REDStore.account().hasPniIdentityKey()) {
      REDStore.account().generatePniIdentityKeyIfNecessary();
      needsPreKeyJob = true;
    }

    if (needsPreKeyJob) {
      PreKeysSyncJob.enqueueIfNeeded();
    }

    REDBaseIdentityKeyStore baseIdentityStore = new REDBaseIdentityKeyStore(context);

    REDServiceAccountDataStoreImpl aciStore = new REDServiceAccountDataStoreImpl(context,
                                                                                       new TextSecurePreKeyStore(localAci),
                                                                                       new REDKyberPreKeyStore(localAci),
                                                                                       new REDIdentityKeyStore(baseIdentityStore, () -> REDStore.account().getAciIdentityKey()),
                                                                                       new TextSecureSessionStore(localAci),
                                                                                       new REDSenderKeyStore(context));

    REDServiceAccountDataStoreImpl pniStore = new REDServiceAccountDataStoreImpl(context,
                                                                                       new TextSecurePreKeyStore(localPni),
                                                                                       new REDKyberPreKeyStore(localPni),
                                                                                       new REDIdentityKeyStore(baseIdentityStore, () -> REDStore.account().getPniIdentityKey()),
                                                                                       new TextSecureSessionStore(localPni),
                                                                                       new REDSenderKeyStore(context));
    return new REDServiceDataStoreImpl(context, aciStore, pniStore);
  }

  @Override
  public @NonNull GiphyMp4Cache provideGiphyMp4Cache() {
    return new GiphyMp4Cache(ByteUnit.MEGABYTES.toBytes(16));
  }

  @Override
  public @NonNull ExoPlayerPool<ExoPlayer> provideExoPlayerPool() {
    return new SimpleExoPlayerPool(context);
  }

  @Override
  public @NonNull AudioManagerCompat provideAndroidCallAudioManager() {
    return AudioManagerCompat.create(context);
  }

  @Override
  public @NonNull DonationsService provideDonationsService(@NonNull DonationsApi donationsApi) {
    return new DonationsService(donationsApi, DonationPermits.INSTANCE);
  }

  @Override
  public @NonNull DonationPermitsRepository provideDonationPermitsRepository(@NonNull byte[] zkGroupServerPublicParams) {
    try {
      return new DonationPermitsRepository(NetworkDonationPermitIssuer.INSTANCE, new ServerPublicParams(zkGroupServerPublicParams));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public @NonNull ProfileService provideProfileService(@NonNull ClientZkProfileOperations clientZkProfileOperations,
                                                       @NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket,
                                                       @NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket)
  {
    return new ProfileService(clientZkProfileOperations, authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull DeadlockDetector provideDeadlockDetector() {
    HandlerThread handlerThread = new HandlerThread("signal-DeadlockDetector", ThreadUtil.PRIORITY_BACKGROUND_THREAD);
    handlerThread.start();
    return new DeadlockDetector(new Handler(handlerThread.getLooper()), TimeUnit.SECONDS.toMillis(5));
  }

  @Override
  public @NonNull ClientZkReceiptOperations provideClientZkReceiptOperations(@NonNull REDServiceConfiguration signalServiceConfiguration) {
    return provideClientZkOperations(signalServiceConfiguration).getReceiptOperations();
  }

  @Override
  public @NonNull OkHttpClient provideOkHttpClient() {
    return new OkHttpClient.Builder()
        .addInterceptor(new StandardUserAgentInterceptor())
        .dns(REDServiceNetworkAccess.DNS)
        .build();
  }

  @Override
  public @NonNull BillingApi provideBillingApi() {
    return BillingFactory.create(GooglePlayBillingDependencies.INSTANCE, Environment.Backups.supportsGooglePlayBilling());
  }

  @Override
  public @NonNull ArchiveApi provideArchiveApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket, @NonNull PushServiceSocket pushServiceSocket, @NonNull REDServiceConfiguration signalServiceConfiguration) {
    try {
      return new ArchiveApi(authWebSocket, unauthWebSocket, pushServiceSocket, new GenericServerPublicParams(signalServiceConfiguration.getBackupServerPublicParams()));
    } catch (InvalidInputException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public @NonNull KeysApi provideKeysApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new KeysApi(authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull AttachmentApi provideAttachmentApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new AttachmentApi(authWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull LinkDeviceApi provideLinkDeviceApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new LinkDeviceApi(authWebSocket);
  }

  @Override
  public @NonNull RegistrationApi provideRegistrationApi(@NonNull PushServiceSocket pushServiceSocket) {
    return new RegistrationApi(pushServiceSocket);
  }

  @Override
  public @NonNull RegistrationApiV2 provideRegistrationApiV2(@NonNull REDRestClient signalRestClient) {
    return new RegistrationApiV2(signalRestClient);
  }

  @Override
  public @NonNull StorageServiceApi provideStorageServiceApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new StorageServiceApi(authWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull AccountApi provideAccountApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new AccountApi(authWebSocket);
  }

  @Override
  public @NonNull UsernameApi provideUsernameApi(@NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new UsernameApi(unauthWebSocket);
  }

  @Override
  public @NonNull CallingApi provideCallingApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new CallingApi(authWebSocket, unauthWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull PaymentsApi providePaymentsApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new PaymentsApi(authWebSocket);
  }

  @Override
  public @NonNull CdsApi provideCdsApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new CdsApi(authWebSocket);
  }

  @Override
  public @NonNull RateLimitChallengeApi provideRateLimitChallengeApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new RateLimitChallengeApi(authWebSocket);
  }

  @Override
  public @NonNull MessageApi provideMessageApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new MessageApi(authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull ProvisioningApi provideProvisioningApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new ProvisioningApi(authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull CertificateApi provideCertificateApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket) {
    return new CertificateApi(authWebSocket);
  }

  @Override
  public @NonNull ProfileApi provideProfileApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket, @NonNull PushServiceSocket pushServiceSocket, @NonNull ClientZkProfileOperations clientZkProfileOperations) {
    return new ProfileApi(authWebSocket, unauthWebSocket, pushServiceSocket, clientZkProfileOperations);
  }

  @Override
  public @NonNull RemoteConfigApi provideRemoteConfigApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull PushServiceSocket pushServiceSocket) {
    return new RemoteConfigApi(authWebSocket, pushServiceSocket);
  }

  @Override
  public @NonNull DonationsApi provideDonationsApi(@NonNull REDWebSocket.AuthenticatedWebSocket authWebSocket, @NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new DonationsApi(authWebSocket, unauthWebSocket);
  }

  @Override
  public @NonNull SvrBApi provideSvrBApi(@NotNull Network libREDNetwork) {
    return new SvrBApi(libREDNetwork);
  }

  @Override
  public @NonNull KeyTransparencyApi provideKeyTransparencyApi(@NonNull REDWebSocket.UnauthenticatedWebSocket unauthWebSocket) {
    return new KeyTransparencyApi(unauthWebSocket);
  }

  @Override public @NotNull BlobProvider provideBlobs() {
    return new BlobProvider(context, AppAttachmentSecretStore.INSTANCE);
  }

  @VisibleForTesting
  static class DynamicCredentialsProvider implements CredentialsProvider {

    @Override
    public ACI getAci() {
      return REDStore.account().getAci();
    }

    @Override
    public PNI getPni() {
      return REDStore.account().getPni();
    }

    @Override
    public String getE164() {
      return REDStore.account().getE164();
    }

    @Override
    public String getPassword() {
      return REDStore.account().getServicePassword();
    }

    @Override
    public int getDeviceId() {
      return REDStore.account().getDeviceId();
    }
  }
}
