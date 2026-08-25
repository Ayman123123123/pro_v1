package com.red.sovereign.stories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * ⚠️ عقد هذه الملفّات مشترك مع الخادم:
 *   backend-server/.../stories/StoryModels.kt
 *
 * كان اسما حقلَي الخصوصية مختلفَين على الطرفين:
 *
 *   العميل يرسل   : visibleTo / audience
 *   الخادم يقرأ    : visibility / allowedUserIds
 *
 * وهذا ليس اختلافَ تسمية فحسب. `FAIL_ON_UNKNOWN_PROPERTIES` مفعَّل
 * افتراضيًّا في Jackson ولم يُعطَّل في JacksonConfig، فالحقلان
 * المجهولان يجعلان الطلب يُرفض بـ400 — أي أن **إنشاء أي قصة يفشل**.
 * ولو عُطّل الخيار لكان الأسوأ: تُنشأ القصة صامتةً بالخصوصية
 * الافتراضية CONTACTS، فيختار المستخدم «أشخاصًا محدَّدين» ثم تُنشر
 * قصّته على كل جهات اتصاله دون أن يعلم — تسريبُ خصوصية لا عطبَ عرض.
 *
 * وهو عين العطب الذي أُصلح في FeedViewModel.createPoll (scope مكان
 * visibility). لذا: **أي حقل هنا يجب أن يطابق اسم حقل الخادم حرفًا
 * بحرف**، ويحرس ذلك StoryContractTest.
 */

/** يطابق `StoryVisibility` في الخادم. القيم الثلاث هي المقبولة فقط. */
object StoryVisibility {
    /** كل من يفتح التطبيق. */
    const val EVERYONE = "EVERYONE"

    /** جهات الاتصال فقط — وهو افتراضي الخادم. */
    const val CONTACTS = "CONTACTS"

    /** قائمة مختارة، تُمرَّر في [CreateStoryRequest.allowedUserIds]. */
    const val SELECTED = "SELECTED"

    val ALL = setOf(EVERYONE, CONTACTS, SELECTED)
}

@Serializable
data class CreateStoryRequest(
    val mediaKey: String,
    val caption: String? = null,
    /** الاسم على الخادم `visibility` — لا تُعِدْه إلى `visibleTo`. */
    val visibility: String = StoryVisibility.CONTACTS,
    /** الاسم على الخادم `allowedUserIds`، ويُقرأ كـ`Set` هناك. */
    val allowedUserIds: List<String> = emptyList(),
    val mediaType: String = "image/jpeg",
    val backgroundColor: String? = null, // للقصص النصّية: ‎#D32F2F, #1565C0
    val durationMs: Long? = null, // للصوت والفيديو
    /** موجة الصوت للقصص الصوتية — أضافها الخادم في 9076487. */
    val waveform: List<Int> = emptyList()
)

/**
 * ردّ الخادم. الحقول الستّة الأولى وحدها هي ما يرسله `StoryResponse`
 * فعلًا؛ ما بعدها اختياري بقيمة افتراضية حتى لا ينكسر فكّ الترميز.
 *
 * `@SerialName("visibility")` يبقي اسم الحقل في JSON مطابقًا للخادم مع
 * إبقاء الاسم المقروء في الشيفرة.
 */
@Serializable
data class Story(
    val id: String,
    val ownerRedId: String,
    val ownerUsername: String,
    val ownerDisplayName: String,
    val mediaUrl: String,
    val mediaType: String,
    val caption: String? = null,
    val createdAt: String,
    val expiresAt: String,
    val viewCount: Long = 0,
    @SerialName("visibility") val visibility: String = StoryVisibility.CONTACTS,
    @SerialName("allowedUserIds") val allowedUserIds: List<String> = emptyList(),
    val reactions: Map<String, Long> = emptyMap(),
    val viewerIds: List<String> = emptyList(),
    val isViewed: Boolean = false,
    val backgroundColor: String? = null,
    val durationMs: Long? = null,
    val waveform: List<Int> = emptyList()
)


@Serializable
data class StoryView(val storyId: String, val viewerRedId: String, val reaction: String? = null)

@Serializable
data class StoryReactionRequest(val emoji: String)

/**
 * التفاعلات التي يقبلها الخادم — `StoryService.react` يرفض ما عداها
 * بـ400. القائمة هنا تمنع عرض إيموجي في الواجهة يفشل عند الإرسال.
 */
val STORY_REACTIONS = listOf("❤️", "🔥", "😢", "👏", "😍", "🎉", "👍")

// أدوات تمييز نوع القصة
fun Story.isText(): Boolean = mediaType == "TEXT" || mediaType.startsWith("text/")
fun Story.isVoice(): Boolean = mediaType.startsWith("audio/") || mediaType == "VOICE"
fun Story.isVideo(): Boolean = mediaType.startsWith("video/")
fun Story.isImage(): Boolean = mediaType.startsWith("image/")
