package org.signal.network.config;



import okhttp3.ConnectionSpec;

public class REDCdsiUrl extends REDUrl {

  public REDCdsiUrl(String url, TrustStore trustStore) {
    super(url, trustStore);
  }

  public REDCdsiUrl(String url, String hostHeader, TrustStore trustStore, ConnectionSpec connectionSpec) {
    super(url, hostHeader, trustStore, connectionSpec);
  }
}
