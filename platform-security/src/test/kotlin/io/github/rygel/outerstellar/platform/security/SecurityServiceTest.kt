package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.ApiKey
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows

class SecurityServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var auditRepository: AuditRepository
    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var service: SecurityService
    private val totpService = TOTPService()

    private val testUser =
        User(
            id = UUID.randomUUID(),
            username = "testuser",
            email = "testuser@test.com",
            passwordHash = "hashed_password",
            role = UserRole.USER,
        )

    private val adminUser =
        User(
            id = UUID.randomUUID(),
            username = "admin",
            email = "admin@test.com",
            passwordHash = "hashed_admin",
            role = UserRole.ADMIN,
        )

    @BeforeEach
    fun setup() {
        userRepository = mockk(relaxed = true)
        passwordEncoder = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        apiKeyRepository = mockk(relaxed = true)
        service =
            SecurityService(
                userRepository = userRepository,
                passwordEncoder = passwordEncoder,
                auditRepository = auditRepository,
                apiKeyRepository = apiKeyRepository,
                totpService = totpService,
            )
    }

    // ---- authenticate ----

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

    // ---- lockout ----

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
    fun `unlockAccount throws for non-admin caller`() {
        every { userRepository.findById(adminUser.id) } returns adminUser.copy(role = UserRole.USER)

        assertThrows<InsufficientPermissionException> { service.unlockAccount(adminUser.id, testUser.id) }
    }

    @Test
    fun `unlockAccount resets failed attempts for target user`() {
        every { userRepository.findById(adminUser.id) } returns adminUser
        every { userRepository.findById(testUser.id) } returns testUser

        service.unlockAccount(adminUser.id, testUser.id)

        verify { userRepository.resetFailedLoginAttempts(testUser.id) }
    }

    // ---- authentication failure audit ----

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

    // ---- register ----

    @Test
    fun `register throws on duplicate username`() {
        every { userRepository.findByUsername("existing") } returns testUser

        assertThrows<UsernameAlreadyExistsException> {
            service.register("existing", java.util.UUID.randomUUID().toString())
        }
    }

    @Test
    fun `register throws on short password`() {
        every { userRepository.findByUsername("newuser") } returns null

        assertThrows<WeakPasswordException> { service.register("newuser", "short") }
    }

    @Test
    fun `register creates user with encoded password`() {
        every { userRepository.findByUsername("newuser") } returns null
        every { passwordEncoder.encode("validpassword") } returns "encoded_hash"

        val result = service.register("newuser", "validpassword")

        assertEquals("newuser", result.username)
        assertEquals(UserRole.USER, result.role)

        val userSlot = slot<User>()
        verify { userRepository.save(capture(userSlot)) }
        assertEquals("encoded_hash", userSlot.captured.passwordHash)
    }

    // ---- changePassword ----

    @Test
    fun `changePassword throws on wrong current password`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("wrongcurrent", testUser.passwordHash) } returns false

        assertThrows<WeakPasswordException> { service.changePassword(testUser.id, "wrongcurrent", "newpassword1") }
    }

    @Test
    fun `changePassword updates hash on success`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("currentpass", testUser.passwordHash) } returns true
        every { passwordEncoder.encode("newpassword1") } returns "new_hash"

        service.changePassword(testUser.id, "currentpass", "newpassword1")

        val userSlot = slot<User>()
        verify { userRepository.save(capture(userSlot)) }
        assertEquals("new_hash", userSlot.captured.passwordHash)
    }

    @Test
    fun `changePassword invalidates all sessions for the user`() {
        val sessionRepository: SessionRepository = mockk(relaxed = true)
        val serviceWithSessions =
            SecurityService(
                userRepository = userRepository,
                passwordEncoder = passwordEncoder,
                auditRepository = auditRepository,
                sessionRepository = sessionRepository,
                totpService = totpService,
            )
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("currentpass", testUser.passwordHash) } returns true
        every { passwordEncoder.encode("newpassword1") } returns "new_hash"

        serviceWithSessions.changePassword(testUser.id, "currentpass", "newpassword1")

        verify { sessionRepository.deleteByUserId(testUser.id) }
    }

    @Test
    fun `changePassword works without session repository`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("currentpass", testUser.passwordHash) } returns true
        every { passwordEncoder.encode("newpassword1") } returns "new_hash"

        service.changePassword(testUser.id, "currentpass", "newpassword1")

        val userSlot = slot<User>()
        verify { userRepository.save(capture(userSlot)) }
        assertEquals("new_hash", userSlot.captured.passwordHash)
    }

    @Test
    fun `changePassword throws on short new password`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("currentpass", testUser.passwordHash) } returns true

        assertThrows<WeakPasswordException> { service.changePassword(testUser.id, "currentpass", "short") }
    }

    // ---- setUserEnabled ----

    @Test
    fun `setUserEnabled prevents self-disable`() {
        assertThrows<InsufficientPermissionException> { service.setUserEnabled(adminUser.id, adminUser.id, false) }
    }

    @Test
    fun `setUserEnabled updates target user`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findById(adminUser.id) } returns adminUser

        service.setUserEnabled(adminUser.id, testUser.id, false)

        verify { userRepository.updateEnabled(testUser.id, false) }
    }

    // ---- setUserRole ----

    @Test
    fun `setUserRole prevents self-demotion`() {
        assertThrows<InsufficientPermissionException> { service.setUserRole(adminUser.id, adminUser.id, UserRole.USER) }
    }

    @Test
    fun `setUserRole updates target user role`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findById(adminUser.id) } returns adminUser

        service.setUserRole(adminUser.id, testUser.id, UserRole.ADMIN)

        verify { userRepository.updateRole(testUser.id, UserRole.ADMIN) }
    }

    // ---- API key ----

    @Test
    fun `createApiKey returns key with osk prefix`() {
        val response = service.createApiKey(testUser.id, "my-key")

        assertTrue(response.key.startsWith("osk_"), "Key should start with osk_ prefix")
        assertEquals("my-key", response.name)
        assertTrue(response.keyPrefix.isNotBlank())
        verify { apiKeyRepository.save(any()) }
    }

    @Test
    fun `createApiKey throws on blank name`() {
        assertThrows<IllegalArgumentException> { service.createApiKey(testUser.id, "") }
    }

    @Test
    fun `authenticateApiKey returns user for valid key`() {
        val rawKey = "osk_abcdef1234567890abcdef1234567890"
        // Compute the expected hash the same way SecurityService does
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }

        val apiKey =
            ApiKey(id = 1L, userId = testUser.id, keyHash = expectedHash, keyPrefix = "osk_abcd", name = "test-key")

        every { apiKeyRepository.findByKeyHash(expectedHash) } returns apiKey
        every { userRepository.findById(testUser.id) } returns testUser

        val result = service.authenticateApiKey(rawKey)

        assertNotNull(result)
        assertEquals(testUser.id, result.id)
        verify { apiKeyRepository.updateLastUsed(1L) }
    }

    @Test
    fun `authenticateApiKey returns null for disabled key`() {
        val rawKey = "osk_abcdef1234567890abcdef1234567890"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }

        val disabledKey =
            ApiKey(
                id = 2L,
                userId = testUser.id,
                keyHash = expectedHash,
                keyPrefix = "osk_abcd",
                name = "disabled-key",
                enabled = false,
            )

        every { apiKeyRepository.findByKeyHash(expectedHash) } returns disabledKey

        val result = service.authenticateApiKey(rawKey)

        assertNull(result)
    }

    @Test
    fun `authenticateApiKey returns null for disabled user`() {
        val rawKey = "osk_abcdef1234567890abcdef1234567890"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }

        val apiKey =
            ApiKey(id = 3L, userId = testUser.id, keyHash = expectedHash, keyPrefix = "osk_abcd", name = "test-key")

        val disabledUser = testUser.copy(enabled = false)
        every { apiKeyRepository.findByKeyHash(expectedHash) } returns apiKey
        every { userRepository.findById(testUser.id) } returns disabledUser

        val result = service.authenticateApiKey(rawKey)

        assertNull(result)
    }

    @Test
    fun `authenticateApiKey returns null for unknown key`() {
        every { apiKeyRepository.findByKeyHash(any()) } returns null

        val result = service.authenticateApiKey("osk_nonexistent")

        assertNull(result)
    }

    @Test
    fun `createApiKey logs API_KEY_CREATED to audit`() {
        every { userRepository.findById(testUser.id) } returns testUser

        service.createApiKey(testUser.id, "my-audit-key")

        verify {
            auditRepository.log(
                match {
                    it.action == "API_KEY_CREATED" &&
                        it.detail?.contains("my-audit-key") == true &&
                        it.actorId == testUser.id.toString()
                }
            )
        }
    }

    @Test
    fun `deleteApiKey logs API_KEY_DELETED to audit`() {
        every { userRepository.findById(testUser.id) } returns testUser

        service.deleteApiKey(testUser.id, 42L)

        verify {
            auditRepository.log(
                match {
                    it.action == "API_KEY_DELETED" &&
                        it.detail?.contains("keyId=42") == true &&
                        it.actorId == testUser.id.toString()
                }
            )
        }
    }

    // ---- updateProfile ----

    @Test
    fun `updateProfile updates email`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findByEmail("new@test.com") } returns null

        service.updateProfile(testUser.id, "new@test.com")

        val saved = slot<User>()
        verify { userRepository.save(capture(saved)) }
        assertEquals("new@test.com", saved.captured.email)
    }

    @Test
    fun `updateProfile updates username when different`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findByEmail(testUser.email) } returns null
        every { userRepository.findByUsername("newname") } returns null

        service.updateProfile(testUser.id, testUser.email, newUsername = "newname")

        verify { userRepository.updateUsername(testUser.id, "newname") }
    }

    @Test
    fun `updateProfile throws when new username is already taken`() {
        val other = testUser.copy(id = UUID.randomUUID(), username = "taken")
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findByEmail(testUser.email) } returns null
        every { userRepository.findByUsername("taken") } returns other

        assertThrows<UsernameAlreadyExistsException> {
            service.updateProfile(testUser.id, testUser.email, newUsername = "taken")
        }
    }

    @Test
    fun `updateProfile skips username update when same as current`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findByEmail(testUser.email) } returns null

        service.updateProfile(testUser.id, testUser.email, newUsername = testUser.username)

        verify(exactly = 0) { userRepository.updateUsername(any(), any()) }
    }

    @Test
    fun `updateProfile updates avatar URL when changed`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findByEmail(testUser.email) } returns null

        service.updateProfile(testUser.id, testUser.email, newAvatarUrl = "https://example.com/avatar.png")

        verify { userRepository.updateAvatarUrl(testUser.id, "https://example.com/avatar.png") }
    }

    @Test
    fun `updateProfile clears avatar URL when blank`() {
        val userWithAvatar = testUser.copy(avatarUrl = "https://old.example.com/avatar.png")
        every { userRepository.findById(testUser.id) } returns userWithAvatar
        every { userRepository.findByEmail(testUser.email) } returns null

        service.updateProfile(testUser.id, testUser.email, newAvatarUrl = "")

        verify { userRepository.updateAvatarUrl(testUser.id, null) }
    }

    // ---- deleteAccount ----

    @Test
    fun `deleteAccount removes the user`() {
        every { userRepository.findById(testUser.id) } returns testUser

        service.deleteAccount(testUser.id)

        verify { userRepository.deleteById(testUser.id) }
    }

    @Test
    fun `deleteAccount blocks deleting the only admin`() {
        every { userRepository.findById(adminUser.id) } returns adminUser
        every { userRepository.countByRole(UserRole.ADMIN) } returns 1L

        assertThrows<io.github.rygel.outerstellar.platform.model.InsufficientPermissionException> {
            service.deleteAccount(adminUser.id)
        }
        verify(exactly = 0) { userRepository.deleteById(any()) }
    }

    @Test
    fun `deleteAccount allows admin deletion when another admin exists`() {
        every { userRepository.findById(adminUser.id) } returns adminUser
        every { userRepository.countByRole(UserRole.ADMIN) } returns 2L

        service.deleteAccount(adminUser.id)

        verify { userRepository.deleteById(adminUser.id) }
    }

    // ---- updateNotificationPreferences ----

    @Test
    fun `updateNotificationPreferences persists both flags`() {
        every { userRepository.findById(testUser.id) } returns testUser

        service.updateNotificationPreferences(testUser.id, emailEnabled = false, pushEnabled = true)

        verify { userRepository.updateNotificationPreferences(testUser.id, false, true) }
    }

    @Test
    fun `updateNotificationPreferences disables all notifications`() {
        every { userRepository.findById(testUser.id) } returns testUser

        service.updateNotificationPreferences(testUser.id, emailEnabled = false, pushEnabled = false)

        verify { userRepository.updateNotificationPreferences(testUser.id, false, false) }
    }
}
