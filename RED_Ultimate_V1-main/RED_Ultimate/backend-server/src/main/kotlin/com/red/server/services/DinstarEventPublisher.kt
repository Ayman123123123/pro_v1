package com.red.server.services

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession

/**
 * ناشر أحداث Dinstar — يربط خدمات Dinstar الداخلية بـ DinstarWebSocketHandler للبث للعملاء.
 */
@Service
class DinstarEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    private val handlers = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<WebSocketSession>>()

    fun onClientConnected(redId: String, session: WebSocketSession) {
        val list = handlers.computeIfAbsent(redId) { java.util.concurrent.CopyOnWriteArrayList() }
        list.removeIf { !it.isOpen }
        list.add(session)
    }

    fun onClientDisconnected(redId: String, session: WebSocketSession) {
        handlers.computeIfPresent(redId) { _, list ->
            list.removeIf { it.id == session.id }
            list.takeIf { it.isNotEmpty() }
        }
    }

    fun publishPortStatus(status: Map<String, Any>) {
        handlers.values.forEach { list ->
            list.forEach { session ->
                if (session.isOpen) {
                    runCatching { session.sendMessage(
                        org.springframework.web.socket.TextMessage(
                            com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                mapOf("type" to "DINSTAR_PORT_STATUS", "data" to status)
                            )
                        )
                    ) }
                }
            }
        }
    }

    fun publishCdr(cdr: Map<String, Any>) {
        handlers.values.forEach { list ->
            list.forEach { session ->
                if (session.isOpen) {
                    runCatching { session.sendMessage(
                        org.springframework.web.socket.TextMessage(
                            com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                mapOf("type" to "DINSTAR_CDR", "data" to cdr)
                            )
                        )
                    ) }
                }
            }
        }
    }

    fun publishSms(sms: Map<String, Any>) {
        handlers.values.forEach { list ->
            list.forEach { session ->
                if (session.isOpen) {
                    runCatching { session.sendMessage(
                        org.springframework.web.socket.TextMessage(
                            com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                mapOf("type" to "DINSTAR_SMS", "data" to sms)
                            )
                        )
                    ) }
                }
            }
        }
    }

    fun publishUssd(ussd: Map<String, Any>) {
        handlers.values.forEach { list ->
            list.forEach { session ->
                if (session.isOpen) {
                    runCatching { session.sendMessage(
                        org.springframework.web.socket.TextMessage(
                            com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                mapOf("type" to "DINSTAR_USSD", "data" to ussd)
                            )
                        )
                    ) }
                }
            }
        }
    }

    fun publishException(exception: Map<String, Any>) {
        handlers.values.forEach { list ->
            list.forEach { session ->
                if (session.isOpen) {
                    runCatching { session.sendMessage(
                        org.springframework.web.socket.TextMessage(
                            com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                mapOf("type" to "DINSTAR_EXCEPTION", "data" to exception)
                            )
                        )
                    ) }
                }
            }
        }
    }
}