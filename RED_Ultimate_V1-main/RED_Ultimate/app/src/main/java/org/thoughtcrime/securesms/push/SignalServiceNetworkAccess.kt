package com.red.sovereign.push

import android.content.Context
import android.net.ConnectivityManager
import android.net.ProxyInfo
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.TlsVersion
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.network.config.HttpProxy
import org.signal.network.config.REDCdnUrl
import org.signal.network.config.REDCdsiUrl
import org.signal.network.config.REDServiceConfiguration
import org.signal.network.config.REDServiceUrl
import org.signal.network.config.REDStorageUrl
import org.signal.network.config.REDSvr2Url
import org.signal.network.config.TrustStore
import com.red.sovereign.BuildConfig
import com.red.sovereign.keyvalue.SettingsValues
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.CustomDns
import com.red.sovereign.net.DeprecatedClientPreventionInterceptor
import com.red.sovereign.net.DeviceTransferBlockingInterceptor
import com.red.sovereign.net.RemoteDeprecationDetectorInterceptor
import com.red.sovereign.net.SequentialDns
import com.red.sovereign.net.StandardUserAgentInterceptor
import com.red.sovereign.net.StaticDns
import com.red.sovereign.net.StorageServiceSizeLoggingInterceptor
import java.io.IOException
import java.util.Optional

/**
 * Provides a [REDServiceConfiguration] to be used with our service layer.
 * If you're looking for a place to start, look at [getConfiguration].
 */
class REDServiceNetworkAccess(context: Context) {
  companion object {
    private val TAG = Log.tag(REDServiceNetworkAccess::class.java)

    @JvmField
    val DNS: Dns = SequentialDns(
      Dns.SYSTEM,
      CustomDns("1.1.1.1"),
      StaticDns(
        mapOf(
          BuildConfig.SIGNAL_URL.stripProtocol() to BuildConfig.SIGNAL_SERVICE_IPS.toSet(),
          BuildConfig.STORAGE_URL.stripProtocol() to BuildConfig.SIGNAL_STORAGE_IPS.toSet(),
          BuildConfig.SIGNAL_CDN_URL.stripProtocol() to BuildConfig.SIGNAL_CDN_IPS.toSet(),
          BuildConfig.SIGNAL_CDN2_URL.stripProtocol() to BuildConfig.SIGNAL_CDN2_IPS.toSet(),
          BuildConfig.SIGNAL_CDN3_URL.stripProtocol() to BuildConfig.SIGNAL_CDN3_IPS.toSet(),
          BuildConfig.SIGNAL_SFU_URL.stripProtocol() to BuildConfig.SIGNAL_SFU_IPS.toSet(),
          BuildConfig.CONTENT_PROXY_HOST.stripProtocol() to BuildConfig.SIGNAL_CONTENT_PROXY_IPS.toSet(),
          BuildConfig.SIGNAL_CDSI_URL.stripProtocol() to BuildConfig.SIGNAL_CDSI_IPS.toSet(),
          BuildConfig.SIGNAL_SVR2_URL.stripProtocol() to BuildConfig.SIGNAL_SVR2_IPS.toSet()
        )
      )
    )

    private fun String.stripProtocol(): String {
      return this.removePrefix("https://")
    }

    private const val COUNTRY_CODE_EGYPT = 20
    private const val COUNTRY_CODE_UAE = 971
    private const val COUNTRY_CODE_OMAN = 968
    private const val COUNTRY_CODE_QATAR = 974
    private const val COUNTRY_CODE_IRAN = 98
    private const val COUNTRY_CODE_CUBA = 53
    private const val COUNTRY_CODE_UZBEKISTAN = 998
    private const val COUNTRY_CODE_VENEZUELA = 58
    private const val COUNTRY_CODE_PAKISTAN = 92

    private const val G_HOST = "reflector-nrgwuv7kwq-uc.a.run.app"
    private const val F_SERVICE_HOST = "chat-signal.global.ssl.fastly.net"
    private const val F_STORAGE_HOST = "storage.red.local.global.prod.fastly.net"
    private const val F_CDN_HOST = "cdn.red.local.global.prod.fastly.net"
    private const val F_CDN2_HOST = "cdn2.red.local.global.prod.fastly.net"
    private const val F_CDN3_HOST = "cdn3-signal.global.ssl.fastly.net"
    private const val F_CDSI_HOST = "cdsi-signal.global.ssl.fastly.net"
    private const val F_SVR2_HOST = "svr2-signal.global.ssl.fastly.net"

    private val GMAPS_CONNECTION_SPEC = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(
        CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
      )
      .supportsTlsExtensions(true)
      .build()

    private val GMAIL_CONNECTION_SPEC = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
      )
      .supportsTlsExtensions(true)
      .build()

