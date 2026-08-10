package com.red.sovereign.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.libsignal.protocol.REDProtocolAddress;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import com.red.sovereign.crypto.ReentrantSessionLock;
import com.red.sovereign.database.SenderKeyTable;
import com.red.sovereign.database.REDDatabase;
import org.whispersystems.signalservice.api.REDServiceSenderKeyStore;
import org.whispersystems.signalservice.api.REDSessionLock;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * An implementation of the storage interface used by the protocol layer to store sender keys. For
 * more details around sender keys, see {@link SenderKeyTable}.
 */
public final class REDSenderKeyStore implements REDServiceSenderKeyStore {

  private final Context context;

  public REDSenderKeyStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public void storeSenderKey(@NonNull REDProtocolAddress sender, @NonNull UUID distributionId, @NonNull SenderKeyRecord record) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      REDDatabase.senderKeys().store(sender, DistributionId.from(distributionId), record);
    }
  }

  @Override
  public @Nullable SenderKeyRecord loadSenderKey(@NonNull REDProtocolAddress sender, @NonNull UUID distributionId) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return REDDatabase.senderKeys().load(sender, DistributionId.from(distributionId));
    }
  }

  @Override
  public Set<REDProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return REDDatabase.senderKeyShared().getSharedWith(distributionId);
    }
  }

  @Override
  public void markSenderKeySharedWith(DistributionId distributionId, Collection<REDProtocolAddress> addresses) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      REDDatabase.senderKeyShared().markAsShared(distributionId, addresses);
    }
  }

  @Override
  public void clearSenderKeySharedWith(Collection<REDProtocolAddress> addresses) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      REDDatabase.senderKeyShared().deleteAllFor(addresses);
    }
  }

  /**
   * Removes all sender key session state for all devices for the provided recipient-distributionId pair.
   */
  public void deleteAllFor(@NonNull String addressName, @NonNull DistributionId distributionId) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      REDDatabase.senderKeys().deleteAllFor(addressName, distributionId);
    }
  }

  /**
   * Deletes all sender key session state.
   */
  public void deleteAll() {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      REDDatabase.senderKeys().deleteAll();
    }
  }
}