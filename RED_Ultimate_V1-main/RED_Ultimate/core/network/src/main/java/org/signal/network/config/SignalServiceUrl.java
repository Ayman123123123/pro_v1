package org.signal.network.config;



import okhttp3.ConnectionSpec;

public class REDServiceUrl extends REDUrl {

  public REDServiceUrl(String url, TrustStore trustStore) {
    super(url, trustStore);
  }

  public REDServiceUrl(String url, String hostHeader, TrustStore trustStore, ConnectionSpec connectionSpec) {
    super(url, hostHeader, trustStore, connectionSpec);
  }
}