    private val PLAY_CONNECTION_SPEC = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .cipherSuites(
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
      )
      .supportsTlsExtensions(true)
      .build()

    private val APP_CONNECTION_SPEC = ConnectionSpec.MODERN_TLS

    @Suppress("DEPRECATION")
    private fun getSystemHttpProxy(context: Context): HttpProxy? {
      val connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager::class.java) ?: return null

      val proxyInfo = connectivityManager
        .activeNetwork
        ?.let { connectivityManager.getLinkProperties(it)?.httpProxy }

      return proxyInfo.toApplicableSystemHttpProxy()
    }

    fun ProxyInfo?.toApplicableSystemHttpProxy(): HttpProxy? {
      return this
        ?.takeIf { !it.exclusionList.contains(BuildConfig.SIGNAL_URL.stripProtocol()) }
        // NB: Edit carefully, dear reader, as the line below is written from hard won experience.
        // It turns out, that despite being documented *nowhere*, if a PAC file is set
        //   as the system proxy, proxyInfo.host will return "localhost" and proxyInfo.port
        //   will return -1.
        // I learnt this by reading the AOSP source code for ProxyInfo:
        //   https://android.googlesource.com/platform/frameworks/base/+/4696ee4/core/java/android/net/ProxyInfo.java#107
        // So, if we do not explicitly check that a PAC file is not set, the proxy
        //   we pass to libsignal may be syntactically invalid, and the user may be
        //   rendered unable to connect.
        ?.takeIf { it.pacFileUrl == Uri.EMPTY }
        ?.let { proxy -> HttpProxy(proxy.host, proxy.port) }
    }
  }

  private val serviceTrustStore: TrustStore = REDServiceTrustStore(context)
  private val gTrustStore: TrustStore = DomainFrontingTrustStore(context)
  private val fTrustStore: TrustStore = DomainFrontingDigicertTrustStore(context)

  private val interceptors: List<Interceptor> = listOf(
    StandardUserAgentInterceptor(),
    StorageServiceSizeLoggingInterceptor(),
    RemoteDeprecationDetectorInterceptor(this::getConfiguration),
    DeprecatedClientPreventionInterceptor(),
    DeviceTransferBlockingInterceptor.getInstance()
  )

  private val zkGroupServerPublicParams: ByteArray = try {
    Base64.decode(BuildConfig.ZKGROUP_SERVER_PUBLIC_PARAMS)
  } catch (e: IOException) {
    throw AssertionError(e)
  }

  private val genericServerPublicParams: ByteArray = try {
    Base64.decode(BuildConfig.GENERIC_SERVER_PUBLIC_PARAMS)
  } catch (e: IOException) {
    throw AssertionError(e)
  }

  private val backupServerPublicParams: ByteArray = try {
    Base64.decode(BuildConfig.BACKUP_SERVER_PUBLIC_PARAMS)
  } catch (e: IOException) {
    throw AssertionError(e)
  }

  private val baseGHostConfigs: List<HostConfig> = listOf(
    HostConfig("https://www.google.com", G_HOST, GMAIL_CONNECTION_SPEC),
    HostConfig("https://android.clients.google.com", G_HOST, PLAY_CONNECTION_SPEC),
    HostConfig("https://clients3.google.com", G_HOST, GMAPS_CONNECTION_SPEC),
    HostConfig("https://clients4.google.com", G_HOST, GMAPS_CONNECTION_SPEC),
    HostConfig("https://googlemail.com", G_HOST, GMAIL_CONNECTION_SPEC)
  )

  private val fUrls = arrayOf("https://github.githubassets.com", "https://pinterest.com", "https://www.redditstatic.com")

  private val fConfig: REDServiceConfiguration = REDServiceConfiguration(
    signalServiceUrls = fUrls.map { REDServiceUrl(it, F_SERVICE_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
    signalCdnUrlMap = mapOf(
      0 to fUrls.map { REDCdnUrl(it, F_CDN_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
      2 to fUrls.map { REDCdnUrl(it, F_CDN2_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
      3 to fUrls.map { REDCdnUrl(it, F_CDN3_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray()
    ),
    signalStorageUrls = fUrls.map { REDStorageUrl(it, F_STORAGE_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
    signalCdsiUrls = fUrls.map { REDCdsiUrl(it, F_CDSI_HOST, fTrustStore, APP_CONNECTION_SPEC) }.toTypedArray(),
    signalSvr2Urls = fUrls.map { REDSvr2Url(it, fTrustStore, F_SVR2_HOST, APP_CONNECTION_SPEC) }.toTypedArray(),
    networkInterceptors = interceptors,
    dns = Optional.of(DNS),
    signalProxy = Optional.empty(),
    systemHttpProxy = Optional.empty(),
    zkGroupServerPublicParams = zkGroupServerPublicParams,
    genericServerPublicParams = genericServerPublicParams,
    backupServerPublicParams = backupServerPublicParams,
    censored = true
  )

  private val censorshipConfiguration: Map<Int, REDServiceConfiguration> = mapOf(
    COUNTRY_CODE_EGYPT to buildGConfiguration(
      listOf(HostConfig("https://www.google.com.eg", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_UAE to buildGConfiguration(
      listOf(HostConfig("https://www.google.ae", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_OMAN to buildGConfiguration(
      listOf(HostConfig("https://www.google.com.om", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_QATAR to buildGConfiguration(
      listOf(HostConfig("https://www.google.com.qa", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_UZBEKISTAN to buildGConfiguration(
      listOf(HostConfig("https://www.google.co.uz", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_VENEZUELA to buildGConfiguration(
      listOf(HostConfig("https://www.google.co.ve", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_PAKISTAN to buildGConfiguration(
      listOf(HostConfig("https://www.google.com.pk", G_HOST, GMAIL_CONNECTION_SPEC)) + baseGHostConfigs
    ),
    COUNTRY_CODE_IRAN to fConfig,
    COUNTRY_CODE_CUBA to fConfig
  )

  private val defaultCensoredConfiguration: REDServiceConfiguration = buildGConfiguration(baseGHostConfigs) + fConfig

  private val defaultCensoredCountryCodes: Set<Int> = setOf(
    COUNTRY_CODE_EGYPT,
    COUNTRY_CODE_UAE,
    COUNTRY_CODE_OMAN,
    COUNTRY_CODE_QATAR,
    COUNTRY_CODE_IRAN,
    COUNTRY_CODE_CUBA,
    COUNTRY_CODE_UZBEKISTAN,
    COUNTRY_CODE_VENEZUELA,
    COUNTRY_CODE_PAKISTAN
  )

  val uncensoredConfiguration: REDServiceConfiguration = REDServiceConfiguration(
    signalServiceUrls = arrayOf(REDServiceUrl(BuildConfig.SIGNAL_URL, serviceTrustStore)),
    signalCdnUrlMap = mapOf(
      0 to arrayOf(REDCdnUrl(BuildConfig.SIGNAL_CDN_URL, serviceTrustStore)),
      2 to arrayOf(REDCdnUrl(BuildConfig.SIGNAL_CDN2_URL, serviceTrustStore)),
      3 to arrayOf(REDCdnUrl(BuildConfig.SIGNAL_CDN3_URL, serviceTrustStore))
    ),
    signalStorageUrls = arrayOf(REDStorageUrl(BuildConfig.STORAGE_URL, serviceTrustStore)),
    signalCdsiUrls = arrayOf(REDCdsiUrl(BuildConfig.SIGNAL_CDSI_URL, serviceTrustStore)),
    signalSvr2Urls = arrayOf(REDSvr2Url(BuildConfig.SIGNAL_SVR2_URL, serviceTrustStore)),
    networkInterceptors = interceptors,
    dns = Optional.of(DNS),
    signalProxy = if (REDStore.proxy.isProxyEnabled) Optional.ofNullable(REDStore.proxy.proxy) else Optional.empty(),
    systemHttpProxy = Optional.ofNullable(getSystemHttpProxy(context)),
    zkGroupServerPublicParams = zkGroupServerPublicParams,
    genericServerPublicParams = genericServerPublicParams,
    backupServerPublicParams = backupServerPublicParams,
    censored = false
  )

  fun getConfiguration(): REDServiceConfiguration {
    return getConfiguration(REDStore.account.e164)
  }

  fun getConfiguration(e164: String?): REDServiceConfiguration {
    if (e164 == null || REDStore.proxy.isProxyEnabled) {
      return uncensoredConfiguration
    }

    val countryCode: Int = PhoneNumberUtil.getInstance().parse(e164, null).countryCode

    return when (REDStore.settings.censorshipCircumventionEnabled) {
      SettingsValues.CensorshipCircumventionEnabled.ENABLED -> {
        censorshipConfiguration[countryCode] ?: defaultCensoredConfiguration
      }

      SettingsValues.CensorshipCircumventionEnabled.DISABLED -> {
        uncensoredConfiguration
      }

      SettingsValues.CensorshipCircumventionEnabled.DEFAULT -> {
        if (defaultCensoredCountryCodes.contains(countryCode)) {
          censorshipConfiguration[countryCode] ?: defaultCensoredConfiguration
        } else {
          uncensoredConfiguration
        }
      }
    }
  }

  fun isCensored(): Boolean {
    return isCensored(REDStore.account.e164)
  }

  fun isCensored(number: String?): Boolean {
    return getConfiguration(number) != uncensoredConfiguration
  }

  fun isCountryCodeCensoredByDefault(countryCode: Int): Boolean {
    return defaultCensoredCountryCodes.contains(countryCode)
  }

  private fun buildGConfiguration(
    hostConfigs: List<HostConfig>
  ): REDServiceConfiguration {
    val serviceUrls: Array<REDServiceUrl> = hostConfigs.map { REDServiceUrl("${it.baseUrl}/service", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdnUrls: Array<REDCdnUrl> = hostConfigs.map { REDCdnUrl("${it.baseUrl}/cdn", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdn2Urls: Array<REDCdnUrl> = hostConfigs.map { REDCdnUrl("${it.baseUrl}/cdn2", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdn3Urls: Array<REDCdnUrl> = hostConfigs.map { REDCdnUrl("${it.baseUrl}/cdn3", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val storageUrls: Array<REDStorageUrl> = hostConfigs.map { REDStorageUrl("${it.baseUrl}/storage", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val cdsiUrls: Array<REDCdsiUrl> = hostConfigs.map { REDCdsiUrl("${it.baseUrl}/cdsi", it.host, gTrustStore, it.connectionSpec) }.toTypedArray()
    val svr2Urls: Array<REDSvr2Url> = hostConfigs.map { REDSvr2Url("${it.baseUrl}/svr2", gTrustStore, it.host, it.connectionSpec) }.toTypedArray()

    return REDServiceConfiguration(
      signalServiceUrls = serviceUrls,
      signalCdnUrlMap = mapOf(
        0 to cdnUrls,
        2 to cdn2Urls,
        3 to cdn3Urls
      ),
      signalStorageUrls = storageUrls,
      signalCdsiUrls = cdsiUrls,
      signalSvr2Urls = svr2Urls,
      networkInterceptors = interceptors,
      dns = Optional.of(DNS),
      signalProxy = Optional.empty(),
      systemHttpProxy = Optional.empty(),
      zkGroupServerPublicParams = zkGroupServerPublicParams,
      genericServerPublicParams = genericServerPublicParams,
      backupServerPublicParams = backupServerPublicParams,
      censored = true
    )
  }

  private data class HostConfig(val baseUrl: String, val host: String, val connectionSpec: ConnectionSpec)
}
