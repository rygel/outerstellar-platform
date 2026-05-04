package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OAuthServiceTest {

    private val userRepo = mockk<UserRepository>(relaxed = true)
    private val passwordEncoder = mockk<PasswordEncoder>(relaxed = true)
    private val oauthRepo = mockk<OAuthRepository>(relaxed = true)
    private val auditRepo = mockk<AuditRepository>(relaxed = true)
    private val service = OAuthService(userRepo, passwordEncoder, oauthRepo, auditRepo)

    @Test
    fun `findOrCreateOAuthUser returns existing user when OAuth connection exists`() {
        val userId = UUID.randomUUID()
        val existingUser = User(userId, "alice", "alice@example.com", "hash", UserRole.USER)
        every { oauthRepo.findByProviderSubject("apple", "sub123") } returns
            OAuthConnection(0L, userId, "apple", "sub123", "alice@example.com")
        every { userRepo.findById(userId) } returns existingUser

        val result = service.findOrCreateOAuthUser("apple", "sub123", "alice@example.com")

        assertEquals("alice", result.username)
    }

    @Test
    fun `findOrCreateOAuthUser creates new user when no connection exists`() {
        every { oauthRepo.findByProviderSubject("google", "sub456") } returns null
        every { userRepo.findByUsername(any()) } returns null

        val result = service.findOrCreateOAuthUser("google", "sub456", "bob@example.com")

        assertEquals("bob@example.com", result.email)
        verify { userRepo.save(any()) }
        verify { oauthRepo.save(any()) }
    }

    @Test
    fun `findOrCreateOAuthUser derives username from email prefix`() {
        every { oauthRepo.findByProviderSubject("github", "sub789") } returns null
        every { userRepo.findByUsername("johndoe") } returns null

        val result = service.findOrCreateOAuthUser("github", "sub789", "johndoe@example.com")

        assertEquals("johndoe", result.username)
    }

    @Test
    fun `findOrCreateOAuthUser appends number when username taken`() {
        every { oauthRepo.findByProviderSubject("github", "sub789") } returns null
        every { userRepo.findByUsername("johndoe") } returns mockk()
        every { userRepo.findByUsername("johndoe2") } returns null

        val result = service.findOrCreateOAuthUser("github", "sub789", "johndoe@example.com")

        assertEquals("johndoe2", result.username)
    }

    @Test
    fun `findOrCreateOAuthUser generates random username when no email`() {
        every { oauthRepo.findByProviderSubject("github", "sub999") } returns null
        every { userRepo.findByUsername(any()) } returns null

        val result = service.findOrCreateOAuthUser("github", "sub999", null)

        assertTrue(result.username.startsWith("github_"))
    }

    @Test
    fun `findOrCreateOAuthUser logs audit entry on creation`() {
        every { oauthRepo.findByProviderSubject("github", "sub000") } returns null
        every { userRepo.findByUsername(any()) } returns null

        service.findOrCreateOAuthUser("github", "sub000", "new@example.com")

        verify { auditRepo.log(any()) }
    }

    @Test
    fun `findOrCreateOAuthUser throws when OAuthRepository not configured`() {
        val svc = OAuthService(userRepo, passwordEncoder, null, null)

        assertThrows<IllegalStateException> { svc.findOrCreateOAuthUser("apple", "sub123", "alice@example.com") }
    }
}
