package com.red.sovereign.groups

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMembershipFingerprintTest {

    private fun member(redId: String, userId: String, role: String = "MEMBER") =
        GroupMember(
            id = "group:$userId",
            groupId = "group",
            userId = userId,
            redId = redId,
            username = "user$userId",
            role = role,
            joinedAt = "2026-09-09T00:00:00Z"
        )

    @Test
    fun `fingerprint is independent of server member ordering`() {
        val owner = member("10001", "owner", "OWNER")
        val participant = member("10002", "participant")

        assertEquals(
            GroupMembershipFingerprint.forMembers(listOf(owner, participant)),
            GroupMembershipFingerprint.forMembers(listOf(participant, owner))
        )
    }

    @Test
    fun `adding or removing a member rotates the fingerprint`() {
        val owner = member("10001", "owner", "OWNER")
        val participant = member("10002", "participant")

        assertNotEquals(
            GroupMembershipFingerprint.forMembers(listOf(owner)),
            GroupMembershipFingerprint.forMembers(listOf(owner, participant))
        )
    }

    @Test
    fun `changing a member role rotates the fingerprint`() {
        val owner = member("10001", "owner", "OWNER")
        val before = member("10002", "participant", "MEMBER")
        val after = member("10002", "participant", "ADMIN")

        assertNotEquals(
            GroupMembershipFingerprint.forMembers(listOf(owner, before)),
            GroupMembershipFingerprint.forMembers(listOf(owner, after))
        )
    }

    @Test
    fun `fingerprint is a full sha256 value`() {
        val fingerprint = GroupMembershipFingerprint.forMembers(listOf(member("10001", "owner", "OWNER")))

        assertTrue(fingerprint.matches(Regex("[0-9a-f]{64}")))
    }
}
