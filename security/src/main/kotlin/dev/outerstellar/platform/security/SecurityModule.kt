package dev.outerstellar.platform.security

import dev.outerstellar.platform.AppConfig
import org.koin.core.qualifier.named
import org.koin.dsl.module

val securityModule
    get() = module {
        single<PasswordEncoder> { BCryptPasswordEncoder() }
        single { JwtService(get<AppConfig>().jwt) }
        single {
            SecurityService(
                get(),
                get(),
                getOrNull(),
                getOrNull(),
                getOrNull(),
                getOrNull(),
                getOrNull(),
                getOrNull<String>(named("appBaseUrl")) ?: "http://localhost:8080",
                getOrNull(),
                get<dev.outerstellar.platform.AppConfig>().sessionTimeoutMinutes.toLong() * 60,
            )
        }
    }
