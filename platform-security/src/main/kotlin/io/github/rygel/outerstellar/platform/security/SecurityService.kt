package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.service.EmailService
import java.util.UUID
import org.slf4j.LoggerFactory

class SecurityService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditRepository: AuditRepository? = null,
    private val resetRepository: PasswordResetRepository? = null,
    private val apiKeyRepository: ApiKeyRepository? = null,
    private val emailService: EmailService? = null,
    private val oauthRepository: OAuthRepository? = null,
    private val config: SecurityConfig = SecurityConfig(),
    private val sessionRepository: SessionRepository? = null,
    private val activityUpdater: AsyncActivityUpdater? = null,
) {
    private val logger = LoggerFactory.getLogger(SecurityService::class.java)

    private val passwordResetService by lazy {
        PasswordResetService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            resetRepository = resetRepository,
            auditRepository = auditRepository,
            sessionRepository = sessionRepository,
            emailService = emailService,
            appBaseUrl = config.appBaseUrl,
        )
    }

    private val apiKeyService by lazy {
        ApiKeyService(userRepository = userRepository, apiKeyRepository = apiKeyRepository)
    }

    private val oauthService by lazy {
        OAuthService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            oauthRepository = oauthRepository,
            auditRepository = auditRepository,
        )
    }

    fun requestPasswordReset(email: String): String? = passwordResetService.requestPasswordReset(email)

    fun resetPassword(token: String, newPassword: String) = passwordResetService.resetPassword(token, newPassword)

    fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse {
        val result = apiKeyService.createApiKey(userId, name)
        val user = userRepository.findById(userId)
        audit("API_KEY_CREATED", actor = user, detail = "name=$name")
        return result
    }

    fun authenticateApiKey(rawKey: String): User? = apiKeyService.authenticateApiKey(rawKey)

    fun listApiKeys(userId: UUID): List<ApiKeySummary> = apiKeyService.listApiKeys(userId)

    fun deleteApiKey(userId: UUID, keyId: Long) {
        apiKeyService.deleteApiKey(userId, keyId)
        val user = userRepository.findById(userId)
        audit("API_KEY_DELETED", actor = user, detail = "keyId=$keyId")
    }

    fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User =
        oauthService.findOrCreateOAuthUser(providerName, oauthSubject, email)

    private fun audit(
        action: String,
        actor: User? = null,
        target: User? = null,
        detail: String? = null,
        targetUsername: String? = null,
    ) {
        auditRepository?.log(
            AuditEntry(
                actorId = actor?.id?.toString(),
                actorUsername = actor?.username,
                targetId = target?.id?.toString(),
                targetUsername = targetUsername ?: target?.username,
                action = action,
                detail = detail,
            )
        )
    }
}
