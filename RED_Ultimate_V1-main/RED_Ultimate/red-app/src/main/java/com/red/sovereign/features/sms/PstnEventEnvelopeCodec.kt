package com.red.sovereign.features.sms

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * يحول غلاف الخادم القياسي `{type, data}` إلى نموذج التطبيق المسطح.
 * يبقى متوافقاً مع الرسائل المسطحة القديمة أثناء الترقية فقط.
 */
internal object PstnEventEnvelopeCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): PstnWsEnvelope {
        val root = json.parseToJsonElement(text) as? JsonObject
            ?: error("PSTN event is not a JSON object")
        val type = (root["type"] as? JsonPrimitive)?.content.orEmpty()
        val data = root["data"] as? JsonObject
        val normalized = if (data == null) {
            root
        } else {
            JsonObject(data.toMutableMap().apply { put("type", JsonPrimitive(type)) })
        }
        return json.decodeFromJsonElement<PstnWsEnvelope>(normalized)
    }
}
