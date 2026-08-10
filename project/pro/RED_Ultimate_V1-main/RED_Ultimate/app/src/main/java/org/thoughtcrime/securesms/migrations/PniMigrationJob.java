package com.red.sovereign.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.jobmanager.impl.NetworkConstraint;
import com.red.sovereign.keyvalue.REDStore;
import org.signal.core.models.ServiceId.PNI;

import java.io.IOException;

/**
 * Migration to fetch our own PNI from the service.
 */
public class PniMigrationJob extends MigrationJob {

  public static final String KEY = "PniMigrationJob";

  private static final String TAG = Log.tag(PniMigrationJob.class);

  PniMigrationJob() {
    this(new Parameters.Builder().addConstraint(NetworkConstraint.KEY).build());
  }

  private PniMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  boolean isUiBlocking() {
    return false;
  }

  @Override
  void performMigration() throws Exception {
    if (!REDStore.account().isRegistered() || REDStore.account().getAci() == null) {
      Log.w(TAG, "Not registered! Skipping migration, as it wouldn't do anything.");
      return;
    }

    PNI pni = PNI.parseOrNull(AppDependencies.getREDServiceAccountManager().getWhoAmI().getPni());

    if (pni == null) {
      throw new IOException("Invalid PNI!");
    }

    REDDatabase.recipients().linkIdsForSelf(REDStore.account().requireAci(), pni, REDStore.account().requireE164());
    REDStore.account().setPni(pni);
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  public static class Factory implements Job.Factory<PniMigrationJob> {
    @Override
    public @NonNull PniMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new PniMigrationJob(parameters);
    }
  }
}
