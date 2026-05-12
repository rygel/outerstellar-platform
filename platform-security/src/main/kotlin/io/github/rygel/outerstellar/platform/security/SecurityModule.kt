package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.AppConfig
import org.koin.dsl.module

val securityModule
    get() = module {
        single<PasswordEncoder> { BCryptPasswordEncoder() }
        single { JwtService(get<AppConfig>().jwt) }
        single { AsyncActivityUpdater(get()) }
        single {
            SecurityService(
                get(),
                get(),
                getOrNull(),
                getOrNull(),
                getOrNull(),
                getOrNull(),
                getOrNull(),
                SecurityConfig(
                    appBaseUrl = get<AppConfig>().appBaseUrl,
                    sessionTimeoutSeconds = get<AppConfig>().sessionTimeoutMinutes.toLong() * 60,
                    maxFailedLoginAttempts = get<AppConfig>().maxFailedLoginAttempts,
                    lockoutDurationSeconds = get<AppConfig>().lockoutDurationSeconds,
                ),
                getOrNull(),
                get(),
            )
        }
        single<PermissionResolver> { RoleBasedPermissionResolver() }
        single<List<AuthRealm>> { listOf(SessionRealm(get()), ApiKeyRealm(get())) }
    }
