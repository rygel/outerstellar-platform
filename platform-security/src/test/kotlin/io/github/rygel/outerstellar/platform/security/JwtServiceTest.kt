package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.JwtConfig
import io.github.rygel.outerstellar.platform.model.UserRole
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class JwtServiceTest {

    private val config =
        JwtConfig(
            enabled = true,
            secret = "super-secret-key-for-testing-12345678",
            issuer = "test",
            expirySeconds = 3600,
        )
    private val service = JwtService(config)
    private val user = User(UUID.randomUUID(), "alice", "alice@example.com", "hash", UserRole.USER)

    @Test
    fun `generateToken returns a non-blank string`() {
        val token = service.generateToken(user)
        assertTrue(!token.isNullOrBlank())
    }

    @Test
    fun `generateToken returns null when JWT is disabled`() {
        val disabledService = JwtService(JwtConfig(enabled = false, secret = "secret", issuer = "test"))
        assertNull(disabledService.generateToken(user))
    }

    @Test
    fun `extractClaims returns userId and admin flag from valid token`() {
        val token = service.generateToken(user)!!

        val (userId, isAdmin) = service.extractClaims(token)!!

        assertEquals(user.id, userId)
        assertEquals(false, isAdmin)
    }

    @Test
    fun `extractClaims returns admin true for admin users`() {
        val admin = user.copy(role = UserRole.ADMIN)
        val token = service.generateToken(admin)!!

        val (_, isAdmin) = service.extractClaims(token)!!

        assertTrue(isAdmin)
    }

    @Test
    fun `extractClaims returns null for invalid token`() {
        assertNull(service.extractClaims("not.a.valid.token"))
    }

    @Test
    fun `extractClaims returns null for wrong issuer`() {
        val wrongIssuerConfig =
            JwtConfig(
                enabled = true,
                secret = "super-secret-key-for-testing-12345678",
                issuer = "wrong",
                expirySeconds = 3600,
            )
        val wrongService = JwtService(wrongIssuerConfig)
        val token = service.generateToken(user)!!

        assertNull(wrongService.extractClaims(token))
    }

    @Test
    fun `extractClaims caches and returns same result on repeat calls`() {
        val token = service.generateToken(user)!!

        val first = service.extractClaims(token)
        val second = service.extractClaims(token)

        assertEquals(first, second)
    }

    @Test
    fun `invalidate removes token from cache`() {
        val token = service.generateToken(user)!!
        val first = service.extractClaims(token)
        assertEquals(user.id, first!!.first)
        service.invalidate(token)
        val afterInvalidation = service.extractClaims(token)
        assertEquals(user.id, afterInvalidation!!.first)
    }

    @Test
    fun `isEnabled is true when config has secret`() {
        assertTrue(service.isEnabled)
    }

    @Test
    fun `isEnabled is false when secret is blank`() {
        val noSecret = JwtService(JwtConfig(enabled = true, secret = "", issuer = "test"))
        assertTrue(!noSecret.isEnabled)
    }

    @Test
    fun `isEnabled is false when disabled`() {
        val disabled = JwtService(JwtConfig(enabled = false, secret = "secret", issuer = "test"))
        assertTrue(!disabled.isEnabled)
    }
}
