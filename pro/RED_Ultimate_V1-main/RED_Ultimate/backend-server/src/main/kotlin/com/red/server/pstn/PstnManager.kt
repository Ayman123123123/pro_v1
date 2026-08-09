package com.red.server.pstn

import jakarta.annotation.PreDestroy
import org.asteriskjava.manager.DefaultManagerConnection
import org.asteriskjava.manager.action.OriginateAction
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PstnManager(
    @Value("\${ASTERISK_AMI_HOST:red-pstn-gateway}") private val amiHost: String,
    @Value("\${ASTERISK_AMI_USER:red_admin}") private val amiUser: String,
    @Value("\${ASTERISK_AMI_PASSWORD:}") private val amiPassword: String
) {
    private val connectionDelegate = lazy {
        require(amiPassword.isNotBlank()) { "ASTERISK_AMI_PASSWORD must be configured" }
        DefaultManagerConnection(amiHost, amiUser, amiPassword).also { it.login() }
    }
    private val connection by connectionDelegate

    fun dialGsm(phoneNumber: String): String {
        require(phoneNumber.matches(Regex("^\\+?[0-9]{6,15}$"))) { "Invalid phone number" }
        val correlationId = UUID.randomUUID().toString()
        val action = OriginateAction().apply {
            // A Local channel forces every call through the restricted backend-only dialplan context.
            actionId = correlationId
            channel = "Local/$phoneNumber@from-red-backend"
            application = "Wait"
            data = "1"
            callerId = "RED SOVEREIGN"
            setAsync(true)
        }
        val response = connection.sendAction(action)
        check(response.response?.equals("Success", ignoreCase = true) == true) {
            response.message ?: "Asterisk rejected originate action"
        }
        return correlationId
    }

    @PreDestroy
    fun close() {
        if (connectionDelegate.isInitialized()) runCatching { connection.logoff() }
    }
}
