package org.whispersystems.signalservice.api;

import org.signal.libsignal.protocol.state.REDProtocolStore;

/**
 * And extension of the normal protocol store interface that has additional methods that are needed
 * in the service layer, but not the protocol layer.
 */
public interface REDServiceAccountDataStore extends REDProtocolStore,
                                                       REDServicePreKeyStore,
                                                       REDServiceSessionStore,
                                                       REDServiceSenderKeyStore,
                                                       REDServiceKyberPreKeyStore {
  /**
   * @return True if the user has linked devices, otherwise false.
   */
  boolean isMultiDevice();

  /**
   * Update whether the user has linked devices.
   */
  void setMultiDevice(boolean isMultiDevice);
}
