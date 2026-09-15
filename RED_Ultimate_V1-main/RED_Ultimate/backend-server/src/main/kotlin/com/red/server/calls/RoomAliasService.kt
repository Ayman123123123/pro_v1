package com.red.server.calls

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * G13 — جدول alias legacy→canonical في Redis مع سقوط ذاكرة محلية.
 *
 * • Create: canonicalize() تولّد معرفاً مسبوقاً دائماً (CONF_/LIVE_/FRND_/GRP_)
 *   وتربط أي legacy مُدخل إلى القانوني.
 * • Join/Ticket/WS: resolve() تعيد القانوني إن وُجد alias، وإلا الخام مقلّماً.
 * • الردود تُرجع canonicalId صريحاً بجانب roomId القانوني.
 *
 * Redis غائب/معطل = سلوك الذاكرة فقط (لا كسر محلي). المفتاح: red:room:alias:{legacy}.
 */
@Service
class RoomAliasService(
    private val redis: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(RoomAliasService::class.java)

    fun resolve(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return v
        // ذاكرة أولاً (الأسرع + تعمل بلا Redis في الاختبارات المحلية).
        RoomSeparationPolicy.aliasOf(v)?.let { return it }
        runCatching {
            redis.opsForValue().get(aliasKey(v))?.trim()?.takeIf { it.isNotBlank() }?.let { canonical ->
                RoomSeparationPolicy.linkAlias(v, canonical)
                return canonical
            }
        }.onFailure { log.debug("Room alias Redis resolve skipped for {}: {}", v, it.message) }
        // سقوط لذاكرة السياسة ثم الخام.
        return RoomSeparationPolicy.resolve(v)
    }

    fun link(legacyRaw: String?, canonical: String) {
        val legacy = legacyRaw?.trim().orEmpty()
        if (legacy.isEmpty() || legacy == canonical || canonical.isBlank()) return
        if (RoomSeparationPolicy.kindOf(legacy) != RoomSeparationPolicy.RoomKind.LEGACY) return
        RoomSeparationPolicy.linkAlias(legacy, canonical)
        runCatching {
            redis.opsForValue().set(aliasKey(legacy), canonical, ALIAS_TTL_DAYS, TimeUnit.DAYS)
        }.onFailure { log.debug("Room alias Redis persist skipped {}->{}: {}", legacy, canonical, it.message) }
    }

    /**
     * مسار الإنشاء فقط: معرف قانوني مسبوق دائماً + ربط alias + رفض متقاطع
     * (مبادأ ببادئة مخالفة يُرفض بدل تمريره).
     */
    fun canonicalize(prefix: String, raw: String?, generate: () -> String): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return prefix + generate()
        val kind = RoomSeparationPolicy.kindOf(v)
        if (kind != RoomSeparationPolicy.RoomKind.LEGACY) {
            require(v.startsWith(prefix)) { "WRONG_NAMESPACE: expected $prefix room, got $v" }
            require(v.matches(RoomSeparationPolicy.ROOM_ID)) { "Invalid room ID" }
            return v
        }
        val canonical = RoomSeparationPolicy.canonicalizeForCreate(prefix, v, generate)
        link(v, canonical)
        // alias لنسخة التنظيف أيضاً إن اختلفت (مدخل غير صالح الأصلي).
        return canonical
    }

    companion object {
        private const val ALIAS_TTL_DAYS = 30L
        private fun aliasKey(legacy: String) = "red:room:alias:$legacy"
    }
}
