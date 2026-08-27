package com.red.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * يثبّت ترجمة حقلي `direction` و`status` قبل الإدراج في `dinstar_cdr`.
 *
 * العمودان مقيَّدان بـCHECK في V15، والجهاز لا يُصدر أيًّا منهما بالشكل
 * المطلوب: يكتب `ip->gsm` ولا يكتب حالة إطلاقًا. الإدراج الخام كان يخرق
 * القيد فيُرفض كل صف، والاستثناء يُبتلَع — فبقي الجدول فارغًا بلا أثر.
 */
class DinstarCdrMappingTest {

    @Test
    @DisplayName("اتجاه الجهاز يُترجم إلى مفردات قيد CHECK")
    fun directionNormalization() {
        assertAll(
            { assertEquals("outbound", DinstarApiContract.Cdr.normalizeDirection("ip->gsm")) },
            { assertEquals("inbound", DinstarApiContract.Cdr.normalizeDirection("gsm->ip")) },
            // مكالمة callback تبدأ بواردة من GSM
            { assertEquals("inbound", DinstarApiContract.Cdr.normalizeDirection("callback")) },
            // اللواحق النصية من إصدارات أخرى
            { assertEquals("outbound", DinstarApiContract.Cdr.normalizeDirection("OUTBOUND")) },
            { assertEquals("inbound", DinstarApiContract.Cdr.normalizeDirection(" in ")) },
            // ما لا يُعرف لا يُخمَّن: null ⇒ يتخطّى المُجدول السجل بدل خرق القيد
            { assertNull(DinstarApiContract.Cdr.normalizeDirection("unknown")) },
            { assertNull(DinstarApiContract.Cdr.normalizeDirection(null)) },
            { assertNull(DinstarApiContract.Cdr.normalizeDirection("")) }
        )
    }

    @Test
    @DisplayName("وجود زمن الإجابة هو الدليل القاطع على الإجابة")
    fun answeredWinsOverHangupCause() {
        // حتى مع سبب إنهاء يشبه الفشل، زمن الإجابة يحسم أن المكالمة تمّت
        assertEquals("answered", DinstarApiContract.Cdr.callOutcome(answered = true, hangup = "Busy"))
        assertEquals("answered", DinstarApiContract.Cdr.callOutcome(answered = true, hangup = null))
    }

    @Test
    @DisplayName("غير المُجابة تُصنَّف من سبب الإنهاء، والمجهول يُعدّ failed")
    fun unansweredClassification() {
        assertAll(
            { assertEquals("busy", DinstarApiContract.Cdr.callOutcome(false, "User busy")) },
            { assertEquals("no_answer", DinstarApiContract.Cdr.callOutcome(false, "No answer")) },
            { assertEquals("no_answer", DinstarApiContract.Cdr.callOutcome(false, "no_answer")) },
            { assertEquals("no_answer", DinstarApiContract.Cdr.callOutcome(false, "Timeout")) },
            { assertEquals("cancelled", DinstarApiContract.Cdr.callOutcome(false, "Originator cancel")) },
            // إخفاق الشبكة لا يُقرأ كعدم ردّ من المستلم: الافتراضي failed
            { assertEquals("failed", DinstarApiContract.Cdr.callOutcome(false, "Network out of order")) },
            { assertEquals("failed", DinstarApiContract.Cdr.callOutcome(false, null)) },
            { assertEquals("failed", DinstarApiContract.Cdr.callOutcome(false, "   ")) }
        )
    }

    @Test
    @DisplayName("كل نواتج الترجمة تقع داخل قيد CHECK للعمودين")
    fun everyOutcomeSatisfiesTheConstraint() {
        // نفس المجموعتين المكتوبتين في V15: أي ناتج خارجهما يُرفض عند الإدراج.
        val allowedStatus = setOf("answered", "no_answer", "busy", "failed", "cancelled")
        val allowedDirection = setOf("inbound", "outbound")

        val hangupSamples = listOf(
            null, "", "Busy", "User busy", "No answer", "no_answer", "NOANSWER",
            "Timeout", "No reply", "Originator cancel", "Caller abandon",
            "Network out of order", "Normal Clearing", "شيء غير متوقع"
        )
        for (answered in listOf(true, false)) {
            for (h in hangupSamples) {
                val outcome = DinstarApiContract.Cdr.callOutcome(answered, h)
                assertTrue(
                    outcome in allowedStatus,
                    "الحالة '$outcome' خارج قيد CHECK (answered=$answered, hangup=$h)"
                )
            }
        }
        for (d in listOf("ip->gsm", "gsm->ip", "callback", "inbound", "outbound", "IN", "OUT")) {
            val norm = DinstarApiContract.Cdr.normalizeDirection(d)
            assertTrue(norm in allowedDirection, "الاتجاه '$norm' خارج قيد CHECK (raw=$d)")
        }
    }

    @Test
    @DisplayName("ON CONFLICT يعيد شرط الفهرس الجزئي حرفيًا وإلا فشل الإدراج كله")
    fun onConflictRepeatsThePartialIndexPredicate() {
        val sql = DinstarApiContract.Cdr.INSERT_SQL
        val flat = sql.replace(Regex("\\s+"), " ")

        // الحَكَم uq_dinstar_cdr_natural_key فهرس جزئي، وPostgreSQL يرفض
        // فهرسًا جزئيًا حَكَمًا للتعارض إلا إذا أعادت الجملة شرطَه (42P10).
        // حذف هذا الشرط لا يُفقد صفًا واحدًا بل يُسقط دورة الابتلاع بأكملها.
        assertTrue(
            flat.contains(
                "ON CONFLICT (gateway_id, port_index, start_time, caller_number, callee_number) " +
                    "WHERE gateway_id IS NOT NULL AND port_index IS NOT NULL AND start_time IS NOT NULL " +
                    "DO NOTHING"
            ),
            "شرط الفهرس الجزئي مفقود أو غير مطابق في ON CONFLICT: $flat"
        )
    }

    @Test
    @DisplayName("عدد محارف ? يطابق عدد الأعمدة المُدرَجة")
    fun placeholderCountMatchesColumnCount() {
        val sql = DinstarApiContract.Cdr.INSERT_SQL

        // موضعان يستخدمان الجملة نفسها بترتيب وسائط يدوي؛ أي عمود يُضاف بلا
        // محرف ? مقابل (أو العكس) يفشل عند التشغيل فقط، وفي كلا الموضعين.
        val columns = Regex("""INSERT INTO dinstar_cdr\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
            .find(sql)!!.groupValues[1]
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val values = Regex("""VALUES\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
            .find(sql)!!.groupValues[1]

        assertEquals(13, columns.size, "أعمدة الإدراج: $columns")
        assertEquals(
            columns.size, values.count { it == '?' },
            "عدد ? ($values) لا يطابق عدد الأعمدة (${columns.size})"
        )

        // call_type مُستبعَد عن قصد — افتراضيّه 'VOICE' في المخطَّط.
        assertTrue("call_type" !in columns, "call_type يجب أن يُترك لافتراضي المخطَّط")
    }
}
