package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.OAuthRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.service.EmailService
import io.github.rygel.outerstellar.platform.service.NoOpEmailService

class SecurityComponents(
    val jwtService: JwtService,
    val asyncActivityUpdater: AsyncActivityUpdater,
    val authService: AuthService,
    val accountService: AccountService,
    val apiKeyService: ApiKeyService,
    val passwordResetService: PasswordResetService,
    val oauthService: OAuthService,
    val authRealms: List<AuthRealm>,
    val totpService: TOTPService,
    val sessionService: SessionService,
    val userAdminService: UserAdminService,
)

@Deprecated(
    "Use createSecurityComponents with an explicit emailService parameter.",
    ReplaceWith(
        "createSecurityComponents(config, userRepository, auditRepository, resetRepository, apiKeyRepository, NoOpEmailService(), oauthRepository, sessionRepository)"
    ),
)
fun createSecurityComponents(
    config: AppConfig,
    userRepository: UserRepository,
    auditRepository: AuditRepository? = null,
    resetRepository: PasswordResetRepository? = null,
    apiKeyRepository: ApiKeyRepository? = null,
    oauthRepository: OAuthRepository? = null,
    sessionRepository: SessionRepository? = null,
): SecurityComponents =
    createSecurityComponents(
        config = config,
        userRepository = userRepository,
        auditRepository = auditRepository,
        resetRepository = resetRepository,
        apiKeyRepository = apiKeyRepository,
        emailService = NoOpEmailService(),
        oauthRepository = oauthRepository,
        sessionRepository = sessionRepository,
    )

@Suppress("LongMethod")
fun createSecurityComponents(
    config: AppConfig,
    userRepository: UserRepository,
    auditRepository: AuditRepository? = null,
    resetRepository: PasswordResetRepository? = null,
    apiKeyRepository: ApiKeyRepository? = null,
    emailService: EmailService,
    oauthRepository: OAuthRepository? = null,
    sessionRepository: SessionRepository? = null,
    transactionManager: TransactionManager? = null,
): SecurityComponents {
    val passwordEncoder = BCryptPasswordEncoder()
    val tokenHashing = TokenHashing(config.tokenPepper)
    val totpSecretEncryption = TotpSecretEncryption(config.tokenPepper)
    val jwtService = JwtService(config.jwt)
    val asyncActivityUpdater = AsyncActivityUpdater(userRepository)
    val securityConfig =
        SecurityConfig(
            appBaseUrl = config.appBaseUrl,
            sessionTimeoutSeconds = config.sessionTimeoutMinutes.toLong() * 60,
            maxFailedLoginAttempts = config.maxFailedLoginAttempts,
            lockoutDurationSeconds = config.lockoutDurationSeconds,
            sessionAbsoluteTimeoutSeconds = config.sessionAbsoluteTimeoutMinutes.toLong() * 60,
            registrationEnabled = config.registrationEnabled,
        )
    val totpService = TOTPService(passwordEncoder)
    val sessionService =
        SessionService(
            sessionRepository = sessionRepository ?: error("SessionRepository required"),
            userRepository = userRepository,
            config = securityConfig,
            activityUpdater = asyncActivityUpdater,
            tokenHashing = tokenHashing,
        )
    val userAdminService = UserAdminService(userRepository, auditRepository ?: error("AuditRepository required"))
    val authService =
        AuthService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            auditRepository = auditRepository,
            config = securityConfig,
            totpService = totpService,
            totpSecretEncryption = totpSecretEncryption,
            transactionManager = transactionManager,
        )

    val accountService =
        AccountService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            sessionRepository = sessionRepository,
            auditRepository = auditRepository,
        )

    val apiKeyService =
        ApiKeyService(
            userRepository = userRepository,
            apiKeyRepository = apiKeyRepository ?: error("ApiKeyRepository required for ApiKeyService"),
            auditRepository = auditRepository,
            tokenHashing = tokenHashing,
        )
    val passwordResetService =
        PasswordResetService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            resetRepository = resetRepository,
            auditRepository = auditRepository,
            sessionRepository = sessionRepository,
            emailService = emailService,
            appBaseUrl = config.appBaseUrl,
            tokenHashing = tokenHashing,
        )
    val oauthService =
        OAuthService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            oauthRepository = oauthRepository,
            auditRepository = auditRepository,
            transactionManager = transactionManager,
        )
    val permissionResolver = RoleBasedPermissionResolver()
    val authRealms = listOf(SessionRealm(sessionService), ApiKeyRealm(apiKeyService))
    return SecurityComponents(
        jwtService = jwtService,
        asyncActivityUpdater = asyncActivityUpdater,
        apiKeyService = apiKeyService,
        passwordResetService = passwordResetService,
        oauthService = oauthService,
        authService = authService,
        accountService = accountService,
        authRealms = authRealms,
        totpService = totpService,
        sessionService = sessionService,
        userAdminService = userAdminService,
    )
}
