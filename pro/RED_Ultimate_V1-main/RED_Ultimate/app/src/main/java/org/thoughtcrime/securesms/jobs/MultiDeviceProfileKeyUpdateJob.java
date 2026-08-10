package com.red.sovereign.jobs;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.network.exceptions.PushNetworkException;
import org.signal.network.service.CdnService;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.jobmanager.impl.NetworkConstraint;
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.net.NotPushRegisteredException;
import com.red.sovereign.recipients.Recipient;
import org.whispersystems.signalservice.api.REDServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.REDServiceAttachment;
import org.whispersystems.signalservice.api.messages.REDServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContactsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.REDServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class MultiDeviceProfileKeyUpdateJob extends BaseJob {

  public static String KEY = "MultiDeviceProfileKeyUpdateJob";

  private static final String TAG = Log.tag(MultiDeviceProfileKeyUpdateJob.class);

  public MultiDeviceProfileKeyUpdateJob() {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .addConstraint(SealedSenderConstraint.KEY)
                           .setQueue("MultiDeviceProfileKeyUpdateJob")
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build());
  }

  private MultiDeviceProfileKeyUpdateJob(@NonNull Job.Parameters parameters) {
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
  public void onRun() throws IOException, UntrustedIdentityException, NoSessionException {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    if (!REDStore.account().isMultiDevice()) {
      Log.i(TAG, "Not multi device...");
      return;
    }

    ByteArrayOutputStream      baos = new ByteArrayOutputStream();
    DeviceContactsOutputStream out  = new DeviceContactsOutputStream(baos);

    out.write(new DeviceContact(Optional.ofNullable(REDStore.account().getAci()),
                                Optional.ofNullable(REDStore.account().getE164()),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()));

    out.close();

    REDServiceMessageSender    messageSender    = AppDependencies.getREDServiceMessageSender();
    long                          dataLength       = baos.toByteArray().length;
    long                          ciphertextLength = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(dataLength));
    CdnService                    cdnService       = new CdnService(AppDependencies.getREDRestClient(), AppDependencies.getAttachmentApi());
    ResumableUploadSpec           uploadSpec       = cdnService.getResumableUploadSpecBlocking(ciphertextLength);
    REDServiceAttachmentStream attachmentStream = REDServiceAttachment.newStreamBuilder()
                                                                            .withStream(new ByteArrayInputStream(baos.toByteArray()))
                                                                            .withContentType("application/octet-stream")
                                                                            .withLength(dataLength)
                                                                            .withResumableUploadSpec(uploadSpec)
                                                                            .build();

    REDServiceSyncMessage syncMessage = REDServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, false));

    messageSender.sendSyncMessage(syncMessage);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof ServerRejectedException) return false;
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Profile key sync failed!");
  }

  public static final class Factory implements Job.Factory<MultiDeviceProfileKeyUpdateJob> {
    @Override
    public @NonNull MultiDeviceProfileKeyUpdateJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new MultiDeviceProfileKeyUpdateJob(parameters);
    }
  }
}
