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
    private val presence = mock<UserStatusService>()
    private val service = ContactService(jdbc, users, redis, presence)

    @Test
    fun `presence excludes identities that are not established contacts`() {
        val owner = UUID.randomUUID()
        // هوية الطالب لازمة: `presence` تقرأ redId الخاص به لتقييم خصوصية
        // online_status (NOBODY/CONTACTS تُخفى عن غير المخوّل). بلا هذا
        // الـstub تعود الدالة بخريطة فارغة قبل أي فحص حضور — وهو سلوك
        // صحيح للإنتاج (لا حضور بلا هوية طالب) وكان الاختبار وحده متأخّرًا.
        whenever(users.findById(owner)).thenReturn(
            Optional.of(
                UserAccount(
                    id = owner,
                    redId = "90735",
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

        val result = service.presence(owner, listOf("85248", "87203"))

        // 87203 ليس في جهات الاتصال ⇒ لا يظهر في النتيجة إطلاقًا
        assertEquals(mapOf("85248" to true), result)
    }
}
