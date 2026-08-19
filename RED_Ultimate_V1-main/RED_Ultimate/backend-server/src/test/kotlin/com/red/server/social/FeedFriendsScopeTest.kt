package com.red.server.social

import com.red.server.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.jdbc.core.JdbcTemplate

/**
 * حارس دلالة تبويب «الأصدقاء» في فيد المنشورات.
 *
 * البند العاشر طلب استبدال «المتابَعة» بـ«الأصدقاء» و«عام». والاستبدال
 * ليس تغيير عنوانٍ فقط: «المتابَعة» علاقة أحادية الاتجاه، أما
 * «الأصدقاء» فمتبادلة.
 *
 * ## تحديث بعد الدمج مع origin/main
 *
 * كانت النسخة السابقة من هذا الاختبار تحرس `FeedService.friendIds`
 * المبنية على `FollowDocument` في Mongo. وقد حُسم عند الدمج أن مصدر
 * الصداقة الصحيح هو جدول `red_contacts`، لأن `ContactService` يُدرج
 * **صفَّين — واحدًا لكل اتجاه — عند قبول طلب الاتصال**، فهو السجلّ
 * الحقيقي للعلاقة المتبادلة. أما `FollowDocument` فيكتبه `follow()`
 * وحده بعلاقة أحادية، فلا يصلح أساسًا للصداقة.
 *
 * ولمّا صار الترشيح داخل SQL (`EXISTS` متماثلة) لا في الذاكرة، لم يعد
 * ممكنًا اختبارُه بمحاكاة Mongo. فالمحروس هنا ما يبقى منطقًا خالصًا
 * في Kotlin: **مُطابِق `canonical()`**، وهو الذي يمنع تسرُّب دلالة
 * «المتابَعة» الأحادية إلى تبويب «الأصدقاء».
 *
 * التبادل نفسه تحرسه صياغة `EXISTS` في `mutualFriendIds`، ويلزمه
 * اختبار تكامل بقاعدة بيانات حقيقية — وهو خارج نطاق اختبار الوحدة.
 */
class FeedFriendsScopeTest {

    private fun service(): FeedService = FeedService(
        mock<MongoTemplate>(),
        mock<UserAccountRepository>(),
        mock<JdbcTemplate>()
    )

    @Test
    fun `القيم المهجورة تُطابَق إلى البديل الجديد`() {
        @Suppress("DEPRECATION")
        assertEquals(FeedScope.FRIENDS, FeedScope.FOLLOWING.canonical())
        @Suppress("DEPRECATION")
        assertEquals(FeedScope.PUBLIC, FeedScope.YEMEN.canonical())
    }

    @Test
    fun `القيم الجديدة تُطابَق إلى نفسها`() {
        assertEquals(FeedScope.ALL, FeedScope.ALL.canonical())
        assertEquals(FeedScope.FRIENDS, FeedScope.FRIENDS.canonical())
        assertEquals(FeedScope.PUBLIC, FeedScope.PUBLIC.canonical())
    }

    @Test
    fun `كل قيم التعداد لها مُطابِق لا يُرجع قيمة مهجورة`() {
        // لو أُضيفت قيمة جديدة ونُسي تطابقها، يفشل هذا الاختبار بدل أن
        // يسقط نطاقٌ صامتًا في فرع `else` فيعرض منشورات لا ينبغي عرضها.
        @Suppress("DEPRECATION")
        val deprecated = setOf(FeedScope.FOLLOWING, FeedScope.YEMEN)
        FeedScope.entries.forEach { scope ->
            assertFalse(
                scope.canonical() in deprecated,
                "canonical() أرجع قيمة مهجورة من $scope"
            )
        }
    }

    @Test
    fun `المطابق مستقر عند اعادة تطبيقه`() {
        // يُستدعى في `feed()` مرّة، لكن ثباتَه يضمن أن أي استدعاء لاحق
        // لا يغيّر النتيجة.
        FeedScope.entries.forEach { scope ->
            assertEquals(scope.canonical(), scope.canonical().canonical())
        }
    }

    @Test
    fun `تعداد الخصوصية يطابق ما يرشّح به FeedService`() {
        // FeedService يرشّح بـPUBLIC وFRIENDS وLOCAL_YEMEN؛ حذف أيٍّ
        // منها يكسر الترشيح، وإضافة قيمة بلا فرع تُسقطها صامتة.
        assertEquals(
            setOf(
                PostVisibility.PUBLIC,
                PostVisibility.FRIENDS,
                PostVisibility.LOCAL_YEMEN
            ),
            PostVisibility.entries.toSet()
        )
    }

    @Test
    fun `الخدمة تُبنى بثلاث تبعيات بينها JdbcTemplate`() {
        // مصدر الصداقة صار red_contacts عبر JdbcTemplate. لو أُعيدت
        // التبعية إلى Mongo وحدها، يفشل البناء هنا بدل أن يتغيّر معنى
        // «الأصدقاء» في الإنتاج دون أن ينتبه أحد.
        assertTrue(service() is FeedService)
    }
}
