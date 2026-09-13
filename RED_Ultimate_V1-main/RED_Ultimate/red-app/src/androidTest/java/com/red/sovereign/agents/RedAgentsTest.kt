package com.red.sovereign.agents

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.red.sovereign.core.RedSyncEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RedAgentsTest {

    @Test
    fun testAll20AgentsAreRegistered() {
        val agents = RedAgentRegistry.getAllAgents()
        assertEquals(20, agents.size)
    }

    @Test
    fun testModerationAgentFlagging() = runBlocking {
        val agent = GroupModerationAgent()
        val context = AgentContext(
            groupId = "group_1",
            senderId = "user_1",
            messageContent = "This is a spam message"
        )
        val result = agent.process(context)
        assertTrue(result is AgentResult.Flagged)
    }

    @Test
    fun testSyncEngine() = runBlocking {
        val syncEngine = RedSyncEngine()
        syncEngine.queueLocalChange("message", "Hello RED Ultimate")
        syncEngine.synchronizeWithServer()
        val health = syncEngine.getDatabaseHealthReport()
        assertTrue(health.containsKey("Server PostgreSQL"))
    }
}
