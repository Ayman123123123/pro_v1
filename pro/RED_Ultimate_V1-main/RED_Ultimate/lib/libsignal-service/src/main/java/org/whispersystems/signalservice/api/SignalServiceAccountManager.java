/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;

import org.signal.libsignal.net.Network;
import org.whispersystems.signalservice.api.account.AccountApi;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.models.ServiceId.PNI;
import org.whispersystems.signalservice.api.registration.RegistrationApi;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV2;
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV3;
import org.whispersystems.signalservice.api.websocket.REDWebSocket;
import org.signal.network.config.REDServiceConfiguration;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.WhoAmIResponse;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The main interface for creating, registering, and
 * managing a RED Service account.
 *
 * @author Moxie Marlinspike
 */
public class REDServiceAccountManager {

  private static final String TAG = REDServiceAccountManager.class.getSimpleName();

  private final PushServiceSocket                      pushServiceSocket;
  private final GroupsV2Operations                     groupsV2Operations;
  private final REDServiceConfiguration             configuration;
  private final REDWebSocket.AuthenticatedWebSocket authWebSocket;
  private final AccountApi                             accountApi;

  /**
   * Construct a REDServiceAccountManager.
   * @param configuration The URL for the RED Service.
   * @param aci The RED Service ACI.
   * @param pni The RED Service PNI.
   * @param e164 The RED Service phone number.
   * @param password A RED Service password.
   * @param signalAgent A string which identifies the client software.
   */
  public static REDServiceAccountManager createWithStaticCredentials(REDServiceConfiguration configuration,
                                                                        ACI aci,
                                                                        PNI pni,
                                                                        String e164,
                                                                        int deviceId,
                                                                        String password,
                                                                        String signalAgent,
                                                                        boolean automaticNetworkRetry,
                                                                        int maxGroupSize)
  {
    StaticCredentialsProvider credentialProvider = new StaticCredentialsProvider(aci, pni, e164, deviceId, password);
    GroupsV2Operations        gv2Operations      = new GroupsV2Operations(ClientZkOperations.create(configuration), maxGroupSize);

    return new REDServiceAccountManager(
        null,
        null,
        new PushServiceSocket(configuration, credentialProvider, signalAgent, automaticNetworkRetry),
        gv2Operations
    );
  }

  public REDServiceAccountManager(@Nullable REDWebSocket.AuthenticatedWebSocket authWebSocket,
                                     @Nullable AccountApi accountApi,
                                     @Nonnull PushServiceSocket pushServiceSocket,
                                     @Nonnull GroupsV2Operations groupsV2Operations) {
    this.authWebSocket      = authWebSocket;
    this.accountApi         = accountApi;
    this.groupsV2Operations = groupsV2Operations;
    this.pushServiceSocket  = pushServiceSocket;
    this.configuration      = pushServiceSocket.getConfiguration();
  }

  public SecureValueRecoveryV2 getSecureValueRecoveryV2(String mrEnclave) {
    return new SecureValueRecoveryV2(configuration, mrEnclave, authWebSocket);
  }

  public SecureValueRecoveryV3 getSecureValueRecoveryV3(Network network) {
    return new SecureValueRecoveryV3(network, authWebSocket);
  }

  public WhoAmIResponse getWhoAmI() throws IOException {
    return NetworkResultUtil.toBasicLegacy(accountApi.whoAmI());
  }

  /**
   * Request a push challenge. A number will be pushed to the GCM (FCM) id. This can then be used
   * during SMS/call requests to bypass the CAPTCHA.
   *
   * @param gcmRegistrationId The GCM (FCM) id to use.
   * @param sessionId         The session to request a push for.
   * @throws IOException
   */
  public void requestRegistrationPushChallenge(String sessionId, String gcmRegistrationId) throws IOException {
    pushServiceSocket.requestPushChallenge(sessionId, gcmRegistrationId);
  }

  public void checkNetworkConnection() throws IOException {
    this.pushServiceSocket.pingStorageService();
  }

  public void cancelInFlightRequests() {
    this.pushServiceSocket.cancelInFlightRequests();
  }

  public GroupsV2Api getGroupsV2Api() {
    return new GroupsV2Api(authWebSocket, pushServiceSocket, groupsV2Operations);
  }

  public RegistrationApi getRegistrationApi() {
    return new RegistrationApi(pushServiceSocket);
  }
}
