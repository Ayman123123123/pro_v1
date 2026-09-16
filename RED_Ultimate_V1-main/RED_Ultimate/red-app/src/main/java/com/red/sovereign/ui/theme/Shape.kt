package com.red.sovereign.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * RED Sovereign Shapes — نظام الأشكال Material 3 كامل
 *
 * حواف ناعمة وحديثة متوافقة مع Material 3.
 * 5 مستويات من الزوايا الدائرية تغطي كل المكونات.
 */
object RedShapes {

    // ═══════════════════════════════════════════════════════════════════════════════
    // Material 3 Shape Scale — 5 مستويات
    // ═══════════════════════════════════════════════════════════════════════════════

    /** افتراضي — للشاشات الرئيسية، البطاقات الكبيرة، الحاويات */
    val Default = Shapes(
        extraSmall = RoundedCornerShape(8.dp),   // أزرار، شرائح، حقول إدخال
        small = RoundedCornerShape(14.dp),       // بطاقات صغيرة، شرائح التصفية
        medium = RoundedCornerShape(20.dp),      // بطاقات متوسطة، مربعات حوار
        large = RoundedCornerShape(28.dp),       // بطاقات كبيرة، شرائط تنقل
        extraLarge = RoundedCornerShape(36.dp),  // شاشات كاملة، أوراق سفلية
    )

    /** أكثر استدارة — للمظهر الودود/الناعم (Liquid Glass style) */
    val Rounded = Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(18.dp),
        medium = RoundedCornerShape(28.dp),
        large = RoundedCornerShape(36.dp),
        extraLarge = RoundedCornerShape(48.dp),
    )

    /** أقل استدارة — للمظهر الحاد/الرسمي */
    val Sharp = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp),
    )

    /** للأشكال الدائرية الكاملة (أفاتار، أزرار عائمة) */
    val Circle = RoundedCornerShape(50.dp)

    // ═══════════════════════════════════════════════════════════════════════════════
    // أشكال مخصصة للمكونات الخاصة
    // ═══════════════════════════════════════════════════════════════════════════════

    /** فقاعات المحادثة — غير متماثلة للتمييز بين الصادرة/الواردة */
    object ChatBubble {
        val Outgoing = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 24.dp,
            bottomEnd = 4.dp    // زاوية صغيرة من جهة المرسل
        )
        val Incoming = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 4.dp,   // زاوية صغيرة من جهة المستقبل
            bottomEnd = 24.dp
        )
    }

    /** شريط التنقل السفلي */
    val BottomNavBar = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    /** الأشرطة العلوية */
    val TopAppBar = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

    /** البطاقات المرتفعة (Elevated cards) */
    val ElevatedCard = RoundedCornerShape(20.dp)

    /** البطاقات المملوءة (Filled cards) */
    val FilledCard = RoundedCornerShape(16.dp)

    /** البطاقات المحددة (Outlined cards) */
    val OutlinedCard = RoundedCornerShape(14.dp)

    /** مربعات الحوار */
    val Dialog = RoundedCornerShape(28.dp)

    /** الأوراق السفلية (Bottom sheets) */
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** القوائم المنسدلة */
    val DropdownMenu = RoundedCornerShape(14.dp)

    /** حقول الإدخال */
    val TextField = RoundedCornerShape(12.dp)

    /** الأزرار */
    val Button = RoundedCornerShape(12.dp)

    /** الشرائح (Chips) */
    val Chip = RoundedCornerShape(16.dp)

    /** مؤشرات التحميل الدائرية */
    val CircularIndicator = RoundedCornerShape(50.dp)

    /** صور الملف الشخصي */
    val Avatar = RoundedCornerShape(50.dp)
}