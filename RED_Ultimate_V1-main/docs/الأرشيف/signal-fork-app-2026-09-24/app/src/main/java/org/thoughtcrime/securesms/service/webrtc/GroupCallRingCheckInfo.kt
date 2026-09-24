package com.red.sovereign.service.webrtc

import org.signal.core.models.ServiceId.ACI
import org.signal.ringrtc.CallManager
import com.red.sovereign.groups.GroupId
import com.red.sovereign.recipients.RecipientId

data class GroupCallRingCheckInfo(
  val recipientId: RecipientId,
  val groupId: GroupId.V2,
  val ringId: Long,
  val ringerAci: ACI,
  val ringUpdate: CallManager.RingUpdate
)
