package com.red.sovereign.features.communities.simple

import kotlinx.serialization.Serializable

/**
 * P1-B — نماذج المجتمعات (عميل بسيط).
 *
 * تطابق حمولة الخادم `CommunityResponse` في
 * `backend-server/.../social/CommunitiesController.kt` حرفًا بحرف
 * (نفس أسماء الحقول) ليعمل فك الترميز مع `ignoreUnknownKeys`.
 * كل الحقول الجديدة مستقبلًا في الخادم تُتجاهل تلقائيًا.
 */
@Serializable
data class Community(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String = "GENERAL",
    val tags: List<String> = emptyList(),
    val isPublic: Boolean = true,
    val createdBy: String,
    val createdByUsername: String,
    val avatarColor: String = "#45B7D1",
    val rules: String? = null,
    val memberCount: Long = 0L,
    val myRole: String? = null,
    val isJoined: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)
