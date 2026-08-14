package com.red.sovereign.core

import com.red.sovereign.crypto.DecryptedMessage
import java.util.UUID

/**
 * بناء رسالة صادرة بأمان — بدون اختراع API على الخادم.
 *
 * [RichMessage] يرفض أي `disappearingMs` خارج المجموعة المسموحة برمي
 * استثناء داخل `init`. زر الإرسال كان يبني الكائن مباشرة ثم يمسح النص
 * حتى لو فشل التشفير. هنا نُثبّت القيم ونُرجع [Result] بدل الانهيار.
 */
object ChatComposer {

    val ALLOWED_DISAPPEARING_MS = setOf(0L, 3_600_000L, 86_400_000L, 604_800_000L)

    fun clampDisappearingMs(ms: Long?): Long? = when {
        ms == null || ms <= 0L -> null
        ms in ALLOWED_DISAPPEARING_MS -> ms
        ms < 3_600_000L -> 3_600_000L
        ms < 86_400_000L -> 86_400_000L
        else -> 604_800_000L
    }

    fun newClientId(): String = "local-${UUID.randomUUID()}"

    fun isClientId(id: String): Boolean = id.startsWith("local-")

    fun buildText(
        action: String = "MESSAGE",
        text: String,
        replyTo: String? = null,
        editOf: String? = null,
        disappearingMs: Long? = null,
        mentions: List<String> = emptyList(),
        hashtags: List<String> = emptyList(),
        poll: InlinePoll? = null,
    ): Result<RichMessage> = runCatching {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() || poll != null) { "EMPTY_TEXT" }
        val safeAction = if (action in setOf("MESSAGE", "EDIT", "DELETE", "STORY_REPLY")) action else "MESSAGE"
        val clamped = clampDisappearingMs(disappearingMs)
        RichMessage(
            action = safeAction,
            text = trimmed.take(65_536),
            replyTo = replyTo,
            editOf = editOf,
            expiresAt = clamped?.takeIf { it > 0L }?.let { System.currentTimeMillis() + it },
            mentions = mentions.take(20),
            hashtags = hashtags.take(10),
            disappearingMs = clamped,
            poll = poll,
        )
    }

    fun humanizeSendError(raw: String?): String {
        val code = raw.orEmpty()
        return when {
            code.contains("NO_APPROVED_REMOTE_DEVICE") ->
                "لا يمكن الإرسال: الطرف الآخر ليس لديه جهاز معتمد بعد."
            code.contains("EMPTY_TEXT") ->
                "اكتب رسالة أولاً."
            code.contains("INVALID_TARGET") ->
                YounesId.ERROR_MESSAGE
            code.contains("INVALID_GROUP") ->
                "تعذر إرسال الرسالة: المجموعة غير جاهزة."
            code.contains("NO_RECIPIENT") ->
                "تعذر التسليم: لا يوجد جهاز مستلم معتمد."
            code.contains("NOT_CONNECTED", ignoreCase = true) ||
                code.contains("WebSocket", ignoreCase = true) ->
                "لا يوجد اتصال بالمخدم. ستُعاد المحاولة عند عودة الشبكة."
            code.contains("ForegroundService", ignoreCase = true) ||
                code.contains("foreground", ignoreCase = true) ->
                "تعذر بدء خدمة الإرسال. أبقِ التطبيق مفتوحاً وحاول مرة أخرى."
            code.isBlank() ->
                "تعذر إرسال الرسالة."
            else -> "تعذر إرسال الرسالة: ${code.take(160)}"
        }
    }

    fun consumeOutgoingEvent(
        event: OutgoingSendEvent,
        messages: MutableList<DecryptedMessage>,
    ): OutgoingUiUpdate {
        val idx = messages.indexOfFirst { it.id == event.clientId }
        if (idx < 0) {
            return if (event.success) OutgoingUiUpdate()
            else OutgoingUiUpdate(error = humanizeSendError(event.error))
        }
        val pending = messages.removeAt(idx)
        return if (event.success) {
            OutgoingUiUpdate()
        } else {
            OutgoingUiUpdate(
                restoreText = RichMessage.decode(pending.plaintext)?.text,
                error = humanizeSendError(event.error),
            )
        }
    }
}

data class OutgoingUiUpdate(
    val restoreText: String? = null,
    val error: String? = null,
)
