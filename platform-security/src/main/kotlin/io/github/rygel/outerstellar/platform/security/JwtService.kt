package io.github.rygel.outerstellar.platform.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import io.github.rygel.outerstellar.platform.JwtConfig
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("outerstellar.JwtService")

class JwtService(private val config: JwtConfig) {

    init {
        if (config.enabled && config.secret.isBlank()) {
            logger.warn("JWT is enabled but secret is blank — JWT authentication will be disabled")
        }
    }

    /** True only when JWT is enabled and a secret is configured. */
    val isEnabled: Boolean
        get() = config.enabled && config.secret.isNotBlank()

    private val algorithm by lazy { Algorithm.HMAC256(config.secret) }

    /** Issue a signed JWT for [user]. Returns null if JWT is not enabled. */
    fun generateToken(user: User): String? {
        if (!isEnabled) return null
        val now = Instant.now()
        return JWT.create()
            .withIssuer(config.issuer)
            .withSubject(user.id.toString())
            .withClaim("username", user.username)
            .withClaim("admin", user.role == UserRole.ADMIN)
            .withIssuedAt(now)
            .withExpiresAt(now.plus(config.expirySeconds, ChronoUnit.SECONDS))
            .sign(algorithm)
    }

    /**
     * Verify [token] and return (userId, isAdmin). Returns null if JWT is not enabled, the token is invalid, or it has
     * expired. The token is re-verified on every call — HMAC-SHA256 verification is sub-millisecond, so a claims cache
     * is not a meaningful optimisation, and caching raw bearer tokens as keys kept them valid for up to 60s after a
     * credential-revoking event (logout, password change, role change, account disable) and exposed up to 2000 raw
     * tokens in a heap dump. See issue #507.
     */
    fun extractClaims(token: String): Pair<UUID, Boolean>? {
        if (!isEnabled) return null
        return try {
            val jwt = JWT.require(algorithm).withIssuer(config.issuer).build().verify(token)
            val userId = UUID.fromString(jwt.subject)
            val isAdmin = jwt.getClaim("admin")?.asBoolean() ?: false
            userId to isAdmin
        } catch (e: JWTVerificationException) {
            logger.warn("JWT verification failed: {}", e.message)
            null
        }
    }

    /** No-op retained for API compatibility. Caching was removed (issue #507); tokens always re-verify. */
    @Suppress("UnusedParameter")
    fun invalidate(token: String) {
        // Intentionally empty: there is no longer a claims cache to invalidate. Retained so callers
        // (logout, password reset, role change) compile without modification.
    }
}
