package com.red.sovereign.features.communities

import com.red.sovereign.core.RichMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * P1-B — نموذج الفعالية (Events).
 *
 * ملف جديد كليًا — لا يعدّل `RichMessage` الأصلي إطلاقًا.
 * الفكرة: `EventMessage` يُغلَّف داخل `RichMessage` عادية
 * (`action = "MESSAGE"`) عبر ترميز JSON مسبوق ببادئة مميزة،
 * فيبقى مشفّرًا E2EE ضمن الحوار/المجموعة دون أي تغيير في البروتوكول.
 */

/** رد الحضور: ذاهب / ربما. */
@Serializable
enum class EventRsvp {
    GOING,
    MAYBE
}

@Serializable
data class EventMessage(
    val title: String,
    /** وقت الفعالية — نص ISO-8601 حر (يُعرض كما هو). */
    val time: String,
    val location: String = "",
    val rsvp: EventRsvp? = null
) {
    init {
        require(title.isNotBlank() && title.length <= 200) { "INVALID_EVENT_TITLE" }
        require(time.length <= 100) { "INVALID_EVENT_TIME" }
        require(location.length <= 300) { "INVALID_EVENT_LOCATION" }
    }

    /** يغلّف الفعالية في رسالة غنية عادية قابلة للإرسال. */
    fun toRichMessage(): RichMessage =
        RichMessage(action = "MESSAGE", text = PREFIX + json.encodeToString(serializer(), this))

    companion object {
        /** بادئة تميّز نص الفعالية عن النص العادي. */
        const val PREFIX = "❖EVENT:"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

        /** يستخرج الفعالية من رسالة غنية، أو null إن لم تكن فعالية. */
        fun fromRich(rich: RichMessage): EventMessage? {
            if (!rich.text.startsWith(PREFIX)) return null
            return runCatching {
                json.decodeFromString<EventMessage>(rich.text.removePrefix(PREFIX))
            }.getOrNull()
        }

        /** نص معاينة قصير للعرض في القوائم. */
        fun previewOf(event: EventMessage): String =
            if (event.location.isBlank()) event.title else "${event.title} · ${event.location}"
    }
}

/** امتداد مريح: `RichMessage.toEventMessage()` — يعيد null لغير الفعاليات. */
fun RichMessage.toEventMessage(): EventMessage? = EventMessage.fromRich(this)

/** تسمية عربية للرد. */
fun eventRsvpLabel(rsvp: EventRsvp?): String = when (rsvp) {
    EventRsvp.GOING -> "ذاهب"
    EventRsvp.MAYBE -> "ربما"
    null -> "بدون رد"
}
