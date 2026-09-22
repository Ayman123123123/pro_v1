package org.signal.network.config

import okhttp3.ConnectionSpec

/**
 * Configuration for reach the SVR2 service.
 */
class REDSvr2Url(
  url: String,
  trustStore: TrustStore,
  hostHeader: String? = null,
  connectionSpec: ConnectionSpec? = null
) : REDUrl(url, hostHeader, trustStore, connectionSpec)
