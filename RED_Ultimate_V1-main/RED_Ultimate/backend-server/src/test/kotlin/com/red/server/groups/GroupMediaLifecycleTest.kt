package com.red.server.groups

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.media.MediaMetadata
import com.red.server.media.MediaService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.util.UUID

/** Group references must not delete uploader-owned images used by other resources. */
class GroupMediaLifecycleTest {
    private val mongo: MongoTemplate = mock()
    private val media: MediaService = mock()
    private val service = GroupService(mongo, mock<UserAccountRepository>(), media, mock<ApplicationEventPublisher>())
    private val owner = UUID.randomUUID()
    private val groupId = UUID.randomUUID().toString()
    private val oldKey = "users/$owner/${UUID.randomUUID()}.jpg"
    private val newKey = "users/$owner/${UUID.randomUUID()}.png"
    private val group = GroupDocument(id = groupId, name = "Example", description = null,
        ownerRedId = "12345", avatarMediaKey = oldKey)
    private val member = GroupMember(id = "$groupId:$owner", groupId = groupId,
        userId = owner.toString(), redId = "12345", username = "owner", role = GroupRole.OWNER)

    private fun mockExistingGroup() {
        whenever(mongo.findOne(any<Query>(), eq(GroupMember::class.java))).thenReturn(member)
        whenever(mongo.findById(groupId, GroupDocument::class.java)).thenReturn(group)
        whenever(mongo.find(any<Query>(), eq(GroupMember::class.java))).thenReturn(listOf(member))
    }

    @Test
    fun `changing the avatar retains the old shared object`() {
        mockExistingGroup()
        whenever(media.exists(newKey)).thenReturn(true)
        whenever(media.metadata(newKey)).thenReturn(MediaMetadata("image/png", 123))
        whenever(mongo.findById(groupId, GroupDocument::class.java))
            .thenReturn(group, group.copy(avatarMediaKey = newKey))

        val updated = service.updateAvatar(owner, groupId, UpdateGroupAvatarRequest(newKey))

        assertEquals("/api/media/$newKey", updated.avatarUrl)
        verify(mongo).updateFirst(any<Query>(), any<Update>(), eq(GroupDocument::class.java))
        verify(mongo, never()).save(any<GroupDocument>())
        verify(media, never()).delete(any())
    }

    @Test
    fun `deleting the group retains its shared avatar object`() {
        mockExistingGroup()
        service.delete(owner, groupId)
        verify(media, never()).delete(any())
        verify(mongo).remove(any<Query>(), eq(GroupDocument::class.java))
    }
}
