package com.red.sovereign.ui

import com.red.sovereign.core.YounesId
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * تفكيك RedDashboard الوحش (~3970 سطر): المعرفات والثوابت المشتركة — نُقلت كاملة من
 * ذيل RedDashboard.kt إلى هنا (نفس الحزمة، نفس الأسماء، نفس السلوك) ليبقى ملف
 * واحد مصدراً للحقيقة ويُحذف التكرار:
 * - RED_ID_PATTERN من core/YounesId.kt (كان مكرراً في QrScannerSheet/SafetyViewModel).
 * - RED_ID_PARTIAL / EMOJI_CATEGORIES / ATTACHMENT_JSON / conversationId.
 * RedDashboard.kt يستخدمها مباشرة بلا import (نفس الحزمة) — لا تغيير في المنادين.
 */

// مصدر الحقيقة الوحيد: core/YounesId.kt.
internal val RED_ID_PATTERN = Regex(YounesId.PATTERN)
// نسخة بدون ^ و $ لاستخدامها داخل نص (مثل @12345)
internal val RED_ID_PARTIAL = Regex(YounesId.MENTION_PATTERN)
// الهاشتاجات العربية/اللاتينية — الهاشتاج لـ # autocomplete
// قائمة الإيموجي السريعة للتفاعل — تظهر أعلى قائمة إجراءات الرسالة.
internal val EMOJI_CATEGORIES = listOf(
    "سريعة" to listOf("😀", "😂", "😍", "👍", "❤️", "🔥", "👏", "🙏", "🎉", "😢", "😮", "✅"),
    "الوجوه" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🙂", "🙃", "😉", "😊", "🥰", "😍", "🤩", "😘", "😋", "😎", "🤔", "😴", "😭", "😡", "🥳"),
    "الإشارات" to listOf("👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "👏", "🙌", "🫶", "🤝", "🙏", "💪", "👀", "❤️", "💚", "💛", "💙"),
    "الأشياء" to listOf("📱", "💻", "⌚", "📷", "🎥", "🎙️", "🔒", "🔑", "💡", "📌", "📎", "📁", "📄", "📚", "🎁", "🏆", "✅", "⚠️"),
    "الطبيعة" to listOf("🌙", "☀️", "⭐", "🔥", "🌈", "🌹", "🌿", "🌳", "🌊", "⛰️", "🐪", "🦅", "🐝", "🦋"),
    "الطعام" to listOf("☕", "🍵", "🥤", "🍞", "🥐", "🍚", "🍗", "🥗", "🍎", "🍉", "🍇", "🍯", "🎂"),
    "السفر" to listOf("🚗", "🚕", "🚌", "✈️", "🚁", "🚢", "🗺️", "🏠", "🏢", "🏥", "🏫", "🕌", "⛺"),
    "الرموز" to listOf("✅", "❌", "⚠️", "❗", "❓", "💯", "➕", "➖", "♻️", "🔴", "🟢", "🟡", "🔵", "🇾🇪")
)
internal val ATTACHMENT_JSON = Json { ignoreUnknownKeys = true }

internal fun conversationId(first: String, second: String): String {
    if (first.isBlank() || second.isBlank()) return "pending-conversation"
    val canonical = listOf(first, second).sorted().joinToString("|")
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
