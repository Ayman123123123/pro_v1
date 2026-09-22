package com.red.sovereign.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;
import com.red.sovereign.storage.StorageSyncHelper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Marks all distribution lists as needing to be synced with storage service.
 */
public final class SyncDistributionListsMigrationJob extends MigrationJob {

  public static final String KEY = "SyncDistributionListsMigrationJob";

  private static final String TAG = Log.tag(SyncDistributionListsMigrationJob.class);

  SyncDistributionListsMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private SyncDistributionListsMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    if (REDStore.account().getAci() == null) {
      Log.w(TAG, "Self not yet available.");
      return;
    }

    List<RecipientId> listRecipients = REDDatabase.distributionLists()
                                                     .getAllListRecipients()
                                                     .stream()
                                                     .filter(id -> {
                                                       try {
                                                         Recipient.resolved(id);
                                                         return true;
                                                       } catch (Exception e) {
                                                         Log.e(TAG, "Unable to resolve distribution list recipient: " + id, e);
                                                         return false;
                                                       }
                                                     })
                                                     .collect(Collectors.toList());

    REDDatabase.recipients().markNeedsSync(listRecipients);
    StorageSyncHelper.scheduleSyncForDataChange();
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<SyncDistributionListsMigrationJob> {
    @Override
    public @NonNull SyncDistributionListsMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new SyncDistributionListsMigrationJob(parameters);
    }
  }
}
