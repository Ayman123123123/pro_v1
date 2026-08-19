package com.red.sovereign.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * حارس تباين الألوان — WCAG 2.1.
 *
 * الخلفية: كانت اللوحة السابقة منسوخة من واتساب وتلجرام، وفيها عيبان
 * مقيسان لا مسألتَي ذوق: النص الأبيض على الزر الأساسي 3.03:1 — راسب
 * دون حد AA البالغ 4.5:1 — واللون الأحمر التحذيري 4.30:1.
 *
 * لون النص على زرٍّ لا يُقرأ ليس نقصًا تجميليًّا؛ المستخدم لا يرى ما
 * يضغطه. ولأن الألوان تُعدَّل بالذوق غالبًا، يلزم حارس يفشل عدديًّا
 * عند أي تراجع بدل الاعتماد على المراجعة البصرية.
 *
 * المعادلة من WCAG 2.1 SC 1.4.3 مطبَّقة كما هي، لا بتقريب.
 */
class ColorContrastTest {

    /** الإضاءة النسبية — WCAG 2.1 §relative luminance. */
    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    /** نسبة التباين بين لونين — من 1:1 إلى 21:1. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertAA(fg: Color, bg: Color, label: String) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$label = ${"%.2f".format(ratio)}:1 — دون حد AA البالغ 4.5:1",
            ratio >= 4.5
        )
    }

    private fun assertAAA(fg: Color, bg: Color, label: String) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$label = ${"%.2f".format(ratio)}:1 — دون حد AAA البالغ 7:1",
            ratio >= 7.0
        )
    }

    @Test
    fun `النص الأساسي يحقق AAA على كل الأسطح`() {
        assertAAA(YounesOnSurface, YounesMidnight, "النص الأساسي/الخلفية")
        assertAAA(YounesOnSurface, YounesSurface1, "النص الأساسي/سطح1")
        assertAAA(YounesOnSurface, YounesSurface2, "النص الأساسي/سطح2")
        assertAAA(YounesOnSurface, YounesSurface3, "النص الأساسي/سطح3")
    }

    @Test
    fun `النص الثانوي يحقق AA على كل الأسطح`() {
        assertAA(YounesOnSurfaceDim, YounesMidnight, "الثانوي/الخلفية")
        assertAA(YounesOnSurfaceDim, YounesSurface1, "الثانوي/سطح1")
        assertAA(YounesOnSurfaceDim, YounesSurface2, "الثانوي/سطح2")
        assertAA(YounesOnSurfaceDim, YounesSurface3, "الثانوي/سطح3")
    }

    @Test
    fun `نص الأزرار الملوّنة مقروء — العيب الأصلي`() {
        // كان أبيض على 00A884 = 3.03:1. الآن نصّ داكن على أخضر أعمق.
        assertAAA(YounesOnPrimary, YounesPrimary, "نص الزر/الأساسي")
        assertAAA(YounesOnAccent, YounesAccent, "نص الزر/الذهب")
    }

    @Test
    fun `ألوان الحالة مقروءة على الخلفية`() {
        assertAA(YounesPrimary, YounesMidnight, "الأساسي/الخلفية")
        assertAA(YounesAccent, YounesMidnight, "الذهب/الخلفية")
        assertAA(YounesRose, YounesMidnight, "الخطر/الخلفية")
        assertAA(YounesCobalt, YounesMidnight, "السماوي/الخلفية")
    }

    @Test
    fun `نص فقاعات المحادثة مقروء في الاتجاهين`() {
        assertAAA(YounesOnSurface, YounesBubbleIn, "النص/فقاعة واردة")
        assertAAA(YounesOnSurface, YounesBubbleOut, "النص/فقاعة صادرة")
        // الطابع الزمني داخل الفقاعة الصادرة يستعمل اللون الثانوي.
        assertAA(YounesOnSurfaceDim, YounesBubbleOut, "الثانوي/فقاعة صادرة")
    }

    @Test
    fun `الأسطح متمايزة بصريًّا لا رقميًّا فقط`() {
        // سلّم الأسطح يجب أن يُرى على شاشة رخيصة، فلا يكفي اختلاف القيمة.
        val steps = listOf(
            YounesMidnight to YounesSurface1,
            YounesSurface1 to YounesSurface2,
            YounesSurface2 to YounesSurface3,
        )
        steps.forEach { (lower, upper) ->
            assertTrue(
                "درجتا سطح متجاورتان غير متمايزتين",
                luminance(upper) > luminance(lower)
            )
        }
    }

    @Test
    fun `اللوحة لا تعود إلى ألوان المنافسين`() {
        // حارس هوية: هذه القيم بعينها كانت منسوخة حرفيًّا من واتساب
        // وتلجرام. عودتها تعني عودة العيوب المقيسة أعلاه معها.
        val forbidden = mapOf(
            0xFF00A884 to "أخضر واتساب",
            0xFF25D366 to "أخضر واتساب الفاتح",
            0xFF2AABEE to "أزرق تلجرام",
            0xFF0E1621 to "خلفية تلجرام الداكنة",
            0xFF2B5278 to "فقاعة تلجرام الصادرة",
        )
        val palette = listOf(
            YounesPrimary, YounesPrimaryGlow, YounesAccent, YounesAccentSoft,
            YounesCobalt, YounesRose, YounesMidnight, YounesSurface1,
            YounesSurface2, YounesSurface3, YounesBubbleIn, YounesBubbleOut,
        )
        // المقارنة بقنوات RGB الصحيحة لا بتمثيل `value` الداخلي:
        // الأخير تفصيلة تنفيذ في Compose قد تتغيّر، فيصمت الحارس بلا إنذار.
        palette.forEach { color ->
            val rgb = (color.red * 255).toInt().shl(16) or
                (color.green * 255).toInt().shl(8) or
                (color.blue * 255).toInt()
            forbidden.forEach { (banned, name) ->
                val bannedRgb = (banned and 0xFFFFFF).toInt()
                assertTrue(
                    "اللوحة عادت إلى $name (#${"%06X".format(bannedRgb)})",
                    rgb != bannedRgb
                )
            }
        }
    }
    /**
     * ألوان مشغّلي الشبكة تُعرض نصًّا/شارةً على خلفية التطبيق، فتخضع
     * لحدّ AA نفسه. الأربعة الأولى ألوان علامات تجارية تبقى كما هي،
     * أما `UNKNOWN` فليس مشغّلًا بل غياب تعرُّف، فلا هوية تلزمه:
     * كان `757575` بـ4.15:1 — راسبًا — فصار لون النص الثانوي نفسه.
     */
    @Test
    fun `الوان مشغلي الشبكة تبلغ حد AA على خلفية التطبيق`() {
        com.red.sovereign.features.dinstar.YemenOperator.entries.forEach { operator ->
            assertAA(operator.color, YounesMidnight, "لون ${operator.arabicName}")
        }
    }
}
