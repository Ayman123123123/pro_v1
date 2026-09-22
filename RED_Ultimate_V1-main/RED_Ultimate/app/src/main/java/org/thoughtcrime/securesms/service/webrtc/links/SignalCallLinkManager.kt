/**
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.service.webrtc.links

import io.reactivex.rxjava3.core.Single
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.GenericServerPublicParams
import org.signal.libsignal.zkgroup.calllinks.CallLinkAuthCredentialPresentation
import org.signal.libsignal.zkgroup.calllinks.CallLinkSecretParams
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredential
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialPresentation
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialRequestContext
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialResponse
import org.signal.network.NetworkResult
import org.signal.ringrtc.CallLinkRootKey
import org.signal.ringrtc.CallLinkState
import org.signal.ringrtc.CallLinkState.Restrictions
import org.signal.ringrtc.CallManager
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.REDNetwork
import com.red.sovereign.recipients.Recipient
import java.io.IOException

/**
 * Call Link manager which encapsulates CallManager and provides a stable interface.
 *
 * We can remove the outer sealed class once we have the final, working builds from core.
 */
class REDCallLinkManager(
  private val callManager: CallManager
) {

  private val genericServerPublicParams: GenericServerPublicParams = GenericServerPublicParams(
    AppDependencies.signalServiceNetworkAccess
      .getConfiguration()
      .genericServerPublicParams
  )

  private fun requestCreateCallLinkCredentialPresentation(
    linkRootKey: ByteArray,
    roomId: ByteArray
  ): CreateCallLinkCredentialPresentation {
    val userAci = Recipient.self().requireAci()
    val requestContext = CreateCallLinkCredentialRequestContext.forRoom(roomId)
    val request = requestContext.request

    Log.d(TAG, "Requesting call link credential response.")

    when (val result: NetworkResult<CreateCallLinkCredentialResponse> = REDNetwork.calling.createCallLinkCredential(request)) {
      is NetworkResult.Success -> {
        Log.d(TAG, "Requesting call link credential.")

        val createCallLinkCredential: CreateCallLinkCredential = requestContext.receiveResponse(
          result.result,
          userAci.libREDAci,
          genericServerPublicParams
        )

        Log.d(TAG, "Requesting and returning call link presentation.")

        return createCallLinkCredential.present(
          roomId,
          userAci.libREDAci,
          genericServerPublicParams,
          CallLinkSecretParams.deriveFromRootKey(linkRootKey)
        )
      }

      else -> throw IOException("Failed to create credential response", result.getCause())
    }
  }

  private fun requestCallLinkAuthCredentialPresentation(
    linkRootKey: ByteArray
  ): CallLinkAuthCredentialPresentation {
    return AppDependencies.groupsV2Authorization.getCallLinkAuthorizationForToday(
      genericServerPublicParams,
      CallLinkSecretParams.deriveFromRootKey(linkRootKey)
    )
  }

  fun createCallLink(
    callLinkCredentials: CallLinkCredentials
  ): Single<CreateCallLinkResult> {
    return Single.create { emitter ->
      Log.d(TAG, "Generating keys.")

      val rootKey = CallLinkRootKey(callLinkCredentials.linkKeyBytes)
      val adminPassKey: ByteArray = requireNotNull(callLinkCredentials.adminPassBytes)
      val roomId: ByteArray = rootKey.deriveRoomId()

      Log.d(TAG, "Generating credential.")
      val credentialPresentation = try {
        requestCreateCallLinkCredentialPresentation(
          rootKey.keyBytes,
          roomId
        )
      } catch (e: Exception) {
        Log.e(TAG, "Failed to create call link credential.", e)
        emitter.onSuccess(CreateCallLinkResult.Failure(-1))
        return@create
      }

      Log.d(TAG, "Creating call link.")

      val publicParams = CallLinkSecretParams.deriveFromRootKey(rootKey.keyBytes).publicParams

      // Credential
      callManager.createCallLink(
        REDStore.internal.groupCallingServer,
        credentialPresentation.serialize(),
        rootKey,
        adminPassKey,
        publicParams.serialize(),
        Restrictions.ADMIN_APPROVAL
      ) { result ->
        if (result.isSuccess) {
          Log.d(TAG, "Successfully created call link.")
          val rootKey = result.value!!.rootKey
          emitter.onSuccess(
            CreateCallLinkResult.Success(
              credentials = CallLinkCredentials(rootKey.keyBytes, adminPassKey),
              state = result.value!!.toAppState()
            )
          )
        } else {
          Log.w(TAG, "Failed to create call link with failure status ${result.status}")
          emitter.onSuccess(CreateCallLinkResult.Failure(result.status))
        }
      }
    }
  }

  fun readCallLink(
    credentials: CallLinkCredentials
  ): Single<ReadCallLinkResult> {
    return Single.create { emitter ->
      callManager.readCallLink(
        REDStore.internal.groupCallingServer,
        requestCallLinkAuthCredentialPresentation(credentials.linkKeyBytes).serialize(),
        CallLinkRootKey(credentials.linkKeyBytes)
      ) {
        if (it.isSuccess) {
          emitter.onSuccess(ReadCallLinkResult.Success(it.value!!.toAppState()))
        } else {
          Log.w(TAG, "Failed to read call link with failure status ${it.status}")
          emitter.onSuccess(ReadCallLinkResult.Failure(it.status))
        }
      }
    }
  }

  fun updateCallLinkName(
    credentials: CallLinkCredentials,
    name: String
  ): Single<UpdateCallLinkResult> {
    if (credentials.adminPassBytes == null) {
      return Single.just(UpdateCallLinkResult.NotAuthorized)
    }

    return Single.create { emitter ->
      val credentialPresentation = requestCallLinkAuthCredentialPresentation(credentials.linkKeyBytes)

      callManager.updateCallLinkName(
        REDStore.internal.groupCallingServer,
        credentialPresentation.serialize(),
        CallLinkRootKey(credentials.linkKeyBytes),
        credentials.adminPassBytes,
        name
      ) { result ->
        if (result.isSuccess) {
          emitter.onSuccess(UpdateCallLinkResult.Update(result.value!!.toAppState()))
        } else {
          emitter.onSuccess(UpdateCallLinkResult.Failure(result.status))
        }
      }
    }
  }

  fun updateCallLinkRestrictions(
    credentials: CallLinkCredentials,
    restrictions: Restrictions
  ): Single<UpdateCallLinkResult> {
    if (credentials.adminPassBytes == null) {
      return Single.just(UpdateCallLinkResult.NotAuthorized)
    }

    return Single.create { emitter ->
      val credentialPresentation = requestCallLinkAuthCredentialPresentation(credentials.linkKeyBytes)

      callManager.updateCallLinkRestrictions(
        REDStore.internal.groupCallingServer,
        credentialPresentation.serialize(),
        CallLinkRootKey(credentials.linkKeyBytes),
        credentials.adminPassBytes,
        restrictions
      ) { result ->
        if (result.isSuccess) {
          emitter.onSuccess(UpdateCallLinkResult.Update(result.value!!.toAppState()))
        } else {
          emitter.onSuccess(UpdateCallLinkResult.Failure(result.status))
        }
      }
    }
  }

  fun deleteCallLink(
    credentials: CallLinkCredentials
  ): Single<UpdateCallLinkResult> {
    if (credentials.adminPassBytes == null) {
      return Single.just(UpdateCallLinkResult.NotAuthorized)
    }

    return Single.create { emitter ->
      val credentialPresentation = requestCallLinkAuthCredentialPresentation(credentials.linkKeyBytes)

      callManager.deleteCallLink(
        REDStore.internal.groupCallingServer,
        credentialPresentation.serialize(),
        CallLinkRootKey(credentials.linkKeyBytes),
        credentials.adminPassBytes
      ) { result ->
        if (result.isSuccess && result.value == true) {
          emitter.onSuccess(UpdateCallLinkResult.Delete(credentials.roomId))
        } else {
          when (result.status) {
            409.toShort() -> emitter.onSuccess(UpdateCallLinkResult.CallLinkIsInUse)
            else -> emitter.onSuccess(UpdateCallLinkResult.Failure(result.status))
          }
        }
      }
    }
  }

  companion object {

    private val TAG = Log.tag(REDCallLinkManager::class.java)

    private fun CallLinkState.toAppState(): REDCallLinkState {
      return REDCallLinkState(
        name = name,
        expiration = expiration,
        restrictions = restrictions,
        revoked = hasBeenRevoked()
      )
    }
  }
}
