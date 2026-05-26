package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.OAuthConnection
import io.github.rygel.outerstellar.platform.persistence.OAuthRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class OAuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val oauthRepository: OAuthRepository? = null,
    private val auditRepository: AuditRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(OAuthService::class.java)

    /**
     * Find an existing user linked to an OAuth provider identity, or create a new one.
     *
     * If [oauthRepository] is not configured this throws [IllegalStateException].
     */
    fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User {
        val repo = oauthRepository ?: error("OAuthRepository is not configured")

        val existing = repo.findByProviderSubject(providerName, oauthSubject)
        if (existing != null) {
            return userRepository.findById(existing.userId)
                ?: error("OAuth user record found but linked user missing: ${existing.userId}")
        }

        // Derive a username from the email or generate a random one
        val baseUsername =
            email?.substringBefore('@')?.filter { it.isLetterOrDigit() }?.take(30)
                ?: providerName + "_" + UUID.randomUUID().toString().take(8)
        val username = ensureUniqueUsername(baseUsername)

        val user =
            User(
                id = UUID.randomUUID(),
                username = username,
                email = email ?: "$username@$providerName.oauth",
                passwordHash = passwordEncoder.encode(UUID.randomUUID().toString()),
                role = UserRole.USER,
            )
        userRepository.save(user)

        repo.save(
            OAuthConnection(id = 0L, userId = user.id, provider = providerName, subject = oauthSubject, email = email)
        )
        logger.info("Created new user {} via OAuth provider {}", sanitize(username), providerName)
        auditRepository?.logAction("OAUTH_USER_CREATED", actor = user, detail = "provider=$providerName")
        return user
    }

    private fun ensureUniqueUsername(base: String): String {
        if (userRepository.findByUsername(base) == null) return base
        var i = 2
        while (userRepository.findByUsername("$base$i") != null) i++
        return "$base$i"
    }
}
