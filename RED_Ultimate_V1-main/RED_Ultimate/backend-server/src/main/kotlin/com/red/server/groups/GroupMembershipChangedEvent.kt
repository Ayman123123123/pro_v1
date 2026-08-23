package com.red.server.groups

/**
 * 🔔 حدث تغيّر عضوية المجموعة — يُنشر داخلياً ويُحوَّل إلى إشعار WebSocket فوري (GROUP_SYNC)
 * لكل الأعضاء المتصلين، فيحدّثون قوائمهم ومفاتيح Sender Key تلقائياً دون انتظار إعادة تشغيل.
 */
data class GroupMembershipChangedEvent(
    val groupId: String,
    val memberRedIds: List<String>
)
