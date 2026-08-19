package com.red.sovereign.stories

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حارس عقد القصص بين العميل والخادم.
 *
 * ## العطب الذي يمنعه
 *
 * كان العميل يرسل `visibleTo` و`audience`، بينما يقرأ الخادم
 * `visibility` و`allowedUserIds`. و`FAIL_ON_UNKNOWN_PROPERTIES` مفعَّل
 * افتراضيًّا في Jackson ولم يُعطَّل في `JacksonConfig`، فكان الطلب
 * يُرفض بـ400 — أي أن **إنشاء أي قصة يفشل**.
 *
 * والأخطر هو الاحتمال الآخر: لو عُطّل ذلك الخيار يومًا، لصار الحقلان
 * يُهملان صامتَين فتُنشأ القصة بالخصوصية الافتراضية `CONTACTS`. عندها
 * يختار المستخدم «أشخاصًا محدَّدين» فتُنشر قصّته على **كل** جهات
 * اتصاله دون أن يعلم. هذا تسريب خصوصية لا خلل عرض، ولا يظهر في أي
 * اختبار واجهة.
 *
 * ولذلك يفحص هذا الحارس **أسماء الحقول في JSON نفسه** لا الحقول في
 * Kotlin: إعادة التسمية في الشيفرة وحدها لا تكفي دليلًا.
 */
class StoryContractTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** أسماء الحقول كما يعرّفها `CreateStoryRequest` في الخادم حرفًا بحرف. */
    private val serverCreateFields = setOf(
        "mediaKey", "caption", "visibility", "allowedUserIds"
    )

    @Test
    fun `طلب الانشاء يحمل اسماء حقول الخادم لا اسماء قديمة`() {
        val body = json.encodeToString(
            CreateStoryRequest(
                mediaKey = "media/abc",
                caption = "مرحبا",
                visibility = StoryVisibility.SELECTED,
                allowedUserIds = listOf("u1", "u2")
            )
        ).let { json.parseToJsonElement(it).jsonObject }

        assertTrue("حقل visibility مفقود — الخادم يرفض الطلب", "visibility" in body)
        assertTrue("حقل allowedUserIds مفقود", "allowedUserIds" in body)

        // الاسمان القديمان يجب ألّا يظهرا: وجودهما يعني رفض الطلب بـ400
        assertTrue("عاد الاسم القديم visibleTo", "visibleTo" !in body)
        assertTrue("عاد الاسم القديم audience", "audience" !in body)
    }

    @Test
    fun `قيم الخصوصية تطابق تعداد الخادم`() {
        // StoryVisibility في الخادم: CONTACTS, EVERYONE, SELECTED
        assertEquals(setOf("EVERYONE", "CONTACTS", "SELECTED"), StoryVisibility.ALL)
    }

    /**
     * `encodeDefaults` تساوي false في [Json] المستعمل هنا وفي
     * `StoryViewModel`، فالقيمة الافتراضية **لا تُرسل أصلًا** ويغيب
     * حقل `visibility` من الجسم. لذلك التوكيد الصحيح ليس «الحقل
     * موجود وقيمته CONTACTS» — ذلك يفشل — بل **تطابق الافتراضين على
     * الطرفين**: ما دام الخادم يفترض `CONTACTS` أيضًا، فغياب الحقل
     * يعطي النتيجة نفسها.
     *
     * ولو غُيّر افتراضُ أحد الطرفين وحده لاتّسع جمهور القصّة دون قصد
     * المستخدم — وهو ما يمسكه هذا الاختبار.
     */
    @Test
    fun `افتراض الخصوصية على الطرفين واحد`() {
        assertEquals(StoryVisibility.CONTACTS, CreateStoryRequest(mediaKey = "m").visibility)

        val body = json.encodeToString(CreateStoryRequest(mediaKey = "m"))
            .let { json.parseToJsonElement(it).jsonObject }
        assertTrue(
            "غياب visibility مقبول فقط لأن الخادم يفترض CONTACTS كذلك",
            "visibility" !in body || body["visibility"].toString() == "\"CONTACTS\""
        )
    }

    @Test
    fun `كل حقل يرسله العميل معروف لدى الخادم`() {
        // الحقول الزائدة تُرفض بـ400 ما دام FAIL_ON_UNKNOWN_PROPERTIES
        // مفعَّلًا (وهو افتراضي Jackson، ولم يُعطَّل في JacksonConfig).
        val optionalExtras = setOf("mediaType", "backgroundColor", "durationMs")
        val body = json.encodeToString(
            CreateStoryRequest(mediaKey = "m", visibility = StoryVisibility.EVERYONE)
        ).let { json.parseToJsonElement(it).jsonObject }

        val unknown = body.keys - serverCreateFields - optionalExtras
        assertTrue("حقول لا يعرفها الخادم فيرفض الطلب: $unknown", unknown.isEmpty())
    }

    @Test
    fun `رد الخادم الادنى يُفك ترميزه بلا استثناء`() {
        // StoryResponse يرسل عشرة حقول فقط؛ الباقي له افتراضي في العميل.
        val server = """
            {"id":"s1","ownerRedId":"RED-1","ownerUsername":"ayman",
             "ownerDisplayName":"أيمن","mediaUrl":"/api/media/k","mediaType":"image/jpeg",
             "caption":null,"createdAt":"2026-08-19T10:00:00Z",
             "expiresAt":"2026-08-20T10:00:00Z","viewCount":5}
        """.trimIndent()
        val story = json.decodeFromString<Story>(server)
        assertEquals("s1", story.id)
        assertEquals(5L, story.viewCount)
        // غير المرسَل يأخذ الافتراضي الآمن لا يرمي
        assertEquals(StoryVisibility.CONTACTS, story.visibility)
        assertTrue(story.allowedUserIds.isEmpty())
    }

    @Test
    fun `تفاعلات الواجهة كلها مقبولة لدى الخادم`() {
        // StoryService.react يرفض ما عدا هذه السبعة بـ400، فعرضُ إيموجي
        // خارجها في الواجهة يعني زرًّا يفشل عند الضغط.
        val serverAccepted = setOf("❤️", "🔥", "😢", "👏", "😍", "🎉", "👍")
        assertEquals(serverAccepted, STORY_REACTIONS.toSet())
    }
}
