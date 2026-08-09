package com.red.sovereign;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.JobManager;
import com.red.sovereign.jobs.DeleteAbandonedAttachmentsJob;
import com.red.sovereign.jobs.EmojiSearchIndexDownloadJob;
import com.red.sovereign.jobs.QuoteThumbnailBackfillJob;
import com.red.sovereign.jobs.StickerPackDownloadJob;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.migrations.ApplicationMigrations;
import com.red.sovereign.migrations.QuoteThumbnailBackfillMigrationJob;
import com.red.sovereign.stickers.BlessedPacks;
import com.red.sovereign.util.TextSecurePreferences;
import org.signal.core.util.Util;

/**
 * Rule of thumb: if there's something you want to do on the first app launch that involves
 * persisting state to the database, you'll almost certainly *also* want to do it post backup
 * restore, since a backup restore will wipe the current state of the database.
 */
public final class AppInitialization {

  private static final String TAG = Log.tag(AppInitialization.class);

  private AppInitialization() {}

  public static void onFirstEverAppLaunch(@NonNull Context context) {
    Log.i(TAG, "onFirstEverAppLaunch()");

    TextSecurePreferences.setAppMigrationVersion(context, ApplicationMigrations.CURRENT_VERSION);
    TextSecurePreferences.setJobManagerVersion(context, JobManager.CURRENT_VERSION);
    TextSecurePreferences.setLastVersionCode(context, BuildConfig.VERSION_CODE);
    TextSecurePreferences.setHasSeenStickerIntroTooltip(context, true);
    REDStore.settings().setPassphraseDisabled(true);
    TextSecurePreferences.setReadReceiptsEnabled(context, true);
    TextSecurePreferences.setTypingIndicatorsEnabled(context, true);
    AppDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
    REDStore.onFirstEverAppLaunch();
    AppDependencies.getJobManager().addAll(BlessedPacks.getFirstInstallJobs());
  }

  public static void onPostBackupRestore(@NonNull Context context) {
    Log.i(TAG, "onPostBackupRestore()");

    AppDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
    REDStore.onPostBackupRestore();
    REDStore.onFirstEverAppLaunch();
    REDStore.onboarding().clearAll();
    REDStore.settings().setPassphraseDisabled(true);
    REDStore.notificationProfile().setHasSeenTooltip(true);
    TextSecurePreferences.onPostBackupRestore(context);
    REDStore.settings().setPassphraseDisabled(true);
    AppDependencies.getJobManager().addAll(BlessedPacks.getFirstInstallJobs());
    EmojiSearchIndexDownloadJob.scheduleImmediately();
    DeleteAbandonedAttachmentsJob.enqueue();

    if (REDStore.misc().startedQuoteThumbnailMigration()) {
      AppDependencies.getJobManager().add(new QuoteThumbnailBackfillJob());
    } else {
      AppDependencies.getJobManager().add(new QuoteThumbnailBackfillMigrationJob());
    }
  }

  /**
   * Temporary migration method that does the safest bits of {@link #onFirstEverAppLaunch(Context)}
   */
  public static void onRepairFirstEverAppLaunch(@NonNull Context context) {
    Log.w(TAG, "onRepairFirstEverAppLaunch()");

    TextSecurePreferences.setAppMigrationVersion(context, ApplicationMigrations.CURRENT_VERSION);
    TextSecurePreferences.setJobManagerVersion(context, JobManager.CURRENT_VERSION);
    TextSecurePreferences.setLastVersionCode(context, BuildConfig.VERSION_CODE);
    TextSecurePreferences.setHasSeenStickerIntroTooltip(context, true);
    REDStore.settings().setPassphraseDisabled(true);
    AppDependencies.getMegaphoneRepository().onFirstEverAppLaunch();
    REDStore.onFirstEverAppLaunch();
    AppDependencies.getJobManager().addAll(BlessedPacks.getFirstInstallJobs());
  }
}
