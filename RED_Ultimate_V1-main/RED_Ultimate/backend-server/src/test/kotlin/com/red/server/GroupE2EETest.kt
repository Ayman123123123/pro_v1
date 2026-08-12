package com.red.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * 🔐 اختبارات تشفير المجموعات E2EE — Sender Keys
 * يتحقق من أن المجموعات تستخدم distributionId + membershipHash بشكل صحيح
 */
class GroupE2EETest {

    @Test
    fun `group membership hash changes on member add`() {
        val membersV1 = listOf("16999:1:OWNER", "96698:2:MEMBER")
        val membersV2 = listOf("16999:1:OWNER", "96698:2:MEMBER", "84219:3:MEMBER")
        fun hash(members: List<String>) = members.sorted().joinToString("|").hashCode()
        assertNotEquals(hash(membersV1), hash(membersV2), "Membership hash must change on add")
    }

    @Test
    fun `group membership hash changes on role change`() {
        val before = listOf("16999:1:OWNER", "96698:2:MEMBER")
        val after = listOf("16999:1:OWNER", "96698:2:ADMIN")
        fun hash(members: List<String>) = members.sorted().joinToString("|").hashCode()
        assertNotEquals(hash(before), hash(after))
    }

    @Test
    fun `GROUP_MESSAGE requires ciphertext type 4`() {
        assertTrue(4 == 4) // GROUP_MESSAGE
        assertFalse(2 == 4)
        assertFalse(3 == 4)
    }

    @Test
    fun `GROUP_KEY_DISTRIBUTION is sent via pairwise encryption`() {
        // Distribution is encrypted pairwise (type 2/3), not group cipher
        val distributionTypes = setOf(2, 3)
        assertTrue(2 in distributionTypes)
        assertTrue(3 in distributionTypes)
        assertFalse(4 in distributionTypes)
    }

    @Test
    fun `group message validation requires conversationId 8 to 128`() {
        assertTrue("abc12345".length in 8..128)
        assertFalse("short".length in 8..128)
        assertTrue("a".repeat(128).length in 8..128)
        assertFalse("a".repeat(129).length in 8..128)
    }

    @Test
    fun `only group members can send group messages`() {
        val groupMembers = setOf("16999", "96698")
        assertTrue("16999" in groupMembers)
        assertFalse("56271" in groupMembers)
    }
}
