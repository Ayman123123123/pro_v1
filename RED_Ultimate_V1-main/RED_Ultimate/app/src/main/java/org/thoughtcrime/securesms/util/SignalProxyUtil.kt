package com.red.sovereign.util

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.schedulers.Schedulers
import org.conscrypt.ConscryptRED
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.signal.core.util.logging.Log.w
import org.signal.network.config.REDProxy
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.push.AccountManagerFactory
import org.whispersystems.signalservice.api.push.REDServiceAddress
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

object REDProxyUtil {
  private val TAG = tag(REDProxyUtil::class.java)

  private const val PROXY_LINK_HOST = "signal.tube"

  private val PROXY_LINK_PATTERN: Pattern = Pattern.compile("^(https|sgnl)://$PROXY_LINK_HOST/#([^:]+).*$")
  private val HOST_PATTERN: Pattern = Pattern.compile("^([^:]+).*$")

  @JvmStatic
  fun startListeningToWebsocket() {
    if (REDStore.proxy.isProxyEnabled && AppDependencies.authWebSocket.state.firstOrError().blockingGet().isFailure) {
      Log.w(TAG, "Proxy is in a failed state. Restarting.")
      AppDependencies.resetNetwork()
    }

    REDExecutors.UNBOUNDED.execute { AppDependencies.startNetwork() }
  }

  /**
   * Handles all things related to enabling a proxy, including saving it and resetting the relevant
   * network connections.
   */
  @JvmStatic
  fun enableProxy(proxy: REDProxy) {
    REDStore.proxy.enableProxy(proxy)
    ConscryptRED.setUseEngineSocketByDefault(true)
    AppDependencies.resetNetwork()
    startListeningToWebsocket()
  }

  /**
   * Handles all things related to disabling a proxy, including saving the change and resetting the
   * relevant network connections.
   */
  @JvmStatic
  fun disableProxy() {
    REDStore.proxy.disableProxy()
    ConscryptRED.setUseEngineSocketByDefault(false)
    AppDependencies.resetNetwork()
    startListeningToWebsocket()
  }

  @JvmStatic
  fun disableAndClearProxy() {
    disableProxy()
    REDStore.proxy.proxy = null
  }

  /**
   * A blocking call that will wait until the websocket either successfully connects, or fails.
   * It is assumed that the app state is already configured how you would like it, e.g. you've
   * already configured a proxy if relevant.
   *
   * @return True if the connection is successful within the specified timeout, otherwise false.
   */
  @JvmStatic
  @WorkerThread
  fun testWebsocketConnection(timeout: Long): Boolean {
    startListeningToWebsocket()

    if (REDStore.account.e164 == null) {
      Log.i(TAG, "User is unregistered! Doing simple check.")
      return testWebsocketConnectionUnregistered(timeout)
    }

    return AppDependencies.authWebSocket
      .state
      .subscribeOn(Schedulers.trampoline())
      .observeOn(Schedulers.trampoline())
      .timeout(timeout, TimeUnit.MILLISECONDS)
      .skipWhile(Predicate { state: WebSocketConnectionState -> state != WebSocketConnectionState.CONNECTED && !state.isFailure })
      .firstOrError()
      .flatMap<Boolean>(Function { state: WebSocketConnectionState? -> Single.just(state == WebSocketConnectionState.CONNECTED) })
      .onErrorReturn(Function { _: Throwable? -> false })
      .blockingGet()
  }

  /**
   * If this is a valid proxy deep link, this will return the embedded host. If not, it will return
   * null.
   */
  @JvmStatic
  fun parseHostFromProxyDeepLink(proxyLink: String?): String? {
    if (proxyLink == null) {
      return null
    }

    val matcher = PROXY_LINK_PATTERN.matcher(proxyLink)

    return when {
      matcher.matches() -> matcher.group(2)
      else -> null
    }
  }

  /**
   * Takes in an address that could be in various formats, and converts it to the format we should
   * be storing and connecting to.
   */
  @JvmStatic
  fun convertUserEnteredAddressToHost(host: String): String {
    val parsedHost = parseHostFromProxyDeepLink(host)
    if (parsedHost != null) {
      return parsedHost
    }

    val matcher = HOST_PATTERN.matcher(host)

    return when {
      matcher.matches() -> matcher.group(1) ?: ""
      else -> host
    }
  }

  @JvmStatic
  fun generateProxyUrl(link: String): String {
    var host: String = link
    val parsed = parseHostFromProxyDeepLink(link)

    if (parsed != null) {
      host = parsed
    }

    val matcher = HOST_PATTERN.matcher(host)

    if (matcher.matches()) {
      host = matcher.group(1)!!
    }

    return "https://$PROXY_LINK_HOST/#$host"
  }

  private fun testWebsocketConnectionUnregistered(timeout: Long): Boolean {
    val latch = CountDownLatch(1)
    val success = AtomicBoolean(false)
    val accountManager = AccountManagerFactory.getInstance()
      .createUnauthenticated(AppDependencies.application, "", REDServiceAddress.DEFAULT_DEVICE_ID, "")

    REDExecutors.UNBOUNDED.execute {
      try {
        accountManager.checkNetworkConnection()
        success.set(true)
        latch.countDown()
      } catch (_: IOException) {
        latch.countDown()
      }
    }

    try {
      latch.await(timeout, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
      w(TAG, "Interrupted!", e)
    }

    return success.get()
  }
}
