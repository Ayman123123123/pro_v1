package com.red.sovereign.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * نظام ثيم يونس السيادي — التعريفات الأساسية للمكوّنات الفاخرة
 * (أوبسيديان داكن + زجاج ضبابي + حواف نيون).
 *
 * ملاحظة معمارية: هذا الملف هو **المصدر الوحيد** لألوان `SovereignColors`
 * وتدرجات `SovereignGradients`. لا تُعرِّف ألوانًا موازية في ملفات المكوّنات.
 */

object SovereignColors {
    /*
     * ── توحيد اللوحات: SovereignColors يعيد تصدير قيم Younes ──────────────
     *
     * كانت هناك 4 لوحات منفصلة (SovereignColors هنا + Younes في RedTheme.kt
     * + متغيرات admin_dashboard + نيون المكوّنات) بقيم متقاربة لكن مختلفة:
     * ذهبي F59E0B مقابل E0B551، زمرد 10B981 مقابل 14C79A، سماوي 38BDF8
     * مقابل 4D9FE8 — فانحرف نفس الدور لونيًا بين الشاشات.
     *
     * القيم الأساسية الثلاث أدناه **مرادفات** لا نسخ: أي تعديل مستقبلي
     * على Younes ينعكس هنا تلقائيًا. الأسماء القديمة كلها باقية (توافق
     * رجعي)، والـneon يبقى متمايزًا لدور التوهج لا للسطح الأساسي.
     * يحرسها `ColorContrastTest` (Gold/Cyan على SurfaceNavy ≥ 3:1).
     */
    // ── الذهب الإمبراطوري والسيادة اليمنية ──────────────────────────────────
    /** ذهب يونس الأساسي — مرادف [YounesAccent] (#E0B551). */
    val Gold = YounesAccent
    val GoldLight = YounesAccentSoft
    val GoldDark = Color(0xFF8A6A0A)
    val GoldNeon = Color(0xFFFFD700)

    // ── الزمرد السيبراني والأمان / التشفير التام E2EE ───────────────────────
    /** زمرد يونس الأساسي — مرادف [YounesPrimary] (#14C79A). */
    val Emerald = YounesPrimary
    val EmeraldDark = Color(0xFF0A7A5E)
    val EmeraldNeon = Color(0xFF00E676)
    val Success = YounesPrimary

    // ── السماوي الكهربائي وإشارات WebRTC Mesh ───────────────────────────────
    /** أزرق يونس الملكي — مرادف [YounesCobalt] (#4D9FE8). */
    val Cyan = YounesCobalt
    val CyanDark = Color(0xFF2E7DA8)
    val CyanNeon = Color(0xFF00E5FF)
    val VoipBlue = Color(0xFF1565C0)

    // ── أسماء legacy للتوافق الرجعي: القيم السابقة قبل التوحيد ─────────────
    /** @deprecated استعمل [Gold] (= YounesAccent). باقٍ للتوافق فقط. */
    @Deprecated("موحّد إلى Gold=YounesAccent", ReplaceWith("SovereignColors.Gold"))
    val GoldLegacyF59E0B = Color(0xFFF59E0B)
    /** @deprecated استعمل [Emerald] (= YounesPrimary). باقٍ للتوافق فقط. */
    @Deprecated("موحّد إلى Emerald=YounesPrimary", ReplaceWith("SovereignColors.Emerald"))
    val EmeraldLegacy10B981 = Color(0xFF10B981)
    /** @deprecated استعمل [Cyan] (= YounesCobalt). باقٍ للتوافق فقط. */
    @Deprecated("موحّد إلى Cyan=YounesCobalt", ReplaceWith("SovereignColors.Cyan"))
    val CyanLegacy38BDF8 = Color(0xFF38BDF8)

    // ── الأوبسيديان العميق والأسطح الأكريليكية ──────────────────────────────
    // أسطح مرفوعة (lifted) لا ساحقة: 030712 يسحق التفاصيل على الشاشات
    // الرخيصة ويبدو أرخص من واتساب. القاعدة ≥0A0F18 للخلفية.
    val Obsidian = YounesVoid // مرفوع من 030712 — نفس الهوية، بلا سحق
    val ObsidianDeep = Color(0xFF0E1522) // مرفوع من 060A12
    val Navy = Color(0xFF0F172A)
    val SurfaceNavy = Color(0xFF1E293B)
    val SurfaceCard = Color(0xFF151F32)
    val SurfaceDialog = Color(0xFF1A263D)

