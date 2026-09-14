package com.red.sovereign.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * تحويل JSON ⟷ خرائط/قوائم Kotlin عبر `org.json` المضمَّن في أندرويد.
 *
 * سبب الوجود: كان `features/admin/AdminViewModel.kt` يجرّ Jackson كاملاً
 * (`jackson-databind` + `jackson-module-kotlin`) لتحليل بضع خرائط صغيرة،
 * وكانت النسخة مثبَّتة يدوياً 2.15.2 في كتلة `dependencies` ثانية تخالف
 * 2.19.2 في كتالوج النسخ — أي إصداران للمكتبة نفسها في بناء واحد.
 * `org.json` جزء من إطار أندرويد (صفر بايت في APK) ويكفي لهذه الحاجة،
 *
 * `JSONObject.NULL` يُحوَّل إلى `null` حقيقي: لو مرّ كما هو لصار
 * `as? Number` يفشل بصمت على قيمة «موجودة لكنها فارغة».
 */
internal fun parseJsonMap(raw: String): Map<String, Any?> = JSONObject(raw).toKotlinMap()

internal fun parseJsonList(raw: String): List<Any?> = JSONArray(raw).toKotlinList()

internal fun JSONObject.toKotlinMap(): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>(length())
    for (key in keys()) out[key] = unwrapJson(opt(key))
    return out
}

internal fun JSONArray.toKotlinList(): List<Any?> = (0 until length()).map { unwrapJson(opt(it)) }

private fun unwrapJson(value: Any?): Any? = when {
    value == null || value === JSONObject.NULL -> null
    value is JSONObject -> value.toKotlinMap()
    value is JSONArray -> value.toKotlinList()
    else -> value
}

/** بناء نص JSON من خريطة Kotlin متداخلة — بديل `writeValueAsString`. */
internal fun jsonBodyOf(values: Map<String, Any?>): String = toJsonValue(values).toString()

private fun toJsonValue(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().apply {
        value.forEach { (key, item) -> put(key.toString(), toJsonValue(item)) }
    }
    is Iterable<*> -> JSONArray().apply {
        value.forEach { put(toJsonValue(it)) }
    }
    is Array<*> -> JSONArray().apply {
        value.forEach { put(toJsonValue(it)) }
    }
    else -> value
}
