package com.red.server

import com.red.server.auth.PublicDirectoryController
import com.red.server.auth.RateLimitExceededException
import com.red.server.auth.RateLimitService
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.util.UUID

class PublicDirectoryControllerTest {
    private val users = mock<UserAccountRepository>()
    private val rateLimiter = mock<RateLimitService>()
    private val controller = PublicDirectoryController(users, rateLimiter)

    private fun auth(id: UUID = UUID.randomUUID()) =
        UsernamePasswordAuthenticationToken(id.toString(), "token")

    @Test
    fun `exact username search returns only approved public profile fields`() {
        whenever(users.findByUsernameIgnoreCase("alithefriend")).thenReturn(
            UserAccount(redId = "28261", username = "alithefriend", displayName = "Ali Friend", status = AccountStatus.APPROVED)
        )

        val result = controller.search("alithefriend", auth())

        assertEquals(1, result.size)
        assertEquals("28261", result.single().redId)
        assertEquals("Ali Friend", result.single().displayName)
    }

    @Test
    fun `five digit query searches by red id not by username`() {
        // انحدار: الفحص السابق كان `term.startsWith("RED-")` بينما المولّد
        // ينتج `YNS-`، فلم يكن البحث بالمعرّف يعمل إطلاقًا — كان يسقط إلى
        // البحث بالاسم فيعود فارغًا دائمًا.
        whenever(users.findByRedId("28261")).thenReturn(
            UserAccount(redId = "28261", username = "ali", displayName = "Ali", status = AccountStatus.APPROVED)
        )

        val result = controller.search("28261", auth())

        assertEquals("28261", result.single().redId)
        verify(users, never()).findByUsernameIgnoreCase(any())
    }

    @Test
    fun `legacy pasted prefix is normalized before lookup`() {
        whenever(users.findByRedId("28261")).thenReturn(
            UserAccount(redId = "28261", username = "ali", displayName = "Ali", status = AccountStatus.APPROVED)
        )

        assertEquals("28261", controller.search("YNS-28261", auth()).single().redId)
        assertEquals("28261", controller.search("RED-28261", auth()).single().redId)
    }

    @Test
    fun `pending or banned accounts are never disclosed`() {
        whenever(users.findByRedId("28261")).thenReturn(
            UserAccount(redId = "28261", username = "ali", displayName = "Ali", status = AccountStatus.PENDING)
        )

        assertTrue(controller.search("28261", auth()).isEmpty())
    }

    @Test
    fun `caller never sees itself in results`() {
        val callerId = UUID.randomUUID()
        whenever(users.findByRedId("28261")).thenReturn(
            UserAccount(id = callerId, redId = "28261", username = "me", displayName = "Me", status = AccountStatus.APPROVED)
        )

        assertTrue(controller.search("28261", auth(callerId)).isEmpty())
    }

    @Test
    fun `every search consumes rate limit budget keyed to the caller`() {
        // فضاء المعرّفات 90,000 فقط، فبلا حدّ معدل يستطيع حساب معتمد واحد
        // حصاد الدليل كاملًا. الحدّ مرتبط بهوية المتصل لا بعنوان IP.
        val callerId = UUID.randomUUID()
        whenever(users.findByRedId(any())).thenReturn(null)

        controller.search("28261", auth(callerId))

        verify(rateLimiter).check(
            eq(PublicDirectoryController.RATE_LIMIT_NAMESPACE),
            eq(callerId.toString()),
            eq(PublicDirectoryController.DIRECTORY_MAX_QUERIES),
            any(),
        )
    }

    @Test
    fun `rate limit breach aborts before touching the repository`() {
        whenever(rateLimiter.check(any(), any(), any(), any()))
            .thenThrow(RateLimitExceededException())

        assertThrows(RateLimitExceededException::class.java) {
            controller.search("28261", auth())
        }
        verify(users, never()).findByRedId(any())
        verify(users, never()).findByUsernameIgnoreCase(any())
    }

    @Test
    fun `query length bounds are enforced`() {
        assertThrows(IllegalArgumentException::class.java) { controller.search("ab", auth()) }
        assertThrows(IllegalArgumentException::class.java) { controller.search("x".repeat(33), auth()) }
    }
}
