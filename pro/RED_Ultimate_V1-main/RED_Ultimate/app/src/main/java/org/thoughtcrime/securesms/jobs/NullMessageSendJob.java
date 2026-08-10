package com.red.sovereign.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import com.red.sovereign.crypto.SealedSenderAccessUtil;
import com.red.sovereign.database.RecipientTable.RegisteredState;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.RecipientRecord;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.jobmanager.JsonJobData;
import com.red.sovereign.jobmanager.impl.NetworkConstraint;
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint;
import com.red.sovereign.recipients.RecipientId;
import com.red.sovereign.recipients.RecipientUtil;
import org.whispersystems.signalservice.api.REDServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.REDServiceAddress;
import org.signal.network.exceptions.PushNetworkException;

import java.util.concurrent.TimeUnit;

/**
 * Just sends an empty message to a target recipient. Only suitable for individuals, NOT groups.
 */
public class NullMessageSendJob extends BaseJob {

  public static final String KEY = "NullMessageSendJob";

  private static final String TAG = Log.tag(NullMessageSendJob.class);

  private final RecipientId recipientId;

  private static final String KEY_RECIPIENT_ID = "recipient_id";

  public NullMessageSendJob(@NonNull RecipientId recipientId) {
    this(recipientId,
         new Parameters.Builder()
                       .setQueue(recipientId.toQueueKey())
                       .addConstraint(NetworkConstraint.KEY)
                       .addConstraint(SealedSenderConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build());
  }

  private NullMessageSendJob(@NonNull RecipientId recipientId, @NonNull Parameters parameters) {
    super(parameters);
    this.recipientId = recipientId;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_RECIPIENT_ID, recipientId.serialize()).serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!REDDatabase.recipients().containsId(recipientId)) {
      Log.w(TAG, "Cannot find recipient, likely deleted group.");
      return;
    }

    RecipientRecord recipient = REDDatabase.recipients().getRecord(recipientId);

    if (recipient.getGroupId() != null) {
      Log.w(TAG, "Groups are not supported!");
      return;
    }

    if (recipient.getRegistered() == RegisteredState.NOT_REGISTERED) {
      Log.w(TAG, recipient.getId() + " not registered!");
    }

    REDServiceMessageSender messageSender = AppDependencies.getREDServiceMessageSender();
    REDServiceAddress       address       = RecipientUtil.toREDServiceAddress(recipient);

    try {
      messageSender.sendNullMessage(address, SealedSenderAccessUtil.getSealedSenderAccessFor(recipient));
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, "Unable to send null message.");
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<NullMessageSendJob> {

    @Override
    public @NonNull NullMessageSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new NullMessageSendJob(RecipientId.from(data.getString(KEY_RECIPIENT_ID)),
                                    parameters);
    }
  }
}