    /*
     * ── أسماء الأسطح الدلالية ───────────────────────────────────────────────
     *
     * الأسماء أعلاه تصف *اللون* (أوبسيديان، نيفي)، وهذه تصف *الدور*
     * (شريط علوي، بطاقة). شاشات المكالمات كُتبت على الأسماء الدلالية وكانت
     * غير معرَّفة إطلاقاً، فأسقطت ترجمة الوحدة كلها في 30 موضعاً.
     *
     * تُعرَّف كمرادفات لا كقيم جديدة: لونان لنفس الدور ينحرفان مع الوقت.
     */

    /** سطح الشريط العلوي وخلفية الشاشات الداكنة — مرادف [Navy]. */
    val SurfaceDark = Navy

    /** سطح البطاقات والحقول فوق [SurfaceDark] — مرادف [SurfaceNavy]. */
    val SurfaceDarkVariant = SurfaceNavy

    // ── وهج الياقوت والتنبيهات / إنهاء المكالمة / البث المباشر ──────────────
    // حاويات الأزرار داكنة تحمل أبيض AA (Danger ‏4.98:1)؛ Warning يُقرن
    // بنص داكن YounesOnBrand (‏9.28:1) لا أبيض. النيون للتوهج على الداكن فقط.
    val Danger = Color(0xFFD32F2F)
    val DangerDark = Color(0xFFB91C1C)
    val RubyNeon = Color(0xFFFF1744)
    // مرادف Danger (مقياس واحد): كان نسخة #D32F2F مستقلة تنحرف مع الوقت.
    val LiveRed = Danger
    // ذهب Younes الموحد (مقياس واحد مع yns_warning): كان #F59E0B المستقل
    // يحمل أبيض 2.14:1 راسبًا؛ YounesAccent يحمل نصًا داكنًا 10.34:1.
    // ممنوع نص أبيض على Warning — استعمل WarningOn دائمًا.
    val Warning = YounesAccent
    val WarningOn = YounesOnBrand
    val WarningDark = GoldDark

    // ── بنفسج الفضاء ومؤتمرات SFU ───────────────────────────────────────────
    // YounesPurple B07CE8 = 6.30:1 على الخلفية (AA). SpaceAccent للعلامات،
    // SpaceContainer (أغمق) لحاويات الأزرار ذات النص الأبيض 7.04:1.
    val SpacePurple = YounesPurple
    val PurpleNeon = Color(0xFFC084FC)
    /** كوبالت ملكي 4D9FE8 = 6.80:1 على الخلفية (AA) — متمايز عن 2AABEE التلجرامي. */
    val RoyalCobalt = YounesCobalt
    val RoyalCobaltDeep = YounesCobaltDeep

    /*
     * ── ألوان مفصولة بالدور ────────────────────────────────────────────────
     *
     * اللون الواحد لا يصلح لدورين متناقضين في متطلّب التباين:
     *
     *   • **حاوية زر**: يُقاس نص الزرّ عليه ⇒ يلزم 4.5:1 على الأقل،
     *     فكلّما فتح اللون ساء.
     *   • **علامة على سطح داكن** (نقطة الحالة، عنوان القسم): يُقاس اللون
     *     نفسه على السطح ⇒ يلزم 3:1، فكلّما غمق اللون ساء.
     *
     * كان `LiveRed` و`SpacePurple` يؤدّيان الدورين معًا في شاشة
     * الاستكشاف، فرسب كلٌّ منهما في دوره الخطأ:
     *
     *   أبيض على LiveRed E53935    = 4.23:1  ❌ (نص زر «بدء بث»)
     *   SpacePurple 8E24AA على 1E293B = 2.08:1  ❌ (نقطة الحالة)
     *
     * فصلُ الدورين يُنجح الأربعة جميعًا. القيم أدناه محسوبة لا مقدَّرة،
     * ويحرسها `ColorContrastTest`.
     */

    /** حاوية زر البث — أبيض عليه 5.62:1. أغمق من `LiveRed` بدرجة واحدة. */
    val LiveContainer = Color(0xFFC62828)

    /** حاوية زر المساحات — أبيض عليه 7.04:1. */
    val SpaceContainer = Color(0xFF8E24AA)

    /** علامة البث على الأسطح الداكنة — 3.46:1 على SurfaceNavy. */
    val LiveAccent = Color(0xFFE53935)

    /** علامة المساحات على الأسطح الداكنة — 4.11:1 على SurfaceNavy. */
    val SpaceAccent = Color(0xFFBA68C8)

