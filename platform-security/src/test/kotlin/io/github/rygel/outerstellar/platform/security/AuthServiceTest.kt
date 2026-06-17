package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.RegistrationDisabledException
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows

class AuthServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var auditRepository: AuditRepository
    private lateinit var service: AuthService

    private val testUser =
        User(
            id = UUID.randomUUID(),
            username = "testuser",
            email = "testuser@test.com",
            passwordHash = "hashed_password",
            role = UserRole.USER,
        )

    @BeforeEach
    fun setup() {
        userRepository = mockk(relaxed = true)
        passwordEncoder = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        service =
            AuthService(
                userRepository = userRepository,
                passwordEncoder = passwordEncoder,
                auditRepository = auditRepository,
                totpService = TOTPService(BCryptPasswordEncoder(logRounds = 4)),
            )
    }

    @Test
    fun `authenticate returns user on valid credentials`() {
        every { userRepository.findByUsername("testuser") } returns testUser
        every { passwordEncoder.matches("correctpass", testUser.passwordHash) } returns true

        val result = service.authenticate("testuser", "correctpass")

        assertTrue(result is AuthResult.Authenticated)
        assertEquals(testUser.id, result.user.id)
        assertEquals("testuser", result.user.username)
    }

    @Test
    fun `authenticate returns null for wrong password`() {
        every { userRepository.findByUsername("testuser") } returns testUser
        every { passwordEncoder.matches("wrongpass", testUser.passwordHash) } returns false

        val result = service.authenticate("testuser", "wrongpass")

        assertNull(result)
    }

    @Test
    fun `authenticate returns null for disabled user`() {
        val disabledUser = testUser.copy(enabled = false)
        every { userRepository.findByUsername("testuser") } returns disabledUser

        val result = service.authenticate("testuser", "anypass")

        assertNull(result)
    }

    @Test
    fun `authenticate returns null for unknown user`() {
        every { userRepository.findByUsername("unknown") } returns null

        val result = service.authenticate("unknown", "anypass")

        assertNull(result)
    }

    @Test
    fun `authenticate returns null for locked account`() {
        val lockedUser = testUser.copy(lockedUntil = Instant.now().plusSeconds(300))
        every { userRepository.findByUsername("testuser") } returns lockedUser

        val result = service.authenticate("testuser", "correctpass")

        assertNull(result, "Locked account should not authenticate")
    }

    @Test
    fun `authenticate succeeds when lock has expired`() {
        val unlockedUser = testUser.copy(lockedUntil = Instant.now().minusSeconds(60), failedLoginAttempts = 3)
        every { userRepository.findByUsername("testuser") } returns unlockedUser
        every { passwordEncoder.matches("correctpass", unlockedUser.passwordHash) } returns true

        val result = service.authenticate("testuser", "correctpass")

        assertTrue(result is AuthResult.Authenticated, "Account with expired lock should authenticate")
        verify { userRepository.resetFailedLoginAttempts(unlockedUser.id) }
    }

    @Test
    fun `authenticate increments failed attempts on wrong password`() {
        every { userRepository.findByUsername("testuser") } returns testUser
        every { passwordEncoder.matches("wrong", testUser.passwordHash) } returns false
        every { userRepository.incrementFailedLoginAttempts(testUser.id) } returns 1

        val result = service.authenticate("testuser", "wrong")

        assertNull(result)
        verify { userRepository.incrementFailedLoginAttempts(testUser.id) }
    }

    @Test
    fun `authenticate locks account after threshold exceeded`() {
        every { userRepository.findByUsername("testuser") } returns testUser
        every { passwordEncoder.matches("wrong", testUser.passwordHash) } returns false
        every { userRepository.incrementFailedLoginAttempts(testUser.id) } returns 10

        val result = service.authenticate("testuser", "wrong")

        assertNull(result)
        verify { userRepository.updateLockedUntil(eq(testUser.id), any()) }
    }

    @Test
    fun `authenticate resets failed attempts on success`() {
        val userWithAttempts = testUser.copy(failedLoginAttempts = 3)
        every { userRepository.findByUsername("testuser") } returns userWithAttempts
        every { passwordEncoder.matches("correctpass", userWithAttempts.passwordHash) } returns true

        val result = service.authenticate("testuser", "correctpass")

        assertTrue(result is AuthResult.Authenticated)
        verify { userRepository.resetFailedLoginAttempts(userWithAttempts.id) }
    }

    @Test
    fun `authenticate audits AUTHENTICATION_FAILED when user not found`() {
        every { userRepository.findByUsername("unknown") } returns null

        service.authenticate("unknown", "anypass")

        verify {
            auditRepository.log(
                match {
                    it.action == "AUTHENTICATION_FAILED" &&
                        it.detail == "User not found" &&
                        it.targetUsername == "unknown"
                }
            )
        }
    }

    @Test
    fun `authenticate audits AUTHENTICATION_FAILED when user disabled`() {
        val disabledUser = testUser.copy(enabled = false)
        every { userRepository.findByUsername("testuser") } returns disabledUser

        service.authenticate("testuser", "anypass")

        verify {
            auditRepository.log(match { it.action == "AUTHENTICATION_FAILED" && it.detail == "Account disabled" })
        }
    }

    @Test
    fun `authenticate audits AUTHENTICATION_FAILED when account locked`() {
        val lockedUser = testUser.copy(lockedUntil = Instant.now().plusSeconds(300))
        every { userRepository.findByUsername("testuser") } returns lockedUser

        service.authenticate("testuser", "correctpass")

        verify {
            auditRepository.log(
                match { it.action == "AUTHENTICATION_FAILED" && it.detail?.startsWith("Account locked until") == true }
            )
        }
    }

    @Test
    fun `authenticate audits AUTHENTICATION_FAILED when password wrong`() {
        every { userRepository.findByUsername("testuser") } returns testUser
        every { passwordEncoder.matches("wrongpass", testUser.passwordHash) } returns false
        every { userRepository.incrementFailedLoginAttempts(testUser.id) } returns 1

        service.authenticate("testuser", "wrongpass")

        verify {
            auditRepository.log(match { it.action == "AUTHENTICATION_FAILED" && it.detail == "Invalid password" })
        }
    }

    @Test
    fun `register throws RegistrationDisabledException when registration is disabled`() {
        val disabledService =
            AuthService(
                userRepository = userRepository,
                passwordEncoder = passwordEncoder,
                auditRepository = auditRepository,
                config = SecurityConfig(registrationEnabled = false),
                totpService = TOTPService(BCryptPasswordEncoder(logRounds = 4)),
            )

        assertThrows<RegistrationDisabledException> { disabledService.register("new@test.com", "ValidP@ss1") }
    }

    @Test
    fun `register throws on duplicate username`() {
        every { userRepository.findByUsername("existing") } returns testUser

        assertThrows<UsernameAlreadyExistsException> {
            service.register("existing", "ValidP@ss1" + java.util.UUID.randomUUID().toString())
        }
    }

    @Test
    fun `register throws on short password`() {
        every { userRepository.findByUsername("newuser") } returns null

        assertThrows<WeakPasswordException> { service.register("newuser", "short") }
    }

    @Test
    fun `register throws on long password`() {
        every { userRepository.findByUsername("newuser") } returns null

        assertThrows<WeakPasswordException> { service.register("newuser", "A".repeat(129)) }
    }

    @Test
    fun `register trims whitespace before validation`() {
        every { userRepository.findByUsername("newuser") } returns null
        every { passwordEncoder.encode("Validp@ss1") } returns "encoded_hash"

        val result = service.register("newuser", "  Validp@ss1  ")

        assertEquals("newuser", result.username)
        assertEquals(UserRole.USER, result.role)
        val userSlot = slot<User>()
        verify { userRepository.save(capture(userSlot)) }
        assertEquals("encoded_hash", userSlot.captured.passwordHash)
    }

    @Test
    fun `register creates user with encoded password`() {
        every { userRepository.findByUsername("newuser") } returns null
        every { passwordEncoder.encode("Validp@ss1") } returns "encoded_hash"

        val result = service.register("newuser", "Validp@ss1")

        assertEquals("newuser", result.username)
        assertEquals(UserRole.USER, result.role)

        val userSlot = slot<User>()
        verify { userRepository.save(capture(userSlot)) }
        assertEquals("encoded_hash", userSlot.captured.passwordHash)
    }
}
