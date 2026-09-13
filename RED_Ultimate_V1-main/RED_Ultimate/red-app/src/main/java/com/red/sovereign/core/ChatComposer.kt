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

    val ALLOWED_DISAPPEARING_MS = setOf(0L, 3_600_000L, 86_400_000L, 604_800_000L, 7_776_000_000L)

    fun clampDisappearingMs(ms: Long?): Long? = when {
        ms == null || ms <= 0L -> null
        ms in ALLOWED_DISAPPEARING_MS -> ms
        ms < 3_600_000L -> 3_600_000L
        ms < 86_400_000L -> 86_400_000L
        ms < 604_800_000L -> 604_800_000L
        else -> 7_776_000_000L
    }

    fun newClientId(): String = UuidV7.next()

    // توافق خلفي: يقبل معرفات "local-*" القديمة (UUIDv4) ومعرفات UUIDv7 الجديدة بدون prefix.
    fun isClientId(id: String): Boolean {
        if (id.startsWith("local-")) return true
        return runCatching { UUID.fromString(id); true }.getOrDefault(false)
    }

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
        val safeAction = if (action in setOf("MESSAGE", "EDIT", "DELETE", "STORY_REPLY", "LOCATION", "CONTACT")) action else "MESSAGE"
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

    fun buildLocation(
        latitude: Double,
        longitude: Double,
        name: String = "",
        address: String = "",
        disappearingMs: Long? = null
    ): Result<RichMessage> = runCatching {
        val clamped = clampDisappearingMs(disappearingMs)
        RichMessage(
            action = "LOCATION",
            text = if (name.isNotBlank()) name else "موقع جغرافي",
            location = LocationData(latitude, longitude, name, address),
            expiresAt = clamped?.takeIf { it > 0L }?.let { System.currentTimeMillis() + it },
            disappearingMs = clamped
        )
    }

    fun buildContact(
        name: String,
        phoneNumber: String = "",
        redId: String = "",
        disappearingMs: Long? = null
    ): Result<RichMessage> = runCatching {
        val clamped = clampDisappearingMs(disappearingMs)
        RichMessage(
            action = "CONTACT",
            text = "جهة اتصال: $name",
            contact = ContactData(name, phoneNumber, redId),
            expiresAt = clamped?.takeIf { it > 0L }?.let { System.currentTimeMillis() + it },
            disappearingMs = clamped
        )
    }

    fun buildEdit(
        messageId: String,
        newText: String,
        disappearingMs: Long? = null
    ): Result<RichMessage> = runCatching {
        val trimmed = newText.trim()
        require(trimmed.isNotEmpty()) { "EMPTY_TEXT" }
        val clamped = clampDisappearingMs(disappearingMs)
        RichMessage(
            action = "EDIT",
            text = trimmed.take(65_536),
            editOf = messageId,
            expiresAt = clamped?.takeIf { it > 0L }?.let { System.currentTimeMillis() + it },
            disappearingMs = clamped
        )
    }

    fun buildDelete(
        messageId: String
    ): Result<RichMessage> = runCatching {
        RichMessage(
            action = "DELETE",
            text = "تم حذف هذه الرسالة",
            deleteOf = messageId
        )
    }

    fun buildReaction(
        messageId: String,
        emoji: String?
    ): Result<RichMessage> = runCatching {
        if (emoji == null) {
            RichMessage(action = "REACTION_REMOVE", reactionOf = messageId)
        } else {
            RichMessage(action = "REACTION", reactionOf = messageId, emoji = emoji)
        }
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
