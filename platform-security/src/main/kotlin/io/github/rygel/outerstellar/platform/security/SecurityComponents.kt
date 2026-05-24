package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.service.EmailService

class SecurityComponents(
    val passwordEncoder: PasswordEncoder,
    val jwtService: JwtService,
    val asyncActivityUpdater: AsyncActivityUpdater,
    val securityService: SecurityService,
    val permissionResolver: PermissionResolver,
    val authRealms: List<AuthRealm>,
    val totpService: TOTPService,
    val sessionService: SessionService,
    val userAdminService: UserAdminService,
)

fun createSecurityComponents(
    config: AppConfig,
    userRepository: UserRepository,
    auditRepository: AuditRepository? = null,
    resetRepository: PasswordResetRepository? = null,
    apiKeyRepository: ApiKeyRepository? = null,
    emailService: EmailService? = null,
    oauthRepository: OAuthRepository? = null,
    sessionRepository: SessionRepository? = null,
): SecurityComponents {
    val passwordEncoder = BCryptPasswordEncoder()
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
    val totpService = TOTPService()
    val sessionService =
        SessionService(
            sessionRepository = sessionRepository ?: error("SessionRepository required"),
            userRepository = userRepository,
            config = securityConfig,
            activityUpdater = asyncActivityUpdater,
        )
    val userAdminService = UserAdminService(userRepository = userRepository, auditRepository = auditRepository)
    val securityService =
        SecurityService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            auditRepository = auditRepository,
            resetRepository = resetRepository,
            apiKeyRepository = apiKeyRepository,
            emailService = emailService,
            oauthRepository = oauthRepository,
            config = securityConfig,
            sessionRepository = sessionRepository,
            activityUpdater = asyncActivityUpdater,
            totpService = totpService,
            sessionService = sessionService,
        )
    val permissionResolver = RoleBasedPermissionResolver()
    val authRealms = listOf(SessionRealm(sessionService), ApiKeyRealm(securityService))
    return SecurityComponents(
        passwordEncoder = passwordEncoder,
        jwtService = jwtService,
        asyncActivityUpdater = asyncActivityUpdater,
        securityService = securityService,
        permissionResolver = permissionResolver,
        authRealms = authRealms,
        totpService = totpService,
        sessionService = sessionService,
        userAdminService = userAdminService,
    )
}
