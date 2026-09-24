package com.red.sovereign.crypto.storage;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyStore;
import com.red.sovereign.crypto.ReentrantSessionLock;
import com.red.sovereign.database.REDDatabase;
import org.whispersystems.signalservice.api.REDServicePreKeyStore;
import org.whispersystems.signalservice.api.REDSessionLock;
import org.signal.core.models.ServiceId;

import java.util.List;

public class TextSecurePreKeyStore implements REDServicePreKeyStore, SignedPreKeyStore {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(TextSecurePreKeyStore.class);

  @NonNull
  private final ServiceId accountId;

  public TextSecurePreKeyStore(@NonNull ServiceId accountId) {
    this.accountId = accountId;
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      PreKeyRecord preKeyRecord = REDDatabase.oneTimePreKeys().get(accountId, preKeyId);

      if (preKeyRecord == null) throw new InvalidKeyIdException("No such key: " + preKeyId);
      else                      return preKeyRecord;
    }
  }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SignedPreKeyRecord signedPreKeyRecord = REDDatabase.signedPreKeys().get(accountId, signedPreKeyId);

      if (signedPreKeyRecord == null) throw new InvalidKeyIdException("No such signed prekey: " + signedPreKeyId);
      else                            return signedPreKeyRecord;
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return REDDatabase.signedPreKeys().getAll(accountId);
    }
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      REDDatabase.oneTimePreKeys().insert(accountId, preKeyId, record);
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      REDDatabase.signedPreKeys().insert(accountId, signedPreKeyId, record);
    }
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    return REDDatabase.oneTimePreKeys().get(accountId, preKeyId) != null;
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    return REDDatabase.signedPreKeys().get(accountId, signedPreKeyId) != null;
  }

  @Override
  public void removePreKey(int preKeyId) {
    REDDatabase.oneTimePreKeys().delete(accountId, preKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    REDDatabase.signedPreKeys().delete(accountId, signedPreKeyId);
  }

  @Override
  public void markAllOneTimeEcPreKeysStaleIfNecessary(long staleTime) {
    REDDatabase.oneTimePreKeys().markAllStaleIfNecessary(accountId, staleTime);
  }

  @Override
  public void deleteAllStaleOneTimeEcPreKeys(long threshold, int minCount) {
    REDDatabase.oneTimePreKeys().deleteAllStaleBefore(accountId, threshold, minCount);
  }
}
