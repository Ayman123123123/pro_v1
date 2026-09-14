package com.red.sovereign.core

/**
 * Phase-1 (2026-09-14): عنصر نائب للرسائل الواردة التي تعذّر فك تشفيرها مؤقتاً
 * (جلسة Signal/SenderKey لم تكتمل بعد).
 *
 * القواعد:
 * - محلي فقط — لا يُرسل على السلك أبداً ولا يُخزَّن في السيرفر.
 * - action = MESSAGE (ضمن القائمة البيضاء) + وسم مميز في بداية النص.
 * - عند نجاح الفك لاحقاً يُستبدل النص الحقيقي عبر REPLACE + يُنشر تحديث للواجهة.
 * - لا يُرسل ACK لرسالة نائبة أبداً — فيعيد السيرفر تسليمها تلقائياً (retry مجاني
 *   عبر مضخة Redelivery + إعادة تشغيل الاتصال)، وكل إعادة تسليم تحاول الفك من جديد.
 */
const val PENDING_DECRYPT_MARKER = "⏳⏳PENDING_DECRYPT⏳⏳"

/** يبني الحمولة المحلية للعنصر النائب. */
fun pendingDecryptPayload(): ByteArray =
    RichMessage.encode(RichMessage(text = "$PENDING_DECRYPT_MARKER ${pendingDecryptDisplayText()}"))

/** هل هذه الحمولة عنصر نائب بانتظار الفك؟ */
fun isPendingDecryptPlaceholder(plaintext: ByteArray): Boolean =
    runCatching {
        RichMessage.decode(plaintext)?.text?.startsWith(PENDING_DECRYPT_MARKER) == true
    }.getOrDefault(false)

/** النص المعروض للمستخدم بدل المحتوى المشفر. */
fun pendingDecryptDisplayText(): String = "🔒 بانتظار الرسالة — سيتم فك تشفيرها تلقائياً"
