package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.PasswordResetToken
import io.github.rygel.outerstellar.platform.service.EmailService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows

class PasswordResetServiceTest {

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val passwordEncoder = mockk<PasswordEncoder>(relaxed = true)
    private val resetRepository = mockk<PasswordResetRepository>(relaxed = true)
    private val emailService = mockk<EmailService>(relaxed = true)

    private val user =
        User(
            id = UUID.randomUUID(),
            username = "reset-user",
            email = "reset-user@example.com",
            passwordHash = "hash",
            role = UserRole.USER,
        )

    @Test
    fun `requestPasswordReset falls back to relative URL when app base URL is absent`() {
        every { userRepository.findByEmail(user.email) } returns user

        val service =
            PasswordResetService(
                userRepository = userRepository,
                passwordEncoder = passwordEncoder,
                resetRepository = resetRepository,
                emailService = emailService,
                appBaseUrl = null,
            )

        val token = service.requestPasswordReset(user.email)
        assertNotNull(token)

        val body = slot<String>()
        verify { emailService.send(user.email, any(), capture(body)) }
        assertTrue(body.captured.contains("/auth/reset?token="), "Reset email should contain a relative reset URL")
    }

    @Test
    fun `resetPassword throttles repeated invalid token attempts`() {
        every { resetRepository.findByToken("bad-token") } returns null

        val service =
            PasswordResetService(
                userRepository = userRepository,
                passwordEncoder = passwordEncoder,
                resetRepository = resetRepository,
                emailService = emailService,
            )

        repeat(5) {
            val exception =
                assertThrows<IllegalArgumentException> { service.resetPassword("bad-token", "newpassword123") }
            assertEquals("Invalid reset token", exception.message)
        }

        val throttled = assertThrows<IllegalArgumentException> { service.resetPassword("bad-token", "newpassword123") }
        assertEquals("Too many password reset attempts. Please request a new reset token.", throttled.message)
    }

    @Test
    fun `resetPassword clears throttle state after successful reset`() {
        val token = "valid-token"
        every { resetRepository.findByToken(token) } returns
            PasswordResetToken(
                userId = user.id,
                token = token,
                expiresAt = Instant.now().plusSeconds(600),
                used = false,
            )
        every { userRepository.findById(user.id) } returns user
        every { passwordEncoder.encode("newpassword123") } returns "updated-hash"

        val service =
            PasswordResetService(
                userRepository = userRepository,
                passwordEncoder = passwordEncoder,
                resetRepository = resetRepository,
                emailService = emailService,
            )

        service.resetPassword(token, "newpassword123")
        verify { resetRepository.markUsed(token) }
    }
}
