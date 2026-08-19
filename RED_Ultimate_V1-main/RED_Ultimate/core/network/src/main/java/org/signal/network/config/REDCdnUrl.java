package org.signal.network.config;


import okhttp3.ConnectionSpec;

public class REDCdnUrl extends REDUrl {
  public REDCdnUrl(String url, TrustStore trustStore) {
    super(url, trustStore);
  }

  public REDCdnUrl(String url, String hostHeader, TrustStore trustStore, ConnectionSpec connectionSpec) {
    super(url, hostHeader, trustStore, connectionSpec);
  }
}