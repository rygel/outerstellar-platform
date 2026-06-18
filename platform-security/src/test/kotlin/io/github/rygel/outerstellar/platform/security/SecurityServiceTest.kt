package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.ApiKey
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SecurityServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var apiKeyService: ApiKeyService
    private lateinit var userAdminService: UserAdminService

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
        auditRepository = mockk(relaxed = true)
        apiKeyRepository = mockk(relaxed = true)
        apiKeyService =
            ApiKeyService(
                userRepository = userRepository,
                apiKeyRepository = apiKeyRepository,
                auditRepository = auditRepository,
            )
        userAdminService = UserAdminService(userRepository, auditRepository)
    }

    @Test
    fun `unlockAccount throws for non-admin caller`() {
        every { userRepository.findById(adminUser.id) } returns adminUser.copy(role = UserRole.USER)

        assertThrows<InsufficientPermissionException> { userAdminService.unlockAccount(adminUser.id, testUser.id) }
    }

    @Test
    fun `unlockAccount resets failed attempts for target user`() {
        every { userRepository.findById(adminUser.id) } returns adminUser
        every { userRepository.findById(testUser.id) } returns testUser

        userAdminService.unlockAccount(adminUser.id, testUser.id)

        verify { userRepository.resetFailedLoginAttempts(testUser.id) }
    }

    @Test
    fun `setUserEnabled prevents self-disable`() {
        assertThrows<InsufficientPermissionException> {
            userAdminService.setUserEnabled(adminUser.id, adminUser.id, false)
        }
    }

    @Test
    fun `setUserEnabled rejects non-admin caller`() {
        every { userRepository.findById(testUser.id) } returns testUser

        assertThrows<InsufficientPermissionException> {
            userAdminService.setUserEnabled(testUser.id, adminUser.id, false)
        }
    }

    @Test
    fun `setUserEnabled updates target user`() {
        every { userRepository.findById(adminUser.id) } returns adminUser
        every { userRepository.findById(testUser.id) } returns testUser

        userAdminService.setUserEnabled(adminUser.id, testUser.id, false)

        verify { userRepository.updateEnabled(testUser.id, false) }
    }

    @Test
    fun `setUserRole prevents self-demotion`() {
        assertThrows<InsufficientPermissionException> {
            userAdminService.setUserRole(adminUser.id, adminUser.id, UserRole.USER)
        }
    }

    @Test
    fun `setUserRole rejects non-admin caller`() {
        every { userRepository.findById(testUser.id) } returns testUser

        assertThrows<InsufficientPermissionException> {
            userAdminService.setUserRole(testUser.id, adminUser.id, UserRole.ADMIN)
        }
    }

    @Test
    fun `setUserRole updates target user role`() {
        every { userRepository.findById(adminUser.id) } returns adminUser
        every { userRepository.findById(testUser.id) } returns testUser

        userAdminService.setUserRole(adminUser.id, testUser.id, UserRole.ADMIN)

        verify { userRepository.updateRole(testUser.id, UserRole.ADMIN) }
    }

    @Test
    fun `setUserRole refuses to demote the last enabled admin`() {
        // Regression guard for issue #513: demoting the only enabled admin would lock everyone out of
        // admin endpoints with no recovery short of DB intervention. Actor is a disabled admin (role check
        // passes on role, not enabled), target is the sole enabled admin.
        val soleEnabledAdmin = adminUser.copy(id = UUID.randomUUID())
        val actor = adminUser.copy(id = UUID.randomUUID(), enabled = false)
        every { userRepository.findById(actor.id) } returns actor
        every { userRepository.findById(soleEnabledAdmin.id) } returns soleEnabledAdmin
        every { userRepository.findAll() } returns listOf(soleEnabledAdmin, actor, testUser)

        assertThrows<InsufficientPermissionException> {
            userAdminService.setUserRole(actor.id, soleEnabledAdmin.id, UserRole.USER)
        }
    }

    @Test
    fun `setUserEnabled refuses to disable the last enabled admin`() {
        // Actor is a disabled admin (role check passes on role, not enabled), target is the sole enabled
        // admin. Disabling the target would leave zero enabled admins. Actor != target so the self-check
        // does not short-circuit; the last-admin guard must fire.
        val soleEnabledAdmin = adminUser.copy(id = UUID.randomUUID())
        val actor = adminUser.copy(id = UUID.randomUUID(), enabled = false)
        every { userRepository.findById(actor.id) } returns actor
        every { userRepository.findById(soleEnabledAdmin.id) } returns soleEnabledAdmin
        every { userRepository.findAll() } returns listOf(soleEnabledAdmin, actor, testUser)

        assertThrows<InsufficientPermissionException> {
            userAdminService.setUserEnabled(actor.id, soleEnabledAdmin.id, false)
        }
    }

    @Test
    fun `createApiKey returns key with osk prefix`() {
        val response = apiKeyService.createApiKey(testUser.id, "my-key")

        assertTrue(response.key.startsWith("osk_"), "Key should start with osk_ prefix")
        assertEquals("my-key", response.name)
        assertTrue(response.keyPrefix.isNotBlank())
        verify { apiKeyRepository.save(any()) }
    }

    @Test
    fun `createApiKey throws on blank name`() {
        assertThrows<IllegalArgumentException> { apiKeyService.createApiKey(testUser.id, "") }
    }

    @Test
    fun `authenticateApiKey returns user for valid key`() {
        val rawKey = "osk_abcdef1234567890abcdef1234567890"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val expectedHash = digest.digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }

        val apiKey =
            ApiKey(id = 1L, userId = testUser.id, keyHash = expectedHash, keyPrefix = "osk_abcd", name = "test-key")

        every { apiKeyRepository.findByKeyHash(expectedHash) } returns apiKey
        every { userRepository.findById(testUser.id) } returns testUser

        val result = apiKeyService.authenticateApiKey(rawKey)

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

        val result = apiKeyService.authenticateApiKey(rawKey)

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

        val result = apiKeyService.authenticateApiKey(rawKey)

        assertNull(result)
    }

    @Test
    fun `authenticateApiKey returns null for unknown key`() {
        every { apiKeyRepository.findByKeyHash(any()) } returns null

        val result = apiKeyService.authenticateApiKey("osk_nonexistent")

        assertNull(result)
    }

    @Test
    fun `createApiKey logs API_KEY_CREATED to audit`() {
        every { userRepository.findById(testUser.id) } returns testUser

        apiKeyService.createApiKey(testUser.id, "my-audit-key")

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

        apiKeyService.deleteApiKey(testUser.id, 42L)

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
}
