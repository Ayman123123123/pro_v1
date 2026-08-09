/*
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.red.sovereign;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.bumptech.glide.Glide;
import com.google.android.gms.security.ProviderInstaller;

import net.zetetic.database.Logger;

import org.conscrypt.ConscryptRED;
import org.greenrobot.eventbus.EventBus;
import org.signal.aesgcmprovider.AesGcmProvider;
import org.signal.core.util.AppForegroundObserver;
import org.signal.core.util.DiskUtil;
import org.signal.core.util.MemoryTracker;
import org.signal.core.util.Util;
import org.signal.core.util.concurrent.AnrDetector;
import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.crypto.AttachmentSecretProvider;
import org.signal.core.util.logging.AndroidLogger;
import org.signal.core.util.logging.Log;
import org.signal.core.util.logging.Scrubber;
import org.signal.core.util.tracing.Tracer;
import org.signal.glide.REDGlideCodecs;
import org.signal.libsignal.net.ChatServiceException;
import org.signal.libsignal.protocol.logging.REDProtocolLoggerProvider;
import org.signal.registration.RegistrationDependencies;
import org.signal.ringrtc.CallManager;
import com.red.sovereign.apkupdate.ApkUpdateRefreshListener;
import com.red.sovereign.avatar.AvatarPickerStorage;
import com.red.sovereign.backup.v2.BackupRepository;
import com.red.sovereign.clockskew.ClockSkewDetector;
import com.red.sovereign.preferences.EditProxyActivity;
import com.red.sovereign.conversation.drafts.DraftBlobs;
import com.red.sovereign.crypto.AppAttachmentSecretStore;
import com.red.sovereign.crypto.DatabaseSecretProvider;
import com.red.sovereign.database.LogDatabase;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.SqlCipherLibraryLoader;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.dependencies.ApplicationDependencyProvider;
import com.red.sovereign.emoji.EmojiSource;
import com.red.sovereign.emoji.JumboEmoji;
import com.red.sovereign.gcm.FcmFetchManager;
import com.red.sovereign.glide.REDGlideComponents;
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint;
import com.red.sovereign.jobs.AccountConsistencyWorkerJob;
import com.red.sovereign.jobs.BackupRefreshJob;
import com.red.sovereign.jobs.BackupSubscriptionCheckJob;
import com.red.sovereign.jobs.BuildExpirationConfirmationJob;
import com.red.sovereign.jobs.CallingAssetsDownloadJob;
import com.red.sovereign.jobs.CheckKeyTransparencyJob;
import com.red.sovereign.jobs.CheckServiceReachabilityJob;
import com.red.sovereign.jobs.DownloadLatestEmojiDataJob;
import com.red.sovereign.jobs.EmojiSearchIndexDownloadJob;
import com.red.sovereign.jobs.FcmRefreshJob;
import com.red.sovereign.jobs.FontDownloaderJob;
import com.red.sovereign.jobs.GroupRingCleanupJob;
import com.red.sovereign.jobs.GroupV2UpdateSelfProfileKeyJob;
import com.red.sovereign.jobs.InAppPaymentAuthCheckJob;
import com.red.sovereign.jobs.InAppPaymentKeepAliveJob;
import com.red.sovereign.jobs.LinkedDeviceInactiveCheckJob;
import com.red.sovereign.jobs.MessageSendLogCleanupJob;
import com.red.sovereign.jobs.MultiDeviceContactUpdateJob;
import com.red.sovereign.jobs.PreKeysSyncJob;
import com.red.sovereign.jobs.ProfileUploadJob;
import com.red.sovereign.jobs.RefreshAttributesJob;
import com.red.sovereign.jobs.RefreshSvrCredentialsJob;
import com.red.sovereign.jobs.RestoreOptimizedMediaJob;
import com.red.sovereign.jobs.RetrieveProfileJob;
import com.red.sovereign.jobs.RetrieveRemoteAnnouncementsJob;
import com.red.sovereign.jobs.StoryOnboardingDownloadJob;
import com.red.sovereign.keyvalue.KeepMessagesDuration;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.logging.CustomREDProtocolLogger;
import com.red.sovereign.logging.PersistentLogger;
import com.red.sovereign.logsubmit.SubmitDebugLogActivity;
import com.red.sovereign.messageprocessingalarm.RoutineMessageFetchReceiver;
import com.red.sovereign.messages.IncomingMessageObserver;
import com.red.sovereign.migrations.ApplicationMigrations;
import com.red.sovereign.mms.REDGlideModule;
import com.red.sovereign.ratelimit.RateLimitUtil;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.registration.util.RegistrationUtil;
import com.red.sovereign.registration.v2.AppContactSupportController;
import com.red.sovereign.registration.v2.AppRegistrationNetworkController;
import com.red.sovereign.registration.v2.AppRegistrationStorageController;
import com.red.sovereign.ringrtc.RingRtcLogger;
import com.red.sovereign.service.AnalyzeDatabaseAlarmListener;
import com.red.sovereign.service.DirectoryRefreshListener;
import com.red.sovereign.service.KeyCachingService;
import com.red.sovereign.service.LocalBackupListener;
import com.red.sovereign.service.MessageBackupListener;
import com.red.sovereign.service.RotateSenderCertificateListener;
import com.red.sovereign.service.RotateSignedPreKeyListener;
import com.red.sovereign.service.webrtc.ActiveCallManager;
import com.red.sovereign.service.webrtc.AndroidTelecomUtil;
import com.red.sovereign.storage.StorageSyncHelper;
import com.red.sovereign.util.AppStartup;
import com.red.sovereign.util.BatterySnapshotTracker;
import com.red.sovereign.util.DeviceProperties;
import com.red.sovereign.util.DynamicTheme;
import com.red.sovereign.util.Environment;
import org.signal.core.util.PlayServicesUtil;
import com.red.sovereign.util.RemoteConfig;
import com.red.sovereign.util.REDLocalMetrics;
import com.red.sovereign.util.REDUncaughtExceptionHandler;
import com.red.sovereign.util.SqlCipherLogTarget;
import com.red.sovereign.util.TextSecurePreferences;
import com.red.sovereign.util.VersionTracker;
import com.red.sovereign.util.dynamiclanguage.DynamicLanguageContextWrapper;
import org.whispersystems.signalservice.api.websocket.REDWebSocket;

import java.io.InterruptedIOException;
import java.net.SocketException;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.exceptions.OnErrorNotImplementedException;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;
import rxdogtag2.RxDogTag;

/**
 * Will be called once when the TextSecure process is created.
 * <p>
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends Application implements AppForegroundObserver.Listener {

  private static final String TAG = Log.tag(ApplicationContext.class);

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext) context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    Tracer.getInstance().start("Application#onCreate()");
    AppStartup.getInstance().onApplicationCreate();
    REDLocalMetrics.ColdStart.start();

    // RED Master Initialization
    com.red.sovereign.developed.REDCore.INSTANCE.initializeEverything();

    long startTime = System.currentTimeMillis();

    super.onCreate();

    AppStartup.getInstance().addBlocking("sqlcipher-init", () -> {
                SqlCipherLibraryLoader.load();
                REDDatabase.init(this,
                                    DatabaseSecretProvider.getOrCreateDatabaseSecret(this),
                                    AttachmentSecretProvider.getInstance(this, AppAttachmentSecretStore.INSTANCE).getOrCreateAttachmentSecret());
                Logger.setTarget(SqlCipherLogTarget.INSTANCE);
              })
              .addBlocking("signal-store", () -> REDStore.init(this))
              .addBlocking("logging", () -> {
                initializeLogging();
                Log.i(TAG, "onCreate()");
              })
              .addBlocking("security-provider", this::initializeSecurityProvider)
              .addBlocking("app-dependencies", this::initializeAppDependencies)
              .addBlocking("anr-detector", this::startAnrDetector)
              .addBlocking("crash-handling", this::initializeCrashHandling)
              .addBlocking("rx-init", this::initializeRx)
              .addBlocking("event-bus", () -> EventBus.builder().logNoSubscriberMessages(false).installDefaultEventBus())
              .addBlocking("scrubber", () -> Scrubber.setIdentifierHmacKeyProvider(() -> REDStore.svr().getMasterKey().deriveLoggingKey()))
              .addBlocking("first-launch", this::initializeFirstEverAppLaunch)
              .addBlocking("app-migrations", this::initializeApplicationMigrations)
              .addBlocking("lifecycle-observer", () -> AppForegroundObserver.addListener(this))
              .addBlocking("message-retriever", this::initializeMessageRetrieval)
              .addBlocking("dynamic-theme", () -> DynamicTheme.setDefaultDayNightMode(this))
              .addBlocking("proxy-init", () -> {
                if (REDStore.proxy().isProxyEnabled()) {
                  Log.w(TAG, "Proxy detected. Enabling Conscrypt.setUseEngineSocketByDefault()");
                  ConscryptRED.setUseEngineSocketByDefault(true);
                }
              })
              .addBlocking("blob-provider", this::initializeBlobProvider)
              .addBlocking("remote-config", RemoteConfig::init)
              .addBlocking("ring-rtc", this::initializeRingRtc)
              .addBlocking("glide", () -> REDGlideModule.setRegisterGlideComponents(new REDGlideComponents()))
              .addBlocking("tracer", this::initializeTracer)
              .addNonBlocking(() -> RegistrationUtil.maybeMarkRegistrationComplete())
              .addNonBlocking(() -> Glide.get(this))
              .addNonBlocking(this::cleanAvatarStorage)
              .addNonBlocking(this::initializeRevealableMessageManager)
              .addNonBlocking(this::initializePendingRetryReceiptManager)
              .addNonBlocking(this::initializeScheduledMessageManager)
              .addNonBlocking(this::initializeFcmCheck)
              .addNonBlocking(PreKeysSyncJob::enqueueIfNeeded)
              .addNonBlocking(this::initializePeriodicTasks)
              .addNonBlocking(this::initializeCircumvention)
              .addNonBlocking(this::initializeCleanup)
              .addNonBlocking(this::initializeGlideCodecs)
              .addNonBlocking(SealedSenderConstraint::checkAndSetValidity)
              .addNonBlocking(StorageSyncHelper::scheduleRoutineSync)
              .addNonBlocking(this::beginJobLoop)
              .addNonBlocking(EmojiSource::refresh)
              .addNonBlocking(() -> AppDependencies.getGiphyMp4Cache().onAppStart(this))
              .addNonBlocking(AppDependencies::getBillingApi)
              .addNonBlocking(this::ensureProfileUploaded)
              .addNonBlocking(() -> AppDependencies.getExpireStoriesManager().scheduleIfNecessary())
              .addNonBlocking(BackupRepository::maybeFixAnyDanglingUploadProgress)
              .addPostRender(() -> AppDependencies.getDeletedCallEventManager().scheduleIfNecessary())
              .addPostRender(() -> RateLimitUtil.retryAllRateLimitedMessages(this))
              .addPostRender(this::initializeExpiringMessageManager)
              .addPostRender(this::initializeTrimThreadsByDateManager)
              .addPostRender(RefreshSvrCredentialsJob::enqueueIfNecessary)
              .addPostRender(() -> DownloadLatestEmojiDataJob.scheduleIfNecessary(this))
              .addPostRender(EmojiSearchIndexDownloadJob::scheduleIfNecessary)
              .addPostRender(MessageSendLogCleanupJob::enqueue)
              .addPostRender(() -> JumboEmoji.updateCurrentVersion(this))
              .addPostRender(RetrieveRemoteAnnouncementsJob::enqueue)
              .addPostRender(AndroidTelecomUtil::registerPhoneAccount)
              .addPostRender(() -> AppDependencies.getJobManager().add(new FontDownloaderJob()))
              .addPostRender(() -> AppDependencies.getJobManager().add(new CallingAssetsDownloadJob()))
              .addPostRender(CheckServiceReachabilityJob::enqueueIfNecessary)
              .addPostRender(GroupV2UpdateSelfProfileKeyJob::enqueueForGroupsIfNecessary)
              .addPostRender(StoryOnboardingDownloadJob.Companion::enqueueIfNeeded)
              .addPostRender(() -> AppDependencies.getExoPlayerPool().getPoolStats().getMaxUnreserved())
              .addPostRender(() -> AppDependencies.getRecipientCache().warmUp())
              .addPostRender(AccountConsistencyWorkerJob::enqueueIfNecessary)
              .addPostRender(GroupRingCleanupJob::enqueue)
              .addPostRender(LinkedDeviceInactiveCheckJob::enqueueIfNecessary)
              .addPostRender(() -> ActiveCallManager.clearNotifications(this))
              .addPostRender(RestoreOptimizedMediaJob::enqueueIfNecessary)
              .addPostRender(() -> AppDependencies.getPinnedMessageManager().scheduleIfNecessary())
              .execute();

    Log.d(TAG, "onCreate() took " + (System.currentTimeMillis() - startTime) + " ms");
    REDLocalMetrics.ColdStart.onApplicationCreateFinished();
    Tracer.getInstance().end("Application#onCreate()");
  }

  @Override
  public void onForeground() {
    long startTime = System.currentTimeMillis();
    Log.i(TAG, "App is now visible. Battery: " + DeviceProperties.getBatteryLevel(this) + "% (charging: " + DeviceProperties.isCharging(this) + ")");

    BatterySnapshotTracker.emit(this, "foreground");

    AppDependencies.getFrameRateTracker().start();
    AppDependencies.getMegaphoneRepository().onAppForegrounded();
    AppDependencies.getDeadlockDetector().start();
    InAppPaymentKeepAliveJob.enqueueAndTrackTimeIfNecessary();
    FcmFetchManager.onForeground(this);
    startAnrDetector();

    REDExecutors.BOUNDED.execute(() -> {
      BackupRefreshJob.enqueueIfNecessary();
      InAppPaymentAuthCheckJob.enqueueIfNeeded();
      RemoteConfig.refreshIfNecessary();
      RetrieveProfileJob.enqueueRoutineFetchIfNecessary();
      executePendingContactSync();
      KeyCachingService.onAppForegrounded(this);
      AppDependencies.getShakeToReport().enable();
      checkBuildExpiration();
      checkFreeDiskSpace();
      MemoryTracker.start();
      BackupSubscriptionCheckJob.enqueueIfAble();
      CheckKeyTransparencyJob.enqueueIfNecessary(true, false);
      AppDependencies.getAuthWebSocket().registerKeepAliveToken(REDWebSocket.FOREGROUND_KEEPALIVE);
      AppDependencies.getUnauthWebSocket().registerKeepAliveToken(REDWebSocket.FOREGROUND_KEEPALIVE);

      long lastForegroundTime = REDStore.misc().getLastForegroundTime();
      long currentTime        = System.currentTimeMillis();
      long timeDiff           = currentTime - lastForegroundTime;

      if (timeDiff < 0) {
        Log.w(TAG, "Time travel! The system clock has moved backwards. (currentTime: " + currentTime + " ms, lastForegroundTime: " + lastForegroundTime + " ms, diff: " + timeDiff + " ms)", true);
      }

      REDStore.misc().setLastForegroundTime(currentTime);
    });

    Log.d(TAG, "onStart() took " + (System.currentTimeMillis() - startTime) + " ms");
  }

  @Override
  public void onBackground() {
    Log.i(TAG, "App is no longer visible.");
    BatterySnapshotTracker.emit(this, "background");
    KeyCachingService.onAppBackgrounded(this);
    AppDependencies.getMessageNotifier().clearVisibleThread();
    AppDependencies.getFrameRateTracker().stop();
    AppDependencies.getShakeToReport().disable();
    AppDependencies.getDeadlockDetector().stop();
    AppDependencies.getAuthWebSocket().removeKeepAliveToken(REDWebSocket.FOREGROUND_KEEPALIVE);
    AppDependencies.getUnauthWebSocket().removeKeepAliveToken(REDWebSocket.FOREGROUND_KEEPALIVE);
    MemoryTracker.stop();
    AnrDetector.stop();
  }

  public void checkBuildExpiration() {
    if (Util.getTimeUntilBuildExpiry(REDStore.misc().getEstimatedServerTime()) <= 0 && !REDStore.misc().isClientDeprecated()) {
      Log.w(TAG, "Build potentially expired! Enqueing job to check.", true);
      AppDependencies.getJobManager().add(new BuildExpirationConfirmationJob());
    }
  }

  public void checkFreeDiskSpace() {
    long availableBytes = DiskUtil.getAvailableSpace(getApplicationContext()).getBytes();
    REDStore.backup().setSpaceAvailableOnDiskBytes(availableBytes);
  }

  /**
   * Note: this is purposefully "started" twice -- once during application create, and once during foreground.
   * This is so we can capture ANR's that happen on boot before the foreground event.
   */
  private void startAnrDetector() {
    AnrDetector.start(TimeUnit.SECONDS.toMillis(5), () -> RemoteConfig.internalUser() && REDStore.internal().getAnrDetectionCrashes(), (dumps) -> {
      LogDatabase.getInstance(this).anrs().save(System.currentTimeMillis(), dumps);
      return Unit.INSTANCE;
    });
  }

  private void initializeSecurityProvider() {
    int aesPosition = Security.insertProviderAt(new AesGcmProvider(), 1);
    Log.i(TAG, "Installed AesGcmProvider: " + aesPosition);

    if (aesPosition < 0) {
      Log.e(TAG, "Failed to install AesGcmProvider()");
      throw new ProviderInitializationException();
    }

    int conscryptPosition = Security.insertProviderAt(ConscryptRED.newProvider(), 2);
    Log.i(TAG, "Installed Conscrypt provider: " + conscryptPosition);

    if (conscryptPosition < 0) {
      Log.w(TAG, "Did not install Conscrypt provider. May already be present.");
    }
  }

  @VisibleForTesting
  protected void initializeLogging() {
    Log.initialize(RemoteConfig::internalUser, AndroidLogger.INSTANCE, PersistentLogger.getInstance(this));

    REDProtocolLoggerProvider.setProvider(new CustomREDProtocolLogger());
    REDProtocolLoggerProvider.initializeLogging(BuildConfig.LIBSIGNAL_LOG_LEVEL);

    REDExecutors.UNBOUNDED.execute(() -> {
      Log.blockUntilAllWritesFinished();
      LogDatabase.getInstance(this).logs().trimToSize();
      LogDatabase.getInstance(this).crashes().trimToSize();
    });
  }

  private void initializeCrashHandling() {
    final Thread.UncaughtExceptionHandler originalHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new REDUncaughtExceptionHandler(originalHandler));
  }

  private void initializeRx() {
    RxDogTag.install();
    RxJavaPlugins.setInitIoSchedulerHandler(schedulerSupplier -> Schedulers.from(REDExecutors.UNBOUNDED, true, false));
    RxJavaPlugins.setInitComputationSchedulerHandler(schedulerSupplier -> Schedulers.from(REDExecutors.BOUNDED, true, false));
    RxJavaPlugins.setErrorHandler(e -> {
      boolean wasWrapped = false;
      while ((e instanceof UndeliverableException || e instanceof AssertionError || e instanceof OnErrorNotImplementedException) && e.getCause() != null) {
        wasWrapped = true;
        e = e.getCause();
      }

      if (wasWrapped && (e instanceof SocketException || e instanceof InterruptedException || e instanceof InterruptedIOException || e instanceof ChatServiceException)) {
        return;
      }

      Log.e(TAG, "RxJava error handler invoked", e);

      Thread.UncaughtExceptionHandler uncaughtExceptionHandler = Thread.currentThread().getUncaughtExceptionHandler();
      if (uncaughtExceptionHandler == null) {
        uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
      }

      uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), e);
    });
  }

  private void initializeApplicationMigrations() {
    ApplicationMigrations.onApplicationCreate(this, AppDependencies.getJobManager());
  }

  public void initializeMessageRetrieval() {
    REDExecutors.UNBOUNDED.execute(AppDependencies::startNetwork);
  }

  @VisibleForTesting
  void initializeAppDependencies() {
    if (!AppDependencies.isInitialized()) {
      Log.i(TAG, "Initializing AppDependencies.");
      AppDependencies.init(this, new ApplicationDependencyProvider(this));
    }
    AppForegroundObserver.begin();
    ClockSkewDetector.beginObserving(this);

    if (Environment.USE_NEW_REGISTRATION) {
      initializeRegistrationDependencies();
    }
  }

  private void initializeRegistrationDependencies() {
    RegistrationDependencies.provide(
      new RegistrationDependencies(
        new AppRegistrationNetworkController(this, AppDependencies.getRegistrationApiV2()),
        new AppRegistrationStorageController(this),
        Environment.IS_LINK_AND_SYNC_AVAILABLE,
        null,
        context -> {
          context.startActivity(new Intent(context, SubmitDebugLogActivity.class));
          return Unit.INSTANCE;
        },
        context -> {
          context.startActivity(EditProxyActivity.intent(context));
          return Unit.INSTANCE;
        },
        new AppContactSupportController()
      )
    );
  }

  private void initializeFirstEverAppLaunch() {
    if (TextSecurePreferences.getFirstInstallVersion(this) == -1) {
      if (!REDDatabase.databaseFileExists(this) || VersionTracker.getDaysSinceFirstInstalled(this) < 365) {
        Log.i(TAG, "First ever app launch!");
        AppInitialization.onFirstEverAppLaunch(this);
      }

      Log.i(TAG, "Setting first install version to " + BuildConfig.CANONICAL_VERSION_CODE);
      TextSecurePreferences.setFirstInstallVersion(this, BuildConfig.CANONICAL_VERSION_CODE);
    } else if (!REDStore.settings().getPassphraseDisabled() && VersionTracker.getDaysSinceFirstInstalled(this) < 90) {
      Log.i(TAG, "Detected a new install that doesn't have passphrases disabled -- assuming bad initialization.");
      AppInitialization.onRepairFirstEverAppLaunch(this);
    } else if (!REDStore.settings().getPassphraseDisabled() && VersionTracker.getDaysSinceFirstInstalled(this) < 912) {
      Log.i(TAG, "Detected a not-recent install that doesn't have passphrases disabled -- disabling now.");
      REDStore.settings().setPassphraseDisabled(true);
    }
  }

  private void initializeFcmCheck() {
    if (!REDStore.account().isRegistered()) {
      return;
    }

    PlayServicesUtil.PlayServicesStatus playServicesStatus = PlayServicesUtil.getPlayServicesStatus(this);

    if (playServicesStatus == PlayServicesUtil.PlayServicesStatus.SUCCESS && !REDStore.account().isFcmEnabled()) {
      Log.w(TAG, "Play Services are newly-available. Enabling FCM and updating server.");
      REDStore.account().setFcmEnabled(true);
      AppDependencies.getJobManager().startChain(new FcmRefreshJob())
                                      .then(new RefreshAttributesJob())
                                      .enqueue();
      AppDependencies.resetNetwork();
      AppDependencies.startNetwork();
      IncomingMessageObserver.stopForegroundService(this);
    } else if (playServicesStatus == PlayServicesUtil.PlayServicesStatus.MISSING && REDStore.account().isFcmEnabled()) {
      Log.w(TAG, "Play Services are no longer available. Attempting to get an FCM token anyway.");
      AppDependencies.getJobManager().add(new FcmRefreshJob());
    } else if (playServicesStatus == PlayServicesUtil.PlayServicesStatus.MISSING && (System.currentTimeMillis() - REDStore.misc().getLastMissingPlayServicesFcmVerificationTime()) > TimeUnit.DAYS.toMillis(3)) {
      Log.i(TAG, "Play Services are unavailable, but it's been long enough that we should check and see if we can get an FCM token anyway.");
      AppDependencies.getJobManager().add(new FcmRefreshJob());
    } else if (REDStore.account().isFcmEnabled()) {
      long lastSetTime = REDStore.account().getFcmTokenLastSetTime();
      long nextSetTime = lastSetTime + TimeUnit.HOURS.toMillis(6);
      long now         = System.currentTimeMillis();

      if (REDStore.account().getFcmToken() == null || nextSetTime <= now || lastSetTime > now) {
        Log.i(TAG, "Time for routine FCM token refresh.");
        AppDependencies.getJobManager().add(new FcmRefreshJob());
      }
    } else {
      Log.d(TAG, "Play Services status: " + playServicesStatus + ", fcmEnabled: false. Skipping FCM check.");
    }
  }

  private void initializeExpiringMessageManager() {
    AppDependencies.getExpiringMessageManager().checkSchedule();
  }

  private void initializeRevealableMessageManager() {
    AppDependencies.getViewOnceMessageManager().scheduleIfNecessary();
  }

  private void initializePendingRetryReceiptManager() {
    AppDependencies.getPendingRetryReceiptManager().scheduleIfNecessary();
  }

  private void initializeScheduledMessageManager() {
    AppDependencies.getScheduledMessageManager().scheduleIfNecessary();
  }

  private void initializeTrimThreadsByDateManager() {
    KeepMessagesDuration keepMessagesDuration = REDStore.settings().getKeepMessagesDuration();
    if (keepMessagesDuration != KeepMessagesDuration.FOREVER) {
      AppDependencies.getTrimThreadsByDateManager().scheduleIfNecessary();
    }
  }

  private void initializeTracer() {
    if (RemoteConfig.internalUser()) {
      Tracer.getInstance().setMaxBufferSize(35_000);
    }
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);
    LocalBackupListener.schedule(this);
    MessageBackupListener.schedule(this);
    RotateSenderCertificateListener.schedule(this);
    RoutineMessageFetchReceiver.startOrUpdateAlarm(this);
    AnalyzeDatabaseAlarmListener.schedule(this);

    if (BuildConfig.MANAGES_APP_UPDATES) {
      ApkUpdateRefreshListener.schedule(this);
    }
  }

  private void initializeRingRtc() {
    try {
      Map<String, String> fieldTrials = new HashMap<>();
      if (RemoteConfig.callingFieldTrialAnyAddressPortsKillSwitch()) {
        fieldTrials.put("RingRTC-AnyAddressPortsKillSwitch", "Enabled");
      }
      CallManager.initialize(this, new RingRtcLogger(), fieldTrials);
    } catch (UnsatisfiedLinkError e) {
      throw new AssertionError("Unable to load ringrtc library", e);
    }
  }

  @WorkerThread
  private void initializeCircumvention() {
    if (AppDependencies.getREDServiceNetworkAccess().isCensored()) {
      try {
        ProviderInstaller.installIfNeeded(ApplicationContext.this);
      } catch (Throwable t) {
        Log.w(TAG, t);
      }
    }
  }

  private void ensureProfileUploaded() {
    if (REDStore.account().isRegistered() && !REDStore.registration().hasUploadedProfile() && !Recipient.self().getProfileName().isEmpty() && REDStore.account().isPrimaryDevice()) {
      Log.w(TAG, "User has a profile, but has not uploaded one. Uploading now.");
      AppDependencies.getJobManager().add(new ProfileUploadJob());
    }
  }

  private void executePendingContactSync() {
    if (TextSecurePreferences.needsFullContactSync(this)) {
      AppDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(true));
    }
  }

  @VisibleForTesting
  protected void beginJobLoop() {
    AppDependencies.getJobManager().beginJobLoop();
  }

  @WorkerThread
  private void initializeBlobProvider() {
    AppDependencies.getBlobs().initialize(this, DraftBlobs.INSTANCE::deleteOrphanedDraftFiles);
  }

  @WorkerThread
  private void cleanAvatarStorage() {
    AvatarPickerStorage.cleanOrphans(this);
  }

  @WorkerThread
  private void initializeCleanup() {
    int deleted = REDDatabase.attachments().deleteAbandonedPreuploadedAttachments();
    Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");
  }

  private void initializeGlideCodecs() {
    REDGlideCodecs.setLogProvider(new org.signal.glide.Log.Provider() {
      @Override
      public void v(@NonNull String tag, @NonNull String message) {
        Log.v(tag, message);
      }

      @Override
      public void d(@NonNull String tag, @NonNull String message) {
        Log.d(tag, message);
      }

      @Override
      public void i(@NonNull String tag, @NonNull String message) {
        Log.i(tag, message);
      }

      @Override
      public void w(@NonNull String tag, @NonNull String message) {
        Log.w(tag, message);
      }

      @Override
      public void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        Log.e(tag, message, throwable);
      }
    });
  }

  @Override
  protected void attachBaseContext(Context base) {
    DynamicLanguageContextWrapper.updateContext(base);
    super.attachBaseContext(base);
  }

  private static class ProviderInitializationException extends RuntimeException {
  }
}
