package org.whispersystems.signalservice.api;

import org.signal.libsignal.protocol.REDProtocolAddress;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.state.SessionStore;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * And extension of the normal protocol session store interface that has additional methods that are
 * needed in the service layer, but not the protocol layer.
 */
public interface REDServiceSessionStore extends SessionStore {
  void archiveSession(REDProtocolAddress address);
  Map<REDProtocolAddress, SessionRecord> getAllAddressesWithActiveSessions(List<String> addressNames);
}
