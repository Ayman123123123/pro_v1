package com.red.sovereign.crypto.storage;

import androidx.annotation.NonNull;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.REDProtocolAddress;
import org.signal.libsignal.protocol.state.IdentityKeyStore;
import com.red.sovereign.database.IdentityTable.VerifiedStatus;
import com.red.sovereign.database.identity.IdentityRecordList;
import com.red.sovereign.database.model.IdentityRecord;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;
import org.signal.core.models.ServiceId;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A wrapper around an instance of {@link REDBaseIdentityKeyStore} that lets us report different values for {@link #getIdentityKeyPair()}.
 * This lets us have multiple instances (one for ACI, one for PNI) that share the same underlying data while also reporting the correct identity key.
 */
public class REDIdentityKeyStore implements IdentityKeyStore {

  private final REDBaseIdentityKeyStore baseStore;
  private final Supplier<IdentityKeyPair>  identitySupplier;

  public REDIdentityKeyStore(@NonNull REDBaseIdentityKeyStore baseStore, @NonNull Supplier<IdentityKeyPair> identitySupplier) {
    this.baseStore        = baseStore;
    this.identitySupplier = identitySupplier;
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return identitySupplier.get();
  }

  @Override
  public int getLocalRegistrationId() {
    return baseStore.getLocalRegistrationId();
  }

  @Override
  public IdentityChange saveIdentity(REDProtocolAddress address, IdentityKey identityKey) {
    return baseStore.saveIdentity(address, identityKey);
  }

  public @NonNull SaveResult saveIdentity(REDProtocolAddress address, IdentityKey identityKey, boolean nonBlockingApproval) {
    return baseStore.saveIdentity(address, identityKey, nonBlockingApproval);
  }

  public void saveIdentityWithoutSideEffects(@NonNull RecipientId recipientId,
                                             @NonNull ServiceId serviceId,
                                             IdentityKey identityKey,
                                             VerifiedStatus verifiedStatus,
                                             boolean firstUse,
                                             long timestamp,
                                             boolean nonBlockingApproval)
  {
    baseStore.saveIdentityWithoutSideEffects(recipientId, serviceId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
  }

  @Override
  public boolean isTrustedIdentity(REDProtocolAddress address, IdentityKey identityKey, Direction direction) {
    return baseStore.isTrustedIdentity(address, identityKey, direction);
  }

  @Override
  public IdentityKey getIdentity(REDProtocolAddress address) {
    return baseStore.getIdentity(address);
  }

  public @NonNull Optional<IdentityRecord> getIdentityRecord(@NonNull RecipientId recipientId) {
    return baseStore.getIdentityRecord(recipientId);
  }

  public @NonNull Optional<IdentityRecord> getIdentityRecord(@NonNull Recipient recipient) {
    return baseStore.getIdentityRecord(recipient);
  }

  public @NonNull IdentityRecordList getIdentityRecords(@NonNull List<Recipient> recipients) {
    return baseStore.getIdentityRecords(recipients);
  }

  public void setApproval(@NonNull RecipientId recipientId, boolean nonBlockingApproval) {
    baseStore.setApproval(recipientId, nonBlockingApproval);
  }

  public void setVerified(@NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    baseStore.setVerified(recipientId, identityKey, verifiedStatus);
  }

  public void delete(@NonNull String addressName) {
    baseStore.delete(addressName);
  }

  public void invalidate(@NonNull String addressName) {
    baseStore.invalidate(addressName);
  }

  public enum SaveResult {
    NEW,
    UPDATE,
    NON_BLOCKING_APPROVAL_REQUIRED,
    NO_CHANGE
  }
}
