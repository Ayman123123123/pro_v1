package com.red.sovereign.stories

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * يثبّت أسماء حقول عقد الخصوصية على السلك.
 *
 * كُتب هذا الاختبار أصلًا لعقد قديم كان العميل يرسل فيه `visibleTo` و
 * `audience` ثم يُترجمهما عند التسلسل. أُلغي ذلك الالتفاف: النموذج
 * [CreateStoryRequest] يحمل الآن أسماء الخادم مباشرة (`visibility` و
 * `allowedUserIds`)، فلا توجد طبقة ترجمة يمكن أن تنحرف.
 *
 * الاختبار يبقى لأن الخطر لم يزل: `FAIL_ON_UNKNOWN_PROPERTIES` مُفعَّل
 * افتراضيًا في Jackson ولم يُعطَّل في `JacksonConfig`، فأي حقل قديم يعود
 * إلى الجسم يُرفض الطلب بـ 400 — أي «إنشاء أي قصة يُشل».
 */
class StoryRequestSerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `story privacy uses the server contract field names`() {
        val encoded = json.encodeToString(
            CreateStoryRequest(
                mediaKey = "users/user-1/story.jpg",
                visibility = StoryVisibility.SELECTED,
                allowedUserIds = listOf("user-2", "user-3"),
            ),
        )

        assertTrue(encoded.contains("\"visibility\":\"SELECTED\""))
        assertTrue(encoded.contains("\"allowedUserIds\":[\"user-2\",\"user-3\"]"))
        // الأسماء القديمة يجب ألا تظهر أبدًا: وجودها يعني 400 من الخادم.
        assertFalse(encoded.contains("\"visibleTo\""))
        assertFalse(encoded.contains("\"audience\""))
    }
}
