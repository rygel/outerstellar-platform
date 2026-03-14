package dev.outerstellar.starter.security

import org.koin.dsl.module

val securityModule
    get() = module {
        single<PasswordEncoder> { BCryptPasswordEncoder() }
        single { SecurityService(get(), get(), getOrNull(), getOrNull(), getOrNull()) }
    }
