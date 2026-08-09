package com.red.sovereign.service.webrtc.state

import android.content.Intent
import com.red.sovereign.components.sensors.Orientation
import com.red.sovereign.events.CallParticipant
import com.red.sovereign.ringrtc.CameraState
import com.red.sovereign.webrtc.audio.REDAudioManager
import org.webrtc.PeerConnection

/**
 * Local device specific state.
 */
data class LocalDeviceState(
  var cameraState: CameraState = CameraState.UNKNOWN,
  var isMicrophoneEnabled: Boolean = true,
  var orientation: Orientation = Orientation.PORTRAIT_BOTTOM_EDGE,
  var isLandscapeEnabled: Boolean = false,
  var deviceOrientation: Orientation = Orientation.PORTRAIT_BOTTOM_EDGE,
  var activeDevice: REDAudioManager.AudioDevice = REDAudioManager.AudioDevice.NONE,
  var availableDevices: Set<REDAudioManager.AudioDevice> = emptySet(),
  var bluetoothPermissionDenied: Boolean = false,
  var isAudioDeviceChangePending: Boolean = false,
  var networkConnectionType: PeerConnection.AdapterType = PeerConnection.AdapterType.UNKNOWN,
  var handRaisedTimestamp: Long = CallParticipant.HAND_LOWERED,
  var remoteMutedBy: CallParticipant? = null,
  var isScreenSharing: Boolean = false,
  var mediaProjectionIntent: Intent? = null
) {

  fun duplicate(): LocalDeviceState {
    return copy()
  }
}
