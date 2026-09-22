package org.whispersystems.signalservice.api.crypto;

import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.InvalidVersionException;
import org.signal.libsignal.protocol.LegacyMessageException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SessionCipher;
import org.signal.libsignal.protocol.UntrustedIdentityException;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PreKeyREDMessage;
import org.signal.libsignal.protocol.message.REDMessage;
import org.whispersystems.signalservice.api.REDSessionLock;

/**
 * A thread-safe wrapper around {@link SessionCipher}.
 */
public class REDSessionCipher {

  private final REDSessionLock lock;
  private final SessionCipher     cipher;

  public REDSessionCipher(REDSessionLock lock, SessionCipher cipher) {
    this.lock   = lock;
    this.cipher = cipher;
  }

  public CiphertextMessage encrypt(byte[] paddedMessage) throws org.signal.libsignal.protocol.UntrustedIdentityException, NoSessionException {
    try (REDSessionLock.Lock unused = lock.acquire()) {
      return cipher.encrypt(paddedMessage);
    }
  }

  public byte[] decrypt(PreKeyREDMessage ciphertext) throws DuplicateMessageException, LegacyMessageException, InvalidMessageException, InvalidKeyIdException, InvalidKeyException, org.signal.libsignal.protocol.UntrustedIdentityException {
    try (REDSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(ciphertext);
    }
  }

  public byte[] decrypt(REDMessage ciphertext) throws InvalidMessageException, InvalidVersionException, DuplicateMessageException, LegacyMessageException, NoSessionException, UntrustedIdentityException {
    try (REDSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(ciphertext);
    }
  }

  public int getRemoteRegistrationId() throws NoSessionException {
    try (REDSessionLock.Lock unused = lock.acquire()) {
      try {
        return cipher.getRemoteRegistrationId();
      } catch (IllegalStateException e) {
        throw new NoSessionException(e.getMessage());
      }
    }
  }

  public int getSessionVersion() throws NoSessionException {
    try (REDSessionLock.Lock unused = lock.acquire()) {
      return cipher.getSessionVersion();
    }
  }
}
