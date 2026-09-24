package com.red.server.social

import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * فحص الجمهور الموحّد — مصدر الحقيقة الوحيد لحظر الاتجاهين والصداقة المتبادلة.
 *
 * القاعدة: الحظر الثنائي الاتجاه يتغلّب على كل رؤية (فيد/حالات/قصص/ظهور/مكالمات).
 * الصداقة = صفّان متبادلان في `red_contacts` (يكتبهما ContactService عند القبول).
 *
 * كائن بلا حالة يستقبل JdbcTemplate الموجود أصلًا في كل خدمة — بلا حقن
 * جديد ولا دورات اعتماد بين الحزم (calls/auth تستدعيه أيضًا).
 */
object AudienceGuard {
    /** حظر في أي اتجاه بين حسابين؟ */
    fun isBlockedEitherDirection(jdbc: JdbcTemplate, first: UUID, second: UUID): Boolean {
        if (first == second) return false
        return (jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)",
            Int::class.java, first, second, second, first
        ) ?: 0) > 0
    }

    /** معرّفات الكتّاب المحظورين ثنائيًا لاستبعادهم من الفيد (authorId مخزّن كنص UUID). */
    fun blockedAuthorIds(jdbc: JdbcTemplate, userId: UUID): List<String> {
        return jdbc.queryForList(
            "SELECT DISTINCT CASE WHEN blocker_id=? THEN blocked_id::text ELSE blocker_id::text END FROM user_blocks WHERE blocker_id=? OR blocked_id=?",
            String::class.java, userId, userId, userId
        ).filterNotNull().filter(String::isNotBlank).distinct()
    }

    /** صداقة متبادلة (صفّان) بين حسابين؟ */
    fun isMutualContact(jdbc: JdbcTemplate, first: UUID, second: UUID): Boolean {
        if (first == second) return true
        val a = jdbc.queryForObject(
            "SELECT COUNT(*) FROM red_contacts WHERE owner_id=? AND contact_id=?",
            Int::class.java, first, second
        ) ?: 0
        if (a == 0) return false
        val b = jdbc.queryForObject(
            "SELECT COUNT(*) FROM red_contacts WHERE owner_id=? AND contact_id=?",
            Int::class.java, second, first
        ) ?: 0
        return b > 0
    }

    /** معرّفات الأصدقاء المتبادلين (نصوص UUID) لترشيح فيد FRIENDS. */
    fun mutualFriendIds(jdbc: JdbcTemplate, userId: UUID): List<String> {
        return jdbc.queryForList(
            """SELECT a.contact_id::text
               FROM red_contacts a
               WHERE a.owner_id = ?
                 AND EXISTS (
                     SELECT 1 FROM red_contacts b
                     WHERE b.owner_id = a.contact_id AND b.contact_id = a.owner_id
                 )""",
            String::class.java, userId
        ).filterNotNull()
    }
}
