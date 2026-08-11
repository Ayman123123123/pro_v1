package com.red.server.pstn

import com.red.server.calls.CallHistoryService
import org.asteriskjava.manager.ManagerEventListener
import org.asteriskjava.manager.event.ManagerEvent
import org.asteriskjava.manager.event.NewStateEvent
import org.asteriskjava.manager.event.HangupEvent
import org.asteriskjava.manager.event.BridgeEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Asterisk AMI event listener that tracks DINSTAR channel state changes
 * and correlates them with call history records.
 */
@Component
class DinstarEventListener(
    private val history: CallHistoryService,
    private val loadBalancer: DinstarLoadBalancer
) : ManagerEventListener {
    companion object { private val log = LoggerFactory.getLogger(DinstarEventListener::class.java) }

    override fun onManagerEvent(event: ManagerEvent) {
        when (event) {
            is NewStateEvent -> handleStateChange(event)
            is HangupEvent -> handleHangup(event)
            is BridgeEvent -> handleBridge(event)
            else -> Unit // Ignore unhandled event types
        }
    }

    private fun handleStateChange(event: NewStateEvent) {
        val state = event.channelStateDesc ?: return
        val channel = event.channel ?: return
        val lineNumber = channel.substringAfter('/').substringBefore('@')

        log.info("DINSTAR line {} state → {} (channel={})", lineNumber, state, channel)

        when (state) {
            "Up" -> {
                log.info("Line {} answered", lineNumber)
            }
            "Ringing" -> {
                log.debug("Line {} ringing", lineNumber)
            }
        }
    }

    private fun handleHangup(event: HangupEvent) {
        val channel = event.channel ?: return
        val lineNumber = channel.substringAfter('/').substringBefore('@')
        val cause = event.causeTxt ?: "UNKNOWN"

        log.info("DINSTAR line {} hung up — cause: {}", lineNumber, cause)

        // Automatic port release in load balancer when channel hangs up from Asterisk / Dinstar
        val port = lineNumber.filter { it.isDigit() }.toIntOrNull()
        if (port != null && port in 0..7) {
            loadBalancer.releasePort(port)
            log.info("DINSTAR port {} released in load balancer on Asterisk hangup", port)
        }
    }

    private fun handleBridge(event: BridgeEvent) {
        log.debug("Bridge event: channel1={}, channel2={}", event.channel1, event.channel2)
    }
}
