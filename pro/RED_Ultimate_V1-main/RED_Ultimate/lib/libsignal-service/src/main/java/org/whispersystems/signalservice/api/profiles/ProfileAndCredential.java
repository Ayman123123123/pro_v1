package org.whispersystems.signalservice.api.profiles;

import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;

import java.util.Optional;


public final class ProfileAndCredential {

  private final REDServiceProfile                   profile;
  private final REDServiceProfile.RequestType       requestType;
  private final Optional<ExpiringProfileKeyCredential> expiringProfileKeyCredential;

  public ProfileAndCredential(REDServiceProfile profile,
                              REDServiceProfile.RequestType requestType,
                              Optional<ExpiringProfileKeyCredential> expiringProfileKeyCredential)
  {
    this.profile                      = profile;
    this.requestType                  = requestType;
    this.expiringProfileKeyCredential = expiringProfileKeyCredential;
  }

  public REDServiceProfile getProfile() {
    return profile;
  }

  public REDServiceProfile.RequestType getRequestType() {
    return requestType;
  }

  public Optional<ExpiringProfileKeyCredential> getExpiringProfileKeyCredential() {
    return expiringProfileKeyCredential;
  }
}
