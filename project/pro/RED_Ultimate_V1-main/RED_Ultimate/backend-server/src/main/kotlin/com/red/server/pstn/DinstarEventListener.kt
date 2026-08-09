package com.red.server.pstn

import org.asteriskjava.manager.ManagerEventListener
import org.asteriskjava.manager.event.ManagerEvent
import org.asteriskjava.manager.event.NewStateEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DinstarEventListener : ManagerEventListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onManagerEvent(event: ManagerEvent) {
        if (event is NewStateEvent) {
            val state = event.channelStateDesc ?: "UNKNOWN"
            val channel = event.channel ?: "unknown"
            val number = channel.substringAfter('/').substringBefore('@')
            log.info("DINSTAR line {} changed state to {}", number, state)
        }
    }
}
