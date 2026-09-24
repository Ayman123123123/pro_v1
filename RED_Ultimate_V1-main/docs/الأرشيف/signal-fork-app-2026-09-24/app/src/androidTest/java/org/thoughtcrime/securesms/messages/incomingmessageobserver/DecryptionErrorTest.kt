package com.red.sovereign.messages.incomingmessageobserver

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.red.sovereign.testing.incomingmessageobserver.IncomingMessageObserverAssertions.assertMessageReceived
import com.red.sovereign.testing.incomingmessageobserver.IncomingMessageObserverAssertions.assertNoMessageReceived
import com.red.sovereign.testing.incomingmessageobserver.IncomingMessageObserverRule

@RunWith(AndroidJUnit4::class)
class DecryptionErrorTest {

  @get:Rule
  val rule = IncomingMessageObserverRule(peerCount = 2)

  @Test
  fun malformedEnvelope_dropsMessage_butPipelineRecovers() {
    val peer = rule.peers[0]

    rule.deliver { malformedEnvelope() from peer }
    assertNoMessageReceived(from = peer, body = "subsequent")

    rule.deliver { text("subsequent") from peer }
    assertMessageReceived(from = peer, body = "subsequent")
  }
}
