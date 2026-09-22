package org.whispersystems.signalservice.api;

import org.signal.core.models.ServiceId;

/**
 * And extension of the normal protocol store interface that has additional methods that are needed
 * in the service layer, but not the protocol layer.
 */
public interface REDServiceDataStore {

  /**
   * @return A {@link REDServiceAccountDataStore} for the specified account.
   */
  REDServiceAccountDataStore get(ServiceId accountIdentifier);

  /**
   * @return A {@link REDServiceAccountDataStore} for the ACI account.
   */
  REDServiceAccountDataStore aci();

  /**
   * @return A {@link REDServiceAccountDataStore} for the PNI account.
   */
  REDServiceAccountDataStore pni();

  /**
   * @return True if the user has linked devices, otherwise false.
   */
  boolean isMultiDevice();
}
