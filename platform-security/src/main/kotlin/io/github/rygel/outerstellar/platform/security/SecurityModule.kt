package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.service.EmailService
import org.koin.dsl.module

@Deprecated("Use createSecurityComponents() for server runtime. This exists for desktop Koin compatibility only.")
val securityModule
    get() = module {
        single {
            val config = get<AppConfig>()
            createSecurityComponents(
                config = config,
                userRepository = get(),
                auditRepository = getOrNull(),
                resetRepository = getOrNull(),
                apiKeyRepository = getOrNull(),
                emailService = getOrNull<EmailService>(),
                oauthRepository = getOrNull(),
                sessionRepository = getOrNull(),
            )
        }
        single<PasswordEncoder> { get<SecurityComponents>().passwordEncoder }
        single { get<SecurityComponents>().jwtService }
        single { get<SecurityComponents>().asyncActivityUpdater }
        single { get<SecurityComponents>().securityService }
        single<PermissionResolver> { get<SecurityComponents>().permissionResolver }
        single<List<AuthRealm>> { get<SecurityComponents>().authRealms }
        single { get<SecurityComponents>().totpService }
    }
