package com.red.server.agents

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class ServerAgentRequest(
    val groupId: String,
    val agentId: String,
    val messageContent: String,
    val metadata: Map<String, Any> = emptyMap()
)

data class ServerAgentResponse(
    val agentId: String,
    val status: String,
    val resultText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@RestController
@RequestMapping("/api/v1/agents")
class RedServerAgentController {

    @PostMapping("/dispatch")
    fun dispatchAgent(@RequestBody request: ServerAgentRequest): ResponseEntity<ServerAgentResponse> {
        val responseText = "Server AI Agent [${request.agentId}] processed request for group ${request.groupId} successfully."
        return ResponseEntity.ok(
            ServerAgentResponse(
                agentId = request.agentId,
                status = "SUCCESS",
                resultText = responseText
            )
        )
    }

    @GetMapping("/health")
    fun getClusterHealth(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "clusterStatus" to "ONLINE",
                "activeAgents" to 20,
                "databases" to listOf("PostgreSQL", "Redis", "MongoDB"),
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
}
