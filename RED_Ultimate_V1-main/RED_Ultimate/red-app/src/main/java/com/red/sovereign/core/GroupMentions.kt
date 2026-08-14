package com.red.sovereign.core

import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.groups.GroupMember

data class MentionCandidate(
    val redId: String,
    val username: String,
    val displayName: String,
)

/**
 * إشارات المجموعة بالاسم — بلا API جديد.
 *
 * النص يظهر `@username` ليقرأه البشر. حقل [RichMessage.mentions] يبقى
 * `@REDID` لأن الإشعار والتوجيه يعتمدان على المعرّف لا على الاسم.
 */
object GroupMentions {

    val AT_QUERY = Regex("""@([A-Za-z0-9_\u0600-\u06FF.]{0,20})$""")
    val NAME_TOKEN = Regex("""@([A-Za-z0-9_\u0600-\u06FF.]{2,32})""")

    fun query(text: String): String? = AT_QUERY.find(text)?.groupValues?.get(1)

    fun candidates(
        query: String,
        members: List<GroupMember>,
        friends: List<PublicRedProfile>,
        ownRedId: String,
    ): List<MentionCandidate> {
        val friendById = friends.associateBy { it.redId }
        return members
            .asSequence()
            .filter { it.redId != ownRedId }
            .map { member ->
                val friend = friendById[member.redId]
                MentionCandidate(
                    redId = member.redId,
                    username = member.username,
                    displayName = friend?.displayName?.ifBlank { member.username } ?: member.username.ifBlank { member.redId },
                )
            }
            .filter { candidate ->
                query.isBlank() ||
                    candidate.displayName.contains(query, ignoreCase = true) ||
                    candidate.username.contains(query, ignoreCase = true) ||
                    candidate.redId.contains(query)
            }
            .sortedBy { it.displayName }
            .take(6)
            .toList()
    }

    fun insert(text: String, username: String): String {
        val token = sanitizeUsername(username)
        return text.replace(AT_QUERY, "@$token ")
    }

    fun mentionIds(
        text: String,
        members: List<GroupMember>,
        friends: List<PublicRedProfile> = emptyList(),
    ): List<String> {
        val tokens = NAME_TOKEN.findAll(text).map { it.groupValues[1] }.toList()
        return tokens.mapNotNull { token ->
            when {
                YounesId.isValid(token) -> "@$token"
                else -> {
                    val member = members.firstOrNull { it.username.equals(token, ignoreCase = true) }
                    val friend = friends.firstOrNull { it.username.equals(token, ignoreCase = true) }
                    (member?.redId ?: friend?.redId)?.let { "@$it" }
                }
            }
        }.distinct().take(20)
    }

    fun displayLabel(
        redIdOrAt: String,
        members: List<GroupMember>,
        friends: List<PublicRedProfile>,
    ): String {
        val id = redIdOrAt.removePrefix("@")
        friends.firstOrNull { it.redId == id }?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        members.firstOrNull { it.redId == id }?.username?.takeIf { it.isNotBlank() }?.let { return it }
        return id
    }

    /** اسم يظهر فوق فقاعة المجموعة: صديق ثم username ثم المعرّف كاملاً — لا نقطع خمسة أرقام. */
    fun senderName(
        redId: String,
        members: List<GroupMember>,
        friends: List<PublicRedProfile>,
        ownRedId: String = "",
    ): String {
        if (redId.isNotBlank() && redId == ownRedId) return "أنت"
        return displayLabel(redId, members, friends)
    }

    fun highlightTokens(
        text: String,
        members: List<GroupMember>,
        friends: List<PublicRedProfile>,
    ): List<String> {
        val fromNames = NAME_TOKEN.findAll(text).map { it.value }.toList()
        val fromIds = members.map { "@${it.redId}" } + friends.map { "@${it.redId}" }
        return (fromNames + fromIds.filter { it in text }).distinct()
    }

    fun sanitizeUsername(username: String): String =
        username.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(20).ifBlank { username.take(20) }
}
