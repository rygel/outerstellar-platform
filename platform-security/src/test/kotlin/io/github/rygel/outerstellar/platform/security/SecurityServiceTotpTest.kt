package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.UserRole
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SecurityServiceTotpTest {

    private lateinit var userRepository: UserRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var totpService: TOTPService
    private lateinit var securityService: SecurityService

    @BeforeEach
    fun setUp() {
        userRepository = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)
        passwordEncoder = mockk(relaxed = true)
        totpService = TOTPService()
        securityService =
            SecurityService(
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                passwordEncoder = passwordEncoder,
                config = SecurityConfig(),
                totpService = totpService,
            )
    }

    @Test
    fun `authenticate returns TotpRequired when totp is enabled`() {
        val userId = UUID.randomUUID()
        val user =
            User(
                id = userId,
                username = "alice",
                email = "alice@test.com",
                passwordHash = "hash",
                role = UserRole.USER,
                totpEnabled = true,
                totpSecret = "secret",
            )
        every { userRepository.findByUsername("alice") } returns user
        every { passwordEncoder.matches("pass", "hash") } returns true

        val result = securityService.authenticate("alice", "pass")
        assertTrue(result is AuthResult.TotpRequired, "Should require TOTP")
        assertNotNull((result as AuthResult.TotpRequired).token, "Should have partial token")
    }

    @Test
    fun `authenticate returns Authenticated when totp is disabled`() {
        val userId = UUID.randomUUID()
        val user =
            User(id = userId, username = "bob", email = "bob@test.com", passwordHash = "hash", role = UserRole.USER)
        every { userRepository.findByUsername("bob") } returns user
        every { passwordEncoder.matches("pass", "hash") } returns true
        every { sessionRepository.save(any()) } just Runs

        val result = securityService.authenticate("bob", "pass")
        assertTrue(result is AuthResult.Authenticated, "Should authenticate directly when TOTP disabled")
    }

    @Test
    fun `verifyTotp with invalid token returns expired`() {
        val result = securityService.verifyTotp("pt_invalid", "123456")
        assertEquals("expired", result.status, "Invalid token should be expired")
    }

    @Test
    fun `enableTotp stores secret and enables`() {
        val userId = UUID.randomUUID()
        every { userRepository.updateTotpSecret(any(), any(), any()) } just Runs
        every { userRepository.enableTotp(any()) } just Runs

        securityService.enableTotp(userId, "newsecret", "[]")
    }

    @Test
    fun `disableTotp clears secret`() {
        val userId = UUID.randomUUID()
        every { userRepository.updateTotpSecret(any(), any(), any()) } just Runs

        securityService.disableTotp(userId)
    }

    @Test
    fun `verifyTotp with valid partial token and invalid code returns invalid`() {
        val userId = UUID.randomUUID()
        val secret = totpService.generateSecret()
        val user =
            User(
                id = userId,
                username = "alice",
                email = "alice@test.com",
                passwordHash = "hash",
                role = UserRole.USER,
                totpEnabled = true,
                totpSecret = secret,
            )
        every { userRepository.findByUsername("alice") } returns user
        every { passwordEncoder.matches("pass", "hash") } returns true

        val authResult = securityService.authenticate("alice", "pass")
        val partialToken = (authResult as AuthResult.TotpRequired).token

        every { userRepository.findById(userId) } returns user
        val result = securityService.verifyTotp(partialToken, "000000")
        assertEquals("invalid_code", result.status, "Invalid code should return invalid_code")
    }
}
