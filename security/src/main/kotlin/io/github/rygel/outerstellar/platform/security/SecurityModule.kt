package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.AppConfig
import org.koin.core.qualifier.named
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
                getOrNull<String>(named("appBaseUrl")) ?: "http://localhost:8080",
                getOrNull(),
                get<io.github.rygel.outerstellar.platform.AppConfig>().sessionTimeoutMinutes.toLong() * 60,
                get(),
            )
        }
    }
