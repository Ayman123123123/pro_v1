package com.red.server.services

import com.red.server.groups.GroupService
import com.red.server.stories.StoryService
import org.springframework.stereotype.Service

@Service
class CoreService(private val stories: StoryService, private val groups: GroupService) {
    fun getActiveStoriesCount(): Map<String, Long> = mapOf("activeStories" to stories.activeCount())
    fun getAggregatedStats(): Map<String, Any> = mapOf(
        "groups" to groups.count(),
        "activeStories" to stories.activeCount(),
        "timestamp" to System.currentTimeMillis()
    )
}
