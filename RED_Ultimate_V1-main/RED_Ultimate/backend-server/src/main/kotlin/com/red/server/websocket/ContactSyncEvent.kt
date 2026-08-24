package com.red.server.websocket

/**
 * 🔔 حدث تغيّر جهات الاتصال — يُنشر عند قبول/رفض طلب صداقة
 * يحوّل إلى WebSocket فوري CONTACT_SYNC لكل الطرفين
 */
data class ContactSyncEvent(
    val memberRedIds: List<String>
)
