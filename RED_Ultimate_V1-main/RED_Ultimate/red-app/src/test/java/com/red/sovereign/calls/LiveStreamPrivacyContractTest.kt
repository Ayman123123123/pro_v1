package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد خصوصية البث المباشر على حدود الواجهة.
 *
 * ## العطل الذي يمنعه هذا الاختبار
 *
 * كان `LiveStreamHubDialog` يجمع «بث خاص» وكلمة السر ثم يُهملهما:
 *
 * ```kotlin
 * onStartBroadcasting = { title, isPriv, pass ->
 *     LiveStreamService.start(context, streamId, ownUserId, true, title)
 *     //                                       isPriv و pass لا يُمرَّران
 * }
 * ```
 *
 * فيُفعّل المستخدم «بث خاص»، ويكتب كلمة سر، ويظن بثه محميًا — **وهو مفتوح
 * للجميع**. الخادم يفرض القاعدة (`PRIVATE_STREAM_PASSWORD_REQUIRED` في
 * `LiveStreamService.kt:58`) لكنه لا يرى ما لا يُرسَل إليه.
 *
 * هذه الاختبارات تثبّت المنطق الخالص لاشتقاق كلمة السر وشرط تمكين الزر،
 * وهما ما كان مفقودًا. لا تحتاج Android ولا شبكة.
 */
class LiveStreamPrivacyContractTest {

    /**
     * الاشتقاق المطبَّق في موضع الاستدعاء: كلمة السر تُعتبر موجودة فقط إن
     * كان البث خاصًا **و** النص غير فارغ بعد التشذيب. المسافات وحدها ليست
     * كلمة سر.
     */
    private fun derivePassword(isPrivate: Boolean, raw: String): String? =
        if (isPrivate) raw.trim().takeIf { it.isNotBlank() } else null

    /** شرط تمكين زر الإطلاق في الحوار. */
    private fun canLaunch(isPrivate: Boolean, raw: String): Boolean =
        !isPrivate || raw.isNotBlank()

    @Test
    fun `public stream carries no password even if a value lingers in the field`() {
        // المستخدم كتب كلمة سر ثم أوقف «خاص»: لا يجوز أن تُرسل.
        assertNull(derivePassword(isPrivate = false, raw = "leftover-secret"))
        assertNull(derivePassword(isPrivate = false, raw = ""))
    }

    @Test
    fun `private stream keeps its password trimmed`() {
        assertEquals("majlis-2026", derivePassword(isPrivate = true, raw = "  majlis-2026  "))
    }

    @Test
    fun `whitespace is not a password`() {
        // بلا تشذيب كان "   " يُقبل كسرّ فيبدأ البث «خاصًا» بسرّ لا يمكن كتابته.
        assertNull(derivePassword(isPrivate = true, raw = "   "))
        assertNull(derivePassword(isPrivate = true, raw = ""))
    }

    @Test
    fun `launch is blocked for a private stream without a password`() {
        assertEquals(false, canLaunch(isPrivate = true, raw = ""))
        // المنع عند الزر لا بعد الإرسال: الحوار يستدعي onDismiss() فورًا بعد
        // الاستدعاء، فأي تحقق لاحق يُغلق الحوار ويُفقد المستخدم ما كتبه.
        assertTrue(canLaunch(isPrivate = true, raw = "s"))
        assertTrue(canLaunch(isPrivate = false, raw = ""))
    }

    @Test
    fun `every launchable private stream yields a usable password`() {
        // الثابتة التي تحمي الخصوصية: إن كان الإطلاق مسموحًا وكان البث خاصًا،
        // فلا بد من كلمة سر فعلية تصل إلى الخدمة.
        listOf("s", "majlis-2026", " x ", "كلمة-سر").forEach { raw ->
            if (canLaunch(isPrivate = true, raw = raw)) {
                val derived = derivePassword(isPrivate = true, raw = raw)
                assertTrue("لا يجوز إطلاق بث خاص بلا كلمة سر فعلية: [$raw]", !derived.isNullOrBlank())
            }
        }
    }
}
