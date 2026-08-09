package com.red.server

import com.red.server.auth.PublicDirectoryController
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.util.UUID

class PublicDirectoryControllerTest {
    private val users = mock<UserAccountRepository>()
    private val controller = PublicDirectoryController(users)

    @Test
    fun `exact username search returns only approved public profile fields`() {
        val callerId = UUID.randomUUID()
        whenever(users.findByUsernameIgnoreCase("alithefriend")).thenReturn(
            UserAccount(redId = "RED-CCCC-DDDD", username = "alithefriend", displayName = "Ali Friend", status = AccountStatus.APPROVED)
        )
        val auth = UsernamePasswordAuthenticationToken(callerId.toString(), "token")

        val result = controller.search("alithefriend", auth)

        assertEquals(1, result.size)
        assertEquals("RED-CCCC-DDDD", result.single().redId)
        assertEquals("Ali Friend", result.single().displayName)
    }
}
