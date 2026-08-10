package com.red.sovereign.features.stories

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * RED Story ViewModel — manages ephemeral stories (24-hour content).
 */
@HiltViewModel
class StoryViewModel @Inject constructor() : ViewModel() {

    data class Story(
        val id: String,
        val authorId: String,
        val mediaUrl: String?,
        val caption: String?,
        val createdAt: Long,
        val expiresAt: Long
    )

    private val stories = mutableListOf<Story>()

    fun getActiveStories(): List<Story> {
        val now = System.currentTimeMillis()
        return stories.filter { it.expiresAt > now }
    }

    fun addStory(story: Story) {
        stories.add(story)
    }

    fun removeStory(id: String) {
        stories.removeAll { it.id == id }
    }
}
