package com.red.sovereign.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.red.sovereign.R

/**
 * نظام الخطوط العربية الكامل — Material 3 + RTL أولوية
 *
 * IBM Plex Sans Arabic (Variable font) — رئيسي، ثنائي النص، SIL OFL 1.1
 * Noto Sans Arabic ك fallback شامل
 * Amiri للـ Quranic/Religious texts
 *
 * جميع الخطوط مضمّنة محليًا في res/font — لا تحميل شبكة، لا Google Fonts provider.
 * Works offline on Yemen's networks.
 */
object RedFont {

    // ═══════════════════════════════════════════════════════════════════════════════
    // IBM Plex Sans Arabic — عائلة الخط الأساسية (Variable font محلية)
    // أوزان كاملة 100-900 + Italic حيث متاح
    // ═══════════════════════════════════════════════════════════════════════════════

    val PlexArabicFamily = FontFamily(
        Font(R.font.plex_arabic_thin, FontWeight.Thin, FontStyle.Normal),
        Font(R.font.plex_arabic_thin, FontWeight.Thin, FontStyle.Italic),
        Font(R.font.plex_arabic_extralight, FontWeight.ExtraLight, FontStyle.Normal),
        Font(R.font.plex_arabic_extralight, FontWeight.ExtraLight, FontStyle.Italic),
        Font(R.font.plex_arabic_light, FontWeight.Light, FontStyle.Normal),
        Font(R.font.plex_arabic_light, FontWeight.Light, FontStyle.Italic),
        Font(R.font.plex_arabic_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.plex_arabic_regular, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.plex_arabic_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.plex_arabic_medium, FontWeight.Medium, FontStyle.Italic),
        Font(R.font.plex_arabic_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.plex_arabic_semibold, FontWeight.SemiBold, FontStyle.Italic),
        Font(R.font.plex_arabic_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.plex_arabic_bold, FontWeight.Bold, FontStyle.Italic),
        Font(R.font.plex_arabic_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(R.font.plex_arabic_extrabold, FontWeight.ExtraBold, FontStyle.Italic),
        Font(R.font.plex_arabic_black, FontWeight.Black, FontStyle.Normal),
        Font(R.font.plex_arabic_black, FontWeight.Black, FontStyle.Italic),
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // Noto Sans Arabic — Fallback شامل لجميع الحروف العربية + رموز Unicode
    // يغطي الحروف النادرة، التشكيل، والرموز التي قد لا توجد في Plex
    // ═══════════════════════════════════════════════════════════════════════════════

    val NotoSansArabicFamily = FontFamily(
        Font(R.font.noto_sans_arabic_thin, FontWeight.Thin, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_extralight, FontWeight.ExtraLight, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_light, FontWeight.Light, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_black, FontWeight.Black, FontStyle.Normal),
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // Amiri — خط قرآن/نصوص دينية تقليدي (Naskh style)
    // يستخدم للنصوص القرآنية، الأذكار، المحتوى الديني
    // ═══════════════════════════════════════════════════════════════════════════════

    val AmiriFamily = FontFamily(
        Font(R.font.amiri_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.amiri_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.amiri_regular, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.amiri_bold, FontWeight.Bold, FontStyle.Italic),
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // Noto Color Emoji — Fallback للرموز والإيموجي
    // يضمن عرض الإيموجي بشكل صحيح على جميع الأجهزة
    // ═══════════════════════════════════════════════════════════════════════════════

    val NotoColorEmojiFamily = FontFamily(
        Font(R.font.noto_color_emoji, FontWeight.Normal, FontStyle.Normal),
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // عائلة الخط الأساسية للتطبيق — Plex Arabic مع Noto كبديل + Emoji
    // تُستخدم في MaterialTheme.typography
    // ═══════════════════════════════════════════════════════════════════════════════

    val AppFontFamily = FontFamily(
        // Plex Arabic weights
        Font(R.font.plex_arabic_thin, FontWeight.Thin, FontStyle.Normal),
        Font(R.font.plex_arabic_thin, FontWeight.Thin, FontStyle.Italic),
        Font(R.font.plex_arabic_extralight, FontWeight.ExtraLight, FontStyle.Normal),
        Font(R.font.plex_arabic_extralight, FontWeight.ExtraLight, FontStyle.Italic),
        Font(R.font.plex_arabic_light, FontWeight.Light, FontStyle.Normal),
        Font(R.font.plex_arabic_light, FontWeight.Light, FontStyle.Italic),
        Font(R.font.plex_arabic_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.plex_arabic_regular, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.plex_arabic_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.plex_arabic_medium, FontWeight.Medium, FontStyle.Italic),
        Font(R.font.plex_arabic_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.plex_arabic_semibold, FontWeight.SemiBold, FontStyle.Italic),
        Font(R.font.plex_arabic_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.plex_arabic_bold, FontWeight.Bold, FontStyle.Italic),
        Font(R.font.plex_arabic_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(R.font.plex_arabic_extrabold, FontWeight.ExtraBold, FontStyle.Italic),
        Font(R.font.plex_arabic_black, FontWeight.Black, FontStyle.Normal),
        Font(R.font.plex_arabic_black, FontWeight.Black, FontStyle.Italic),
        // Noto Sans Arabic fallback
        Font(R.font.noto_sans_arabic_thin, FontWeight.Thin, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_extralight, FontWeight.ExtraLight, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_light, FontWeight.Light, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_black, FontWeight.Black, FontStyle.Normal),
        // Noto Color Emoji for emoji
        Font(R.font.noto_color_emoji, FontWeight.Normal, FontStyle.Normal),
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // عائلات متخصصة للاستخدام المباشر
    // ═══════════════════════════════════════════════════════════════════════════════

    /** للعناوين الرئيسية - وزن ثقيل للظهور القوي */
    val DisplayFontFamily = FontFamily(
        Font(R.font.plex_arabic_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(R.font.plex_arabic_black, FontWeight.Black, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_black, FontWeight.Black, FontStyle.Normal),
    )

    /** للعناوين - وزن Bold/SemiBold */
    val HeadlineFontFamily = FontFamily(
        Font(R.font.plex_arabic_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.plex_arabic_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.plex_arabic_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_semibold, FontWeight.SemiBold, FontStyle.Normal),
    )

    /** للنصوص الطويلة - وزن Normal/Medium لسهولة القراءة */
    val BodyFontFamily = FontFamily(
        Font(R.font.plex_arabic_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.plex_arabic_regular, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.plex_arabic_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.plex_arabic_medium, FontWeight.Medium, FontStyle.Italic),
        Font(R.font.noto_sans_arabic_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_medium, FontWeight.Medium, FontStyle.Normal),
    )

    /** للأزرار والتسميات - وزن SemiBold/Medium */
    val LabelFontFamily = FontFamily(
        Font(R.font.plex_arabic_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.plex_arabic_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.plex_arabic_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.noto_sans_arabic_medium, FontWeight.Medium, FontStyle.Normal),
    )

    /** للنصوص الدينية/القرآنية - Amiri Naskh */
    val QuranicFontFamily = AmiriFamily

    // ═══════════════════════════════════════════════════════════════════════════════
    // دوال مساعدة
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * اختيار عائلة الخط حسب نوع المحتوى
     */
    fun familyForContentType(contentType: ContentType): FontFamily = when (contentType) {
        ContentType.DISPLAY -> DisplayFontFamily
        ContentType.HEADLINE -> HeadlineFontFamily
        ContentType.BODY -> BodyFontFamily
        ContentType.LABEL -> LabelFontFamily
        ContentType.QURANIC -> QuranicFontFamily
        ContentType.DEFAULT -> AppFontFamily
    }

    /**
     * Preload الخطوط الحرجة في Application class
     * يستدعى مرة واحدة عند بدء التطبيق
     */
    @Suppress("UNUSED_PARAMETER")
    fun preloadCriticalFonts(context: android.content.Context) {
        // Preload يتم تلقائياً عند أول استخدام للخط عبر FontFamily
        // يمكن إضافة preloading صريح هنا إن لزم
    }

    enum class ContentType {
        DISPLAY, HEADLINE, BODY, LABEL, QURANIC, DEFAULT
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// توافق رجعي — الأسماء القديمة
// ═══════════════════════════════════════════════════════════════════════════════

/** @deprecated استعمل RedFont.PlexArabicFamily مباشرة */
@Deprecated("استعمل RedFont.PlexArabicFamily مباشرة", ReplaceWith("RedFont.PlexArabicFamily"))
val PlexArabicFamily: FontFamily = RedFont.PlexArabicFamily

/** @deprecated استعمل RedFont.AppFontFamily مباشرة */
@Deprecated("استعمل RedFont.AppFontFamily مباشرة", ReplaceWith("RedFont.AppFontFamily"))
val AppFontFamily: FontFamily = RedFont.AppFontFamily

/** @deprecated استعمل RedFont.NotoSansArabicFamily مباشرة */
@Deprecated("استعمل RedFont.NotoSansArabicFamily مباشرة", ReplaceWith("RedFont.NotoSansArabicFamily"))
val NotoSansArabicFamily: FontFamily = RedFont.NotoSansArabicFamily