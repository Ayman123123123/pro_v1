package com.red.sovereign.crypto.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.REDProtocolAddress;
import org.signal.libsignal.protocol.state.SessionRecord;
import com.red.sovereign.crypto.ReentrantSessionLock;
import com.red.sovereign.database.SessionTable;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;
import com.red.sovereign.util.RemoteConfig;
import org.whispersystems.signalservice.api.REDServiceSessionStore;
import org.whispersystems.signalservice.api.REDSessionLock;
import org.signal.core.models.ServiceId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TextSecureSessionStore implements REDServiceSessionStore {

  private static final String TAG = Log.tag(TextSecureSessionStore.class);

  private final ServiceId accountId;

  public TextSecureSessionStore(@NonNull ServiceId accountId) {
    this.accountId = accountId;
  }

  @Override
  public SessionRecord loadSession(@NonNull REDProtocolAddress address) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SessionRecord sessionRecord = REDDatabase.sessions().load(accountId, address);

      if (sessionRecord == null) {
        Log.w(TAG, "No existing session information found for " + address);
        return new SessionRecord();
      }

      return sessionRecord;
    }
  }

  @Override
  public List<SessionRecord> loadExistingSessions(List<REDProtocolAddress> addresses) throws NoSessionException {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      List<SessionRecord> sessionRecords = REDDatabase.sessions().load(accountId, addresses);

      if (sessionRecords.size() != addresses.size()) {
        String message = "Mismatch! Asked for " + addresses.size() + " sessions, but only found " + sessionRecords.size() + "!";
        Log.w(TAG, message);
        throw new NoSessionException(message);
      }

      if (sessionRecords.stream().anyMatch(Objects::isNull)) {
        throw new NoSessionException("Failed to find one or more sessions.");
      }

      return sessionRecords;
    }
  }

  @Override
  public void storeSession(@NonNull REDProtocolAddress address, @NonNull SessionRecord record) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      REDDatabase.sessions().store(accountId, address, record);
    }
  }

  @Override
  public boolean containsSession(REDProtocolAddress address) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SessionRecord sessionRecord = REDDatabase.sessions().load(accountId, address);

      return sessionRecord != null && sessionRecord.hasSenderChain(RemoteConfig.requirePqRatio());
    }
  }

  @Override
  public void deleteSession(REDProtocolAddress address) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      Log.w(TAG, "Deleting session for " + address);
      REDDatabase.sessions().delete(accountId, address);
    }
  }

  @Override
  public void deleteAllSessions(String name) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      Log.w(TAG, "Deleting all sessions for " + name);
      REDDatabase.sessions().deleteAllFor(accountId, name);
    }
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return REDDatabase.sessions().getSubDevices(accountId, name);
    }
  }

  @Override
  public Map<REDProtocolAddress, SessionRecord> getAllAddressesWithActiveSessions(List<String> addressNames) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return REDDatabase.sessions()
                           .getAllFor(accountId, addressNames)
                           .stream()
                           .filter(row -> isActive(row.getRecord()))
                           .collect(Collectors.toMap(row -> new REDProtocolAddress(row.getAddress(), row.getDeviceId()), SessionTable.SessionRow::getRecord));
    }
  }

  @Override
  public void archiveSession(REDProtocolAddress address) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SessionRecord session = REDDatabase.sessions().load(accountId, address);
      if (session != null) {
        session.archiveCurrentState();
        REDDatabase.sessions().store(accountId, address, session);
      }
    }
  }
  
  public void archiveSession(@NonNull ServiceId serviceId, int deviceId) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      archiveSession(new REDProtocolAddress(serviceId.toString(), deviceId));
    }
  }

  public void archiveSessions(@NonNull RecipientId recipientId, int deviceId) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      Recipient recipient = Recipient.resolved(recipientId);

      if (recipient.getHasAci()) {
        archiveSession(new REDProtocolAddress(recipient.requireAci().toString(), deviceId));
      }

      if (recipient.getHasPni()) {
        archiveSession(new REDProtocolAddress(recipient.requirePni().toString(), deviceId));
      }
    }
  }

  public void archiveSessions(@NonNull RecipientId recipientId) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      Recipient recipient = Recipient.resolved(recipientId);

      if (recipient.getHasAci()) {
        REDProtocolAddress address = new REDProtocolAddress(recipient.requireAci().toString(), 1);
        archiveSiblingSessions(address);
        archiveSession(address);
      }

      if (recipient.getHasPni()) {
        REDProtocolAddress address = new REDProtocolAddress(recipient.requirePni().toString(), 1);
        archiveSiblingSessions(address);
        archiveSession(address);
      }
    }
  }

  public void archiveSiblingSessions(@NonNull REDProtocolAddress address) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      List<SessionTable.SessionRow> sessions = REDDatabase.sessions().getAllFor(accountId, address.getName());

      for (SessionTable.SessionRow row : sessions) {
        if (row.getDeviceId() != address.getDeviceId()) {
          row.getRecord().archiveCurrentState();
          storeSession(new REDProtocolAddress(row.getAddress(), row.getDeviceId()), row.getRecord());
        }
      }
    }
  }

  public void archiveAllSessions() {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      List<SessionTable.SessionRow> sessions = REDDatabase.sessions().getAll(accountId);

      for (SessionTable.SessionRow row : sessions) {
        row.getRecord().archiveCurrentState();
        storeSession(new REDProtocolAddress(row.getAddress(), row.getDeviceId()), row.getRecord());
      }
    }
  }

  private static boolean isActive(@Nullable SessionRecord record) {
    return record != null && record.hasSenderChain(RemoteConfig.requirePqRatio());
  }
}
