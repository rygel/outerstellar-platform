package io.github.rygel.outerstellar.platform.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.rygel.outerstellar.platform.JwtConfig
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
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

    /** Verified claims cached for 60 s to avoid re-parsing identical tokens on rapid requests. */
    private val claimsCache =
        Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build<String, Pair<UUID, Boolean>>()

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
     * expired.
     */
    fun extractClaims(token: String): Pair<UUID, Boolean>? {
        if (!isEnabled) return null
        claimsCache.getIfPresent(token)?.let {
            return it
        }
        return try {
            val jwt = JWT.require(algorithm).withIssuer(config.issuer).build().verify(token)
            val userId = UUID.fromString(jwt.subject)
            val isAdmin = jwt.getClaim("admin")?.asBoolean() ?: false
            (userId to isAdmin).also { claimsCache.put(token, it) }
        } catch (e: JWTVerificationException) {
            logger.warn("JWT verification failed: {}", e.message)
            null
        }
    }

    /** Evict a token from the claims cache (e.g. on logout). */
    fun invalidate(token: String) {
        claimsCache.invalidate(token)
    }
}
