package com.red.server.services

import com.red.server.groups.GroupService
import com.red.server.stories.StoryService
import org.springframework.stereotype.Service

/**
 * Thin groups/stories aggregate for legacy admin compat surfaces.
 *
 * This is intentionally NOT the system-wide stats source: complete live
 * metrics (users, messages, delivery rate, approvals, DB health) live in
 * [MasterStatsService.getLiveMetrics], and [MasterOrchestrationService]
 * covers the cross-store rollup. Keep this class limited to stories/groups
 * so the three surfaces cannot drift apart again.
 */
@Service
class CoreService(private val stories: StoryService, private val groups: GroupService) {
    fun getActiveStoriesCount(): Map<String, Long> = mapOf("activeStories" to stories.activeCount())
    fun getAggregatedStats(): Map<String, Any> = mapOf(
        "groups" to groups.count(),
        "activeStories" to stories.activeCount(),
        "timestamp" to System.currentTimeMillis()
    )
}
