package com.red.sovereign.stories

import com.red.sovereign.core.database.StoryEntity

/**
 * يحول cache Room محدود الحقول إلى نماذج عرض من دون فقدان metadata التي وصلَت
 * حديثًا من الخادم. لا يضيف هذا المخطط migration؛ لذلك عندما يكون التطبيق
 * غير متصل من أول تشغيل، تظهر هوية المالك RED ID بدل placeholder مضلل.
 */
internal fun Story.toCacheEntity() = StoryEntity(
    id = id,
    userId = ownerRedId,
    mediaUrl = mediaUrl,
    mediaType = mediaType,
    caption = caption,
    timestamp = storyTimestamp(createdAt),
    expiresAt = storyTimestamp(expiresAt),
)

internal fun mergeCachedStories(
    cached: List<StoryEntity>,
    inMemory: List<Story>,
): List<Story> {
    val currentById = inMemory.associateBy(Story::id)
    return cached.map { entity ->
        val existing = currentById[entity.id]
        if (existing != null) {
            existing.copy(
                mediaUrl = entity.mediaUrl,
                mediaType = entity.mediaType,
                caption = entity.caption,
                createdAt = entity.timestamp.toString(),
                expiresAt = entity.expiresAt.toString(),
            )
        } else {
            Story(
                id = entity.id,
                ownerRedId = entity.userId,
                ownerUsername = entity.userId,
                ownerDisplayName = entity.userId,
                mediaUrl = entity.mediaUrl,
                mediaType = entity.mediaType,
                caption = entity.caption,
                createdAt = entity.timestamp.toString(),
                expiresAt = entity.expiresAt.toString(),
            )
        }
    }
}
