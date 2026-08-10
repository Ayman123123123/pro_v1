package org.signal.network.config

import okhttp3.Dns
import okhttp3.Interceptor
import java.util.Optional

/**
 * Defines all network configuration needed to connect to the RED service.
 */
@Suppress("ArrayInDataClass") // Using data class for .copy(), don't care about equals/hashcode
data class REDServiceConfiguration(
  val signalServiceUrls: Array<REDServiceUrl>,
  val signalCdnUrlMap: Map<Int, Array<REDCdnUrl>>,
  val signalStorageUrls: Array<REDStorageUrl>,
  val signalCdsiUrls: Array<REDCdsiUrl>,
  val signalSvr2Urls: Array<REDSvr2Url>,
  val networkInterceptors: List<Interceptor>,
  val dns: Optional<Dns>,
  val signalProxy: Optional<REDProxy>,
  val systemHttpProxy: Optional<HttpProxy>,
  val zkGroupServerPublicParams: ByteArray,
  val genericServerPublicParams: ByteArray,
  val backupServerPublicParams: ByteArray,
  val censored: Boolean
) {

  /** Convenience operator overload for combining the URL lists. Does not add the other fields together, as those wouldn't make sense.  */
  operator fun plus(other: REDServiceConfiguration): REDServiceConfiguration {
    return this.copy(
      signalServiceUrls = signalServiceUrls + other.signalServiceUrls,
      signalCdnUrlMap = signalCdnUrlMap + other.signalCdnUrlMap,
      signalStorageUrls = signalStorageUrls + other.signalStorageUrls,
      signalCdsiUrls = signalCdsiUrls + other.signalCdsiUrls,
      signalSvr2Urls = signalSvr2Urls + other.signalSvr2Urls
    )
  }
}
