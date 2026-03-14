package dev.outerstellar.starter.security

import dev.outerstellar.starter.model.ApiKey
import dev.outerstellar.starter.model.InsufficientPermissionException
import dev.outerstellar.starter.model.UsernameAlreadyExistsException
import dev.outerstellar.starter.model.WeakPasswordException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
            )
    }

    // ---- authenticate ----

    @Test
    fun `authenticate returns user on valid credentials`() {
        every { userRepository.findByUsername("testuser") } returns testUser
        every { passwordEncoder.matches("correctpass", testUser.passwordHash) } returns true

        val result = service.authenticate("testuser", "correctpass")

        assertNotNull(result)
        assertEquals(testUser.id, result.id)
        assertEquals("testuser", result.username)
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

    // ---- register ----

    @Test
    fun `register throws on duplicate username`() {
        every { userRepository.findByUsername("existing") } returns testUser

        assertThrows<UsernameAlreadyExistsException> { service.register("existing", "password123") }
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

        assertThrows<WeakPasswordException> {
            service.changePassword(testUser.id, "wrongcurrent", "newpassword1")
        }
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
    fun `changePassword throws on short new password`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("currentpass", testUser.passwordHash) } returns true

        assertThrows<WeakPasswordException> {
            service.changePassword(testUser.id, "currentpass", "short")
        }
    }

    // ---- setUserEnabled ----

    @Test
    fun `setUserEnabled prevents self-disable`() {
        assertThrows<InsufficientPermissionException> {
            service.setUserEnabled(adminUser.id, adminUser.id, false)
        }
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
        assertThrows<InsufficientPermissionException> {
            service.setUserRole(adminUser.id, adminUser.id, UserRole.USER)
        }
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
        val expectedHash =
            digest.digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }

        val apiKey =
            ApiKey(
                id = 1L,
                userId = testUser.id,
                keyHash = expectedHash,
                keyPrefix = "osk_abcd",
                name = "test-key",
            )

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
        val expectedHash =
            digest.digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }

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
        val expectedHash =
            digest.digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }

        val apiKey =
            ApiKey(
                id = 3L,
                userId = testUser.id,
                keyHash = expectedHash,
                keyPrefix = "osk_abcd",
                name = "test-key",
            )

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
}
