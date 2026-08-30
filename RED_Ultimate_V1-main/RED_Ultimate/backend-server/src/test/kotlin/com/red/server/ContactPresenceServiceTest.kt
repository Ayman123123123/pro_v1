package com.red.server

import com.red.server.auth.ContactService
import com.red.server.auth.PublicRedProfile
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.social.UserStatusService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.util.Optional
import java.util.UUID

class ContactPresenceServiceTest {
    private val jdbc = mock<JdbcTemplate>()
    private val users = mock<UserAccountRepository>()
    private val redis = mock<RedisTemplate<String, String>>()
    private val zset = mock<ZSetOperations<String, String>>()
    private val statusService = mock<UserStatusService>()
    private val service = ContactService(jdbc, users, redis, statusService)

    @Test
    fun `presence excludes identities that are not established contacts`() {
        val owner = UUID.randomUUID()

        // `ContactService.presence` تقرأ redId الطالب أولًا لتقييم خصوصية
        // الظهور (NOBODY/CONTACTS تُقاس بالنسبة إليه). بلا هذا المزدوج تُعيد
        // Mockito ‏`Optional.empty()` فتخرج الدالة بـ`emptyMap()` قبل أن تصل
        // إلى منطق التصفية المقصود — أي أن الاختبار كان يفشل على أمرٍ لا
        // يخصّ ما يزعم قياسه.
        whenever(users.findById(owner)).thenReturn(
            Optional.of(
                UserAccount(
                    id = owner,
                    redId = "90001",
                    username = "owner",
                    displayName = "Owner",
                    status = AccountStatus.APPROVED
                )
            )
        )
        whenever(jdbc.query<PublicRedProfile>(any(), any<RowMapper<PublicRedProfile>>(), eq(owner)))
            .thenReturn(listOf(PublicRedProfile("85248", "ahmed", "أحمد")))
        whenever(redis.opsForZSet()).thenReturn(zset)
        whenever(zset.score("red:presence:index", "85248"))
            .thenReturn(System.currentTimeMillis().toDouble())

        // 87203 مطلوب لكنه ليس جهةَ اتصال ⇒ يُستبعَد تمامًا من الرد،
        // لا يُعاد بـ`false`: وجودُه بأي قيمة يُفصح بأن المعرّف قائم.
        val result = service.presence(owner, listOf("85248", "87203"))

        assertEquals(mapOf("85248" to true), result)
    }
}
