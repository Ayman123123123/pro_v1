package org.signal.network.config;

public class REDProxy {
  private final String host;
  private final int    port;

  public REDProxy(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }
}
