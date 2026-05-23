package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.service.EmailService
import org.koin.dsl.module

@Deprecated("Use createSecurityComponents() for server runtime. This exists for desktop Koin compatibility only.")
val securityModule
    get() = module {
        single<PasswordEncoder> { BCryptPasswordEncoder() }
        single { JwtService(get<AppConfig>().jwt) }
        single { AsyncActivityUpdater(get<UserRepository>()) }
        single {
            SecurityService(
                userRepository = get<UserRepository>(),
                passwordEncoder = get<PasswordEncoder>(),
                auditRepository = getOrNull<AuditRepository>(),
                resetRepository = getOrNull<PasswordResetRepository>(),
                apiKeyRepository = getOrNull<ApiKeyRepository>(),
                emailService = getOrNull<EmailService>(),
                oauthRepository = getOrNull<OAuthRepository>(),
                config =
                    SecurityConfig(
                        appBaseUrl = get<AppConfig>().appBaseUrl,
                        sessionTimeoutSeconds = get<AppConfig>().sessionTimeoutMinutes.toLong() * 60,
                        maxFailedLoginAttempts = get<AppConfig>().maxFailedLoginAttempts,
                        lockoutDurationSeconds = get<AppConfig>().lockoutDurationSeconds,
                        sessionAbsoluteTimeoutSeconds = get<AppConfig>().sessionAbsoluteTimeoutMinutes.toLong() * 60,
                        registrationEnabled = get<AppConfig>().registrationEnabled,
                    ),
                sessionRepository = getOrNull<SessionRepository>(),
                activityUpdater = getOrNull<AsyncActivityUpdater>(),
                totpService = get<TOTPService>(),
            )
        }
        single<PermissionResolver> { RoleBasedPermissionResolver() }
        single<List<AuthRealm>> { listOf(SessionRealm(get()), ApiKeyRealm(get())) }
        single { TOTPService() }
    }
