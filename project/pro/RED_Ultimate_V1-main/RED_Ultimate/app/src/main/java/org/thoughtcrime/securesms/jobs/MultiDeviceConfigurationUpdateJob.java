package com.red.sovereign.jobs;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.network.exceptions.PushNetworkException;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.jobmanager.JsonJobData;
import com.red.sovereign.jobmanager.impl.NetworkConstraint;
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.net.NotPushRegisteredException;
import com.red.sovereign.recipients.Recipient;
import org.whispersystems.signalservice.api.REDServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.REDServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.Optional;

public class MultiDeviceConfigurationUpdateJob extends BaseJob {

  public static final String KEY   = "MultiDeviceConfigurationUpdateJob";
  public static final String QUEUE = "__MULTI_DEVICE_CONFIGURATION_UPDATE_JOB__";

  private static final String TAG = Log.tag(MultiDeviceConfigurationUpdateJob.class);

  private static final String KEY_READ_RECEIPTS_ENABLED                    = "read_receipts_enabled";
  private static final String KEY_TYPING_INDICATORS_ENABLED                = "typing_indicators_enabled";
  private static final String KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED = "unidentified_delivery_indicators_enabled";
  private static final String KEY_LINK_PREVIEWS_ENABLED                    = "link_previews_enabled";

  private boolean readReceiptsEnabled;
  private boolean typingIndicatorsEnabled;
  private boolean unidentifiedDeliveryIndicatorsEnabled;
  private boolean linkPreviewsEnabled;

  public MultiDeviceConfigurationUpdateJob(boolean readReceiptsEnabled,
                                           boolean typingIndicatorsEnabled,
                                           boolean unidentifiedDeliveryIndicatorsEnabled,
                                           boolean linkPreviewsEnabled)
  {
    this(new Job.Parameters.Builder()
                           .setQueue(QUEUE)
                           .addConstraint(NetworkConstraint.KEY)
                           .addConstraint(SealedSenderConstraint.KEY)
                           .setMaxAttempts(10)
                           .build(),
         readReceiptsEnabled,
         typingIndicatorsEnabled,
         unidentifiedDeliveryIndicatorsEnabled,
         linkPreviewsEnabled);

  }

  private MultiDeviceConfigurationUpdateJob(@NonNull Job.Parameters parameters,
                                            boolean readReceiptsEnabled,
                                            boolean typingIndicatorsEnabled,
                                            boolean unidentifiedDeliveryIndicatorsEnabled,
                                            boolean linkPreviewsEnabled)
  {
    super(parameters);

    this.readReceiptsEnabled                   = readReceiptsEnabled;
    this.typingIndicatorsEnabled               = typingIndicatorsEnabled;
    this.unidentifiedDeliveryIndicatorsEnabled = unidentifiedDeliveryIndicatorsEnabled;
    this.linkPreviewsEnabled                   = linkPreviewsEnabled;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putBoolean(KEY_READ_RECEIPTS_ENABLED, readReceiptsEnabled)
                                    .putBoolean(KEY_TYPING_INDICATORS_ENABLED, typingIndicatorsEnabled)
                                    .putBoolean(KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED, unidentifiedDeliveryIndicatorsEnabled)
                                    .putBoolean(KEY_LINK_PREVIEWS_ENABLED, linkPreviewsEnabled)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException, NoSessionException {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    if (!REDStore.account().isMultiDevice()) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    if (REDStore.account().isLinkedDevice()) {
      Log.i(TAG, "Not primary device, aborting...");
      return;
    }

    REDServiceMessageSender messageSender = AppDependencies.getREDServiceMessageSender();
    messageSender.sendSyncMessage(REDServiceSyncMessage.forConfiguration(new ConfigurationMessage(Optional.of(readReceiptsEnabled),
                                                                                                     Optional.of(unidentifiedDeliveryIndicatorsEnabled),
                                                                                                     Optional.of(typingIndicatorsEnabled),
                                                                                                     Optional.of(linkPreviewsEnabled)))
    );
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "**** Failed to synchronize read receipts state!");
  }

  public static final class Factory implements Job.Factory<MultiDeviceConfigurationUpdateJob> {
    @Override
    public @NonNull MultiDeviceConfigurationUpdateJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new MultiDeviceConfigurationUpdateJob(parameters,
                                                   data.getBooleanOrDefault(KEY_READ_RECEIPTS_ENABLED, false),
                                                   data.getBooleanOrDefault(KEY_TYPING_INDICATORS_ENABLED, false),
                                                   data.getBooleanOrDefault(KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED, false),
                                                   data.getBooleanOrDefault(KEY_LINK_PREVIEWS_ENABLED, false));
    }
  }
}
