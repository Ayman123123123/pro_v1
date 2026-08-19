package com.red.server.social

import com.red.server.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.util.UUID

/**
 * حارس دلالة تبويب «الأصدقاء» في فيد المنشورات.
 *
 * البند العاشر طلب استبدال «المتابَعة» بـ«الأصدقاء» و«عام». والاستبدال
 * ليس تغيير عنوانٍ فقط: «المتابَعة» علاقة أحادية الاتجاه، أما «الأصدقاء»
 * فمتبادلة. هذه الاختبارات تثبّت الفرق حتى لا يعود أحدٌ فيُبسّطها إلى
 * «من أتابعهم» ويُسرّب منشورات أشخاص لا يتابعون المستخدم.
 */
class FeedFriendsScopeTest {

    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()
    private val dave = UUID.randomUUID()

    /**
     * يبني خدمةً على Mongo وهمي: الاستعلام عن `followerId` يعيد من
     * يتابعهم أليس، والاستعلام عن `followedId` يعيد من يتابعونها.
     */
    private fun serviceWith(iFollow: List<UUID>, followMe: List<UUID>): FeedService {
        val mongo = mock<MongoTemplate>()
        val me = alice.toString()
        whenever(mongo.find(any<Query>(), eq(FollowDocument::class.java))).thenAnswer { invocation ->
            // toString() بدل toJson(): لا يعتمد على واجهة BSON بعينها.
            // ملاحظة: تمثيل Document هو {{followerId=...}} بلا علامات
            // اقتباس حول اسم الحقل، فالمطابقة تكون على الاسم مجرّدًا.
            // و"followerId" و"followedId" لا يحتوي أحدهما الآخر.
            val query = invocation.getArgument<Query>(0).queryObject.toString()
            when {
                query.contains("followerId") && query.contains(me) ->
                    iFollow.map { FollowDocument("$me:$it", me, it.toString()) }
                query.contains("followedId") && query.contains(me) ->
                    followMe.map { FollowDocument("$it:$me", it.toString(), me) }
                else -> emptyList()
            }
        }
        return FeedService(mongo, mock<UserAccountRepository>())
    }

    @Test
    fun `الصديق من تتبادل معه المتابعة في الاتجاهين`() {
        // أليس تتابع بوب وكارول؛ بوب وديف يتابعونها.
        // الصديق الوحيد هو بوب (تبادُل)، وكارول تُستبعد لأنها لا تتابع أليس.
        val service = serviceWith(iFollow = listOf(bob, carol), followMe = listOf(bob, dave))
        val friends = service.friendIds(alice)

        // مقارنة كمجموعة لا كقائمة: الناتج من تقاطُع مجموعتين فترتيبه
        // غير مضمون، والتأكيد على الترتيب يجعل الاختبار هشًّا بلا فائدة.
        assertEquals(setOf(bob.toString()), friends.toSet())
        assertFalse(friends.contains(carol.toString()), "من أتابعه ولا يتابعني ليس صديقًا")
        assertFalse(friends.contains(dave.toString()), "من يتابعني ولا أتابعه ليس صديقًا")
    }

    @Test
    fun `لا أصدقاء عند غياب التبادل رغم وجود متابعات`() {
        // حالة تكشف الخطأ الشائع: لو رُدَّت «من أتابعهم» لظهرت كارول.
        val service = serviceWith(iFollow = listOf(carol), followMe = listOf(dave))
        assertTrue(service.friendIds(alice).isEmpty())
    }

    @Test
    fun `قائمة فارغة عندما لا يتابع المستخدم أحدًا`() {
        val service = serviceWith(iFollow = emptyList(), followMe = listOf(bob, carol))
        assertTrue(service.friendIds(alice).isEmpty())
    }

    @Test
    fun `التبادل الكامل يعيد الجميع دون تكرار`() {
        val service = serviceWith(iFollow = listOf(bob, carol), followMe = listOf(bob, carol))
        val friends = service.friendIds(alice)
        assertEquals(2, friends.size)
        assertEquals(friends.size, friends.toSet().size, "لا تكرار في قائمة الأصدقاء")
        assertTrue(friends.containsAll(listOf(bob.toString(), carol.toString())))
    }

    @Test
    fun `القيم المهجورة تُطابَق إلى البديل الجديد`() {
        // نسخ التطبيق المثبَّتة ما زالت ترسل FOLLOWING و YEMEN؛ لو حُذفتا
        // من التعداد لردّ الخادم 400 على كل طلب منها.
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
        // يمنع إضافة قيمة جديدة دون تحديث canonical().
        @Suppress("DEPRECATION")
        val deprecated = setOf(FeedScope.FOLLOWING, FeedScope.YEMEN)
        FeedScope.entries.forEach { scope ->
            assertFalse(
                scope.canonical() in deprecated,
                "canonical() أرجع قيمة مهجورة لـ $scope",
            )
        }
    }
}
