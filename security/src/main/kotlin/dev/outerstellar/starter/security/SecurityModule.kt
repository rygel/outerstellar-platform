package dev.outerstellar.starter.security

import org.koin.core.qualifier.named
import org.koin.dsl.module

val securityModule
    get() = module {
        single<PasswordEncoder> { BCryptPasswordEncoder() }
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
            )
        }
    }
