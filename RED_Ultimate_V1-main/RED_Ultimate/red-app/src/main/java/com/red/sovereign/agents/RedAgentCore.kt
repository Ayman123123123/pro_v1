package com.red.sovereign.agents

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Core Agent Framework for RED Ultimate Messenger
 * Defines the base structure, event bus, and execution context for the 20+ specialized AI agents.
 */

data class AgentContext(
    val eventId: String = UUID.randomUUID().toString(),
    val groupId: String,
    val senderId: String,
    val messageContent: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)

sealed class AgentResult {
    data class Success(val responseText: String, val actionPayload: Map<String, Any> = emptyMap()) : AgentResult()
    data class Flagged(val reason: String, val severity: Int) : AgentResult()
    object Ignored : AgentResult()
}

interface RedAiAgent {
    val agentId: String
    val agentName: String
    val description: String
    val isActive: Boolean

    suspend fun process(context: AgentContext): AgentResult
}

class AgentOrchestrator {
    private val agents = mutableMapOf<String, RedAiAgent>()
    private val _agentActivityState = MutableStateFlow<Map<String, String>>(emptyMap())
    val agentActivityState: StateFlow<Map<String, String>> = _agentActivityState.asStateFlow()

    fun registerAgent(agent: RedAiAgent) {
        agents[agent.agentId] = agent
    }

    suspend fun dispatchEvent(context: AgentContext): List<Pair<String, AgentResult>> {
        val results = mutableListOf<Pair<String, AgentResult>>()
        for ((id, agent) in agents) {
            if (agent.isActive) {
                try {
                    val result = agent.process(context)
                    results.add(id to result)
                    updateActivity(id, result.toString())
                } catch (e: Exception) {
                    results.add(id to AgentResult.Flagged("Error: ${e.localizedMessage}", 1))
                }
            }
        }
        return results
    }

    private fun updateActivity(agentId: String, status: String) {
        val current = _agentActivityState.value.toMutableMap()
        current[agentId] = status
        _agentActivityState.value = current
    }
}
