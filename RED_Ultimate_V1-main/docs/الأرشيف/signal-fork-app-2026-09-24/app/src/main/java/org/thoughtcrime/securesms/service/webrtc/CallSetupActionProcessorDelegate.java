package com.red.sovereign.service.webrtc;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallManager;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.events.WebRtcViewModel;
import com.red.sovereign.ringrtc.OutgoingVideoSourceRouter;
import com.red.sovereign.ringrtc.RemotePeer;
import com.red.sovereign.service.webrtc.state.WebRtcServiceState;
import org.signal.core.util.AppForegroundObserver;
import com.red.sovereign.webrtc.audio.REDAudioManager;
import com.red.sovereign.webrtc.locks.LockManager;

import static com.red.sovereign.webrtc.CallNotificationBuilder.TYPE_ESTABLISHED;

/**
 * Encapsulates the shared logic to setup a 1:1 call. Setup primarily includes retrieving turn servers and
 * transitioning to the connected state. Other action processors delegate the appropriate action to it but it is
 * not intended to be the main processor for the system.
 */
public class CallSetupActionProcessorDelegate extends WebRtcActionProcessor {

  public CallSetupActionProcessorDelegate(@NonNull WebRtcInteractor webRtcInteractor, @NonNull String tag) {
    super(webRtcInteractor, tag);
  }

  @Override
  public @NonNull WebRtcServiceState handleCallConnected(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    if (!remotePeer.callIdEquals(currentState.getCallInfoState().getActivePeer())) {
      Log.w(tag, "handleCallConnected(): Ignoring for inactive call.");
      return currentState;
    }

    Log.i(tag, "handleCallConnected(): call_id: " + remotePeer.getCallId());

    RemotePeer activePeer = currentState.getCallInfoState().requireActivePeer();

    webRtcInteractor.sendAcceptedCallEventSyncMessage(
      activePeer,
      currentState.getCallInfoState().getCallState() == WebRtcViewModel.State.CALL_RINGING,
      currentState.getCallSetupState(activePeer).isAcceptWithVideo() || currentState.getLocalDeviceState().getCameraState().isEnabled()
    );

    AppForegroundObserver.removeListener(webRtcInteractor.getForegroundListener());
    webRtcInteractor.startAudioCommunication();
    webRtcInteractor.activateCall(activePeer.getId());

    activePeer.connected();

    boolean localVideoEnabled  = currentState.getLocalDeviceState().getCameraState().isEnabled();
    boolean remoteVideoEnabled = currentState.getCallSetupState(activePeer).isRemoteVideoOffer();
    webRtcInteractor.updatePhoneState(WebRtcUtil.getInCallPhoneState(context, localVideoEnabled, remoteVideoEnabled));

    currentState = currentState.builder()
                               .actionProcessor(new ConnectedCallActionProcessor(webRtcInteractor))
                               .changeCallInfoState()
                               .callState(WebRtcViewModel.State.CALL_CONNECTED)
                               .callConnectedTime(System.currentTimeMillis())
                               .commit()
                               .changeLocalDeviceState()
                               .build();

    boolean isRemoteVideoOffer = currentState.getCallSetupState(activePeer).isRemoteVideoOffer();

    webRtcInteractor.setCallInProgressNotification(TYPE_ESTABLISHED, activePeer, isRemoteVideoOffer);
    webRtcInteractor.unregisterPowerButtonReceiver();

    try {
      CallManager callManager = webRtcInteractor.getCallManager();
      callManager.setAudioEnable(currentState.getLocalDeviceState().isMicrophoneEnabled());
      callManager.setVideoEnable(currentState.getLocalDeviceState().getCameraState().isEnabled(), false);
    } catch (CallException e) {
      return callFailure(currentState, "Enabling audio/video failed: ", e);
    }

    if (currentState.getCallSetupState(activePeer).isAcceptWithVideo()) {
      currentState = currentState.getActionProcessor().handleSetEnableVideo(currentState, true);
    }

    if (currentState.getCallSetupState(activePeer).isAcceptWithVideo() || currentState.getLocalDeviceState().getCameraState().isEnabled()) {
      webRtcInteractor.setDefaultAudioDevice(activePeer.getId(), REDAudioManager.AudioDevice.SPEAKER_PHONE, false);
    } else {
      webRtcInteractor.setDefaultAudioDevice(activePeer.getId(), REDAudioManager.AudioDevice.EARPIECE, false);
    }

    return currentState;
  }

  @Override
  protected @NonNull WebRtcServiceState handleSetEnableVideo(@NonNull WebRtcServiceState currentState, boolean enable) {
    Log.i(tag, "handleSetEnableVideo(): enable: " + enable);

    OutgoingVideoSourceRouter router = currentState.getVideoState().requireRouter();

    if (router.isInitialized()) {
      router.setEnabled(enable);
    }

    currentState = currentState.builder()
                               .changeLocalDeviceState()
                               .cameraState(router.getCameraState())
                               .build();

    //noinspection SimplifiableBooleanExpression
    if ((enable && router.isInitialized()) || !enable) {
      try {
        CallManager callManager = webRtcInteractor.getCallManager();
        callManager.setVideoEnable(enable, false);
      } catch (CallException e) {
        Log.w(tag, "Unable change video enabled state to " + enable, e);
      }
    }

    WebRtcUtil.enableSpeakerPhoneIfNeeded(webRtcInteractor, currentState);

    return currentState;
  }
}
