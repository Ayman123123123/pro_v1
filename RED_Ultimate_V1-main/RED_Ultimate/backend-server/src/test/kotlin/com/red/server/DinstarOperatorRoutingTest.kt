package com.red.server

import com.red.server.pstn.DinstarLoadBalancer.Companion.classifyNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * تصنيف المشغل اليمني — مبني على خطة الترقيم الوطنية.
 *
 * البادئات الخاطئة ليست خطأً تجميليًا: التصنيف يغذّي وزن «داخل الشبكة»
 * في `DinstarLoadBalancer`، فبادئة معكوسة تعني اختيار شريحة على شبكة
 * أخرى واحتساب المكالمة بتعرفة أعلى.
 *
 * `classifyNumber` دالة نقيّة في الـ companion، فتُختبر بلا بناء الخدمة
 * (التي تتطلب اتصال قاعدة بيانات وعميل أجهزة).
 */
class DinstarOperatorRoutingTest {

    @Test
    fun `كل بادئة محمول تُنسب إلى مشغلها الصحيح`() {
        assertEquals("Sabafon", classifyNumber("711234567")?.apiName)
        assertEquals("YOU", classifyNumber("731234567")?.apiName)
        assertEquals("YemenMobile", classifyNumber("771234567")?.apiName)
        assertEquals("YemenMobile", classifyNumber("781234567")?.apiName)
        assertEquals("YTelecom", classifyNumber("701234567")?.apiName)
    }

    @Test
    fun `الصيغة الدولية وصيغة الصفر تُطبَّعان قبل التصنيف`() {
        val expected = "Sabafon"
        assertEquals(expected, classifyNumber("711234567")?.apiName)
        assertEquals(expected, classifyNumber("+967711234567")?.apiName)
        assertEquals(expected, classifyNumber("00967711234567")?.apiName)
        assertEquals(expected, classifyNumber("0711234567")?.apiName)
        // الفواصل والمسافات شائعة في الإدخال اليدوي
        assertEquals(expected, classifyNumber("+967 71 123 4567")?.apiName)
    }

    @Test
    fun `بادئة يمن فورجي مُصنَّفة لكنها ليست شبكة محمول`() {
        val info = classifyNumber("101234567")
        assertNotNull(info, "10 تخصيص حقيقي في خطة الترقيم فلا يُهمل")
        assertEquals("Yemen4G", info!!.apiName)
        // خدمة بيانات ثابتة: لا شريحة في البوابة عليها، فلا تُطابَق
        // «داخل الشبكة» وإلا مُنح المنفذ أفضلية لا يستحقها
        assertFalse(info.isMobile)
    }

    @Test
    fun `المشغلون المحمولون موسومون بأنهم محمول`() {
        listOf("701234567", "711234567", "731234567", "771234567", "781234567")
            .forEach { assertTrue(classifyNumber(it)!!.isMobile, "يجب أن يكون محمولًا: $it") }
    }

    @Test
    fun `البادئة غير المعروفة لا تُخمَّن`() {
        assertNull(classifyNumber("991234567"))
        assertNull(classifyNumber("7"))
        assertNull(classifyNumber(""))
        assertNull(classifyNumber("abc"))
    }

    /**
     * `722` نطاق سبأفون عدن للجيل الرابع (VoLTE)، أُطلق مستقلًّا عن `71`.
     *
     * المطابقة يجب أن تكون **الأطول أولًا**: لو فُحصت خانتان فقط لقُرئ
     * `72` — وهو غير مخصَّص — فسقط الرقم في «غير معروف»، ورُفض قبل
     * الطلب، أو وُجِّه عبر شريحة مشغّل آخر بتعرفة خارج الشبكة.
     */
    @Test
    fun `بادئة سبأفون عدن 722 تُطابَق قبل الخانتين`() {
        assertEquals("Sabafon", classifyNumber("722012919")?.apiName)
        assertEquals("Sabafon", classifyNumber("+967722012919")?.apiName)
        assertEquals("Sabafon", classifyNumber("00967722012919")?.apiName)
        assertEquals("Sabafon", classifyNumber("0722012919")?.apiName)
        assertTrue(classifyNumber("722012919")!!.isMobile)
    }

    /**
     * `718` نطاق سبأفون عدن القديم؛ يطابقه `71` أصلًا. يُختبر حتى لا
     * يُضاف جدولٌ ثلاثي يُغيّر نسبته سهوًا.
     */
    @Test
    fun `نطاق عدن القديم 718 يبقى سبأفون`() {
        assertEquals("Sabafon", classifyNumber("718123456")?.apiName)
    }

    /**
     * حارس معماري: `DinstarHardwareService` كان يحمل جدول بادئات موازيًا
     * (`YEMEN_OPERATOR_PREFIXES`) يطابق بخانتين ثابتتين، فيُخطئ في `722`.
     * حُذف وفُوِّض إلى `classifyNumber`. هذا الاختبار يمنع عودته.
     *
     * تكرار جداول البادئات كان أكثر أخطاء هذا المشروع تكرارًا: بلغت
     * أربعة جداول متضاربة قبل التوحيد.
     */
    @Test
    fun `لا جدول بادئات موازٍ خارج DinstarLoadBalancer`() {
        // Gradle يضبط مجلد عمل الاختبار على مجلد الوحدة، لكن التشغيل من
        // جذر المستودع أو من IDE قد يختلف — لذا تُجرَّب المسارات المحتملة.
        val relative = "src/main/kotlin/com/red/server"
        val sourceRoot = listOf(
            relative,
            "backend-server/$relative",
            "RED_Ultimate_V1-main/RED_Ultimate/backend-server/$relative"
        ).map { java.io.File(it) }.firstOrNull { it.isDirectory }

        assertNotNull(sourceRoot, "لم يُعثر على مصادر الخادم — تحقق من مجلد عمل الاختبار")

        val serverSources = sourceRoot!!
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "DinstarLoadBalancer.kt" }
            .toList()

        assertTrue(serverSources.isNotEmpty(), "مجلد المصادر فارغ — تحقق من مجلد عمل الاختبار")

        // بادئات يمن موبايل: وجودها كمفتاح خريطة يعني جدولًا موازيًا
        val offenders = serverSources.filter { file ->
            val text = file.readText()
            Regex("""["'](?:77|78)["']\s*(?:to|->)""").containsMatchIn(text)
        }

        assertTrue(
            offenders.isEmpty(),
            "جدول بادئات موازٍ في: ${offenders.joinToString { it.name }} — " +
                "استخدم DinstarLoadBalancer.classifyNumber بدلًا منه"
        )
    }
}
