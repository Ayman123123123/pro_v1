package com.red.sovereign.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ألوان أسماء المرسلين في المجموعات — المصدر المركزي الوحيد (2026-09-26).
 *
 * سبب الوجود: كانت القائمة مكررة نصياً في 3 مواضع (ChatsScreen، RedDashboard،
 * ModernChatComponents) بعناصر متفاوتة (6 أو 7) — أي تعديل هوية لاحق كان سينسخ
 * في موضع وينسى في موضعين. اليوم موضع واحد:
 *
 * - اللوحة: 7 ألوان هادئة متناسقة مع هوية يونس (زمرد/ذهبي/كوبالت ملوّنة
 *   بنِسَب تشبّع أقل — «هادئة» عمداً كما وثّق التعليق الأصلي: لا مهرجان ألوان).
 * - الاختيار: تجزئة hash لمعرّف المرسل — ثابت لكل شخص عبر كل الشاشات والجلسات،
 *   وموزّع بانتظام على اللوحة (نفس المعادلة الموثقة سابقاً).
 */
private val SenderNamePalette: List<Color> = listOf(
    Color(0xFF6FD8B0), // زمرد هادئ
    Color(0xFF7FB5E0), // أزرق سماوي
    Color(0xFFF0C674), // ذهبي رملي
    Color(0xFFC9A7E8), // بنفسجي فاتح
    Color(0xFF8FC7E8), // أزرق فاتح
    Color(0xFFB5D8A0), // أخضر فاتح
    Color(0xFFE0B8A0)  // نحاسي دافئ
)

/** فهرس اللون الثابت لمعرّف مرسل — استخدمه مع [senderNameColor]. */
fun senderNameColorIndex(senderId: String): Int =
    kotlin.math.abs(senderId.hashCode()) % SenderNamePalette.size

/** لون اسم المرسل الثابت — نفس الشخص يظهر بنفس اللون في كل الشاشات. */
fun senderNameColor(index: Int): Color = SenderNamePalette[index % SenderNamePalette.size]
