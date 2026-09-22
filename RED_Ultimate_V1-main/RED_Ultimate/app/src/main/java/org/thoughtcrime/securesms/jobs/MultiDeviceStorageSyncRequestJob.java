package com.red.sovereign.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.jobmanager.impl.NetworkConstraint;
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.net.NotPushRegisteredException;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.REDServiceMessageSender;
import org.whispersystems.signalservice.api.messages.multidevice.REDServiceSyncMessage;
import org.signal.network.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

public class MultiDeviceStorageSyncRequestJob extends BaseJob {

  public static final String KEY = "MultiDeviceStorageSyncRequestJob";

  private static final String TAG = Log.tag(MultiDeviceStorageSyncRequestJob.class);

  public MultiDeviceStorageSyncRequestJob() {
    this(new Parameters.Builder()
                       .setQueue("MultiDeviceStorageSyncRequestJob")
                       .setMaxInstancesForFactory(2)
                       .addConstraint(NetworkConstraint.KEY)
                       .addConstraint(SealedSenderConstraint.KEY)
                       .setMaxAttempts(10)
                       .build());
  }

  private MultiDeviceStorageSyncRequestJob(@NonNull Parameters parameters) {
    super(parameters);
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
  protected void onRun() throws Exception {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    if (!REDStore.account().isMultiDevice()) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    REDServiceMessageSender messageSender = AppDependencies.getREDServiceMessageSender();

    messageSender.sendSyncMessage(REDServiceSyncMessage.forFetchLatest(REDServiceSyncMessage.FetchType.STORAGE_MANIFEST));
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Did not succeed!");
  }

  public static final class Factory implements Job.Factory<MultiDeviceStorageSyncRequestJob> {
    @Override
    public @NonNull MultiDeviceStorageSyncRequestJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new MultiDeviceStorageSyncRequestJob(parameters);
    }
  }
}
