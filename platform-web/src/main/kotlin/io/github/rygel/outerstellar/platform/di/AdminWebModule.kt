package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.security.AdminStatsService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.web.AdminPageFactory
import org.koin.dsl.module

val adminWebModule
    get() = module {
        single { NotificationService(get()) }
        single { AdminPageFactory(get(), get()) }
        single { AdminStatsService(get()) }
    }
