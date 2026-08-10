package org.signal.network.config;



import okhttp3.ConnectionSpec;

public class REDStorageUrl extends REDUrl {

  public REDStorageUrl(String url, TrustStore trustStore) {
    super(url, trustStore);
  }

  public REDStorageUrl(String url, String hostHeader, TrustStore trustStore, ConnectionSpec connectionSpec) {
    super(url, hostHeader, trustStore, connectionSpec);
  }
}