    // ── طبقات الزجاج الضبابي — Liquid Glass 2026 (شفافية + blur + عمق ضوئي) ─────
    // مصدر التصميم: واتساب/تليجرام 2026 — real-time blurring + frosted panels
    val GlassBg = Color(0x1F1E293B)
    val GlassBgLiquid = Color(0x99151F32) // 60% شفاف للـ Liquid Glass
    val GlassBorder = Color(0x3394A3B8)
    val GlassBorderLiquid = Color(0x4D94A3B8) // 30% للزجاج السائل
    val GlassHighlight = Color(0x22FFFFFF)
    val GlassShadow = Color(0x40000000)
    // ألوان الزجاج الفاتح
    val GlassBgLight = Color(0xB3FFFFFF) // 70% أبيض شفاف
    val GlassBorderLight = Color(0x330F1B2D)
}

object SovereignGradients {
    val gold = Brush.horizontalGradient(
        listOf(SovereignColors.GoldDark, SovereignColors.Gold, SovereignColors.GoldLight)
    )
    val emerald = Brush.horizontalGradient(
        listOf(SovereignColors.EmeraldDark, SovereignColors.Emerald, SovereignColors.EmeraldNeon)
    )
    val cyan = Brush.horizontalGradient(
        listOf(SovereignColors.CyanDark, SovereignColors.Cyan, SovereignColors.CyanNeon)
    )
    val danger = Brush.horizontalGradient(
        listOf(SovereignColors.DangerDark, SovereignColors.Danger, SovereignColors.RubyNeon)
    )
    val royal = Brush.horizontalGradient(
        listOf(SovereignColors.Navy, SovereignColors.CyanDark, SovereignColors.Cyan)
    )
    val live = Brush.horizontalGradient(
        listOf(Color(0xFFB71C1C), SovereignColors.LiveRed, SovereignColors.RubyNeon)
    )
    val space = Brush.horizontalGradient(
        listOf(Color(0xFF4A148C), SovereignColors.SpacePurple, SovereignColors.PurpleNeon)
    )

    val glassCard = Brush.linearGradient(
        listOf(
            Color(0x2E1E293B),
            Color(0x140F172A)
        )
    )

    // ─── Liquid Glass 2026 — تدرجات زجاجية سائلة شفافة (واتساب/تليجرام) ─────
    val liquidGlassCard = Brush.linearGradient(
        listOf(
            Color(0x99151F32), // 60% Body
            Color(0x801A263D)  // 50% depth
        )
    )
    val liquidGlassLight = Brush.linearGradient(
        listOf(
            Color(0xB3FFFFFF),
            Color(0x80F0F2F5)
        )
    )
    val liquidGlassBorder = Brush.linearGradient(
        listOf(
            Color(0x4D94A3B8),
            Color(0x3314C79A),
            Color(0x4D94A3B8)
        )
    )

    val neonBorderGold = Brush.linearGradient(
        listOf(
            SovereignColors.Gold.copy(alpha = 0.8f),
            SovereignColors.Gold.copy(alpha = 0.2f),
            SovereignColors.GoldLight.copy(alpha = 0.8f)
        )
    )

    val neonBorderEmerald = Brush.linearGradient(
        listOf(
            SovereignColors.Emerald.copy(alpha = 0.8f),
            SovereignColors.Emerald.copy(alpha = 0.2f),
            SovereignColors.EmeraldNeon.copy(alpha = 0.8f)
        )
    )

    val neonBorderCyan = Brush.linearGradient(
        listOf(
            SovereignColors.Cyan.copy(alpha = 0.8f),
            SovereignColors.Cyan.copy(alpha = 0.2f),
            SovereignColors.CyanNeon.copy(alpha = 0.8f)
        )
    )

    // ─── شبكية المحادثة والمكالمات — هالات ≤12% فوق أساس مرفوع ──────────
    // تُركَّب فوق GradientBackground لا بدلًا منه، حتى يبقى تباين النص
    // مقاسًا على الأساس المعتم لا على الهالة الشفافة.
    val meshChat = Brush.radialGradient(
        colors = listOf(
            YounesPrimary.copy(alpha = 0.08f),
            YounesCobalt.copy(alpha = 0.07f),
            Color.Transparent
        ),
        radius = 1100f
    )
    val meshCall = Brush.radialGradient(
        colors = listOf(
            YounesCobalt.copy(alpha = 0.10f),
            YounesPurple.copy(alpha = 0.07f),
            Color.Transparent
        ),
        radius = 1200f
    )
    val meshLight = Brush.radialGradient(
        colors = listOf(
            YounesPrimary.copy(alpha = 0.05f),
            Color.Transparent
        ),
        radius = 900f
    )
}
