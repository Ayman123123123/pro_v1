package com.red.sovereign.jobs;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.jobmanager.impl.NetworkConstraint;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.util.ProfileUtil;

import java.util.concurrent.TimeUnit;

public final class ProfileUploadJob extends BaseJob {

  private static final String TAG = Log.tag(ProfileUploadJob.class);

  public static final String KEY = "ProfileUploadJob";

  public static final String QUEUE = "ProfileAlteration";

  public ProfileUploadJob() {
    this(new Job.Parameters.Builder()
                            .addConstraint(NetworkConstraint.KEY)
                            .setQueue(QUEUE)
                            .setLifespan(TimeUnit.DAYS.toMillis(30))
                            .setMaxAttempts(Parameters.UNLIMITED)
                            .setMaxInstancesForFactory(2)
                            .build());
  }

  private ProfileUploadJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  protected void onRun() throws Exception {
    if (!REDStore.account().isRegistered()) {
      Log.w(TAG, "Not registered. Skipping.");
      return;
    }

    if (REDStore.account().isLinkedDevice() && !REDStore.registration().hasDownloadedProfile()) {
      Log.w(TAG, "Attempting to upload profile before downloading, forcing download first");
      AppDependencies.getJobManager()
                     .startChain(new RefreshOwnProfileJob())
                     .then(new ProfileUploadJob())
                     .enqueue();

      return;
    }

    ProfileUtil.uploadProfile(context);
    Log.i(TAG, "Profile uploaded.");
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return true;
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
  }

  public static class Factory implements Job.Factory<ProfileUploadJob> {

    @Override
    public @NonNull ProfileUploadJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new ProfileUploadJob(parameters);
    }
  }
}
