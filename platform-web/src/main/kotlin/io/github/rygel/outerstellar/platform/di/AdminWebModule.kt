package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.persistence.JooqNotificationRepository
import io.github.rygel.outerstellar.platform.persistence.NotificationRepository
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.web.AdminPageFactory
import org.koin.dsl.module

val adminWebModule
    get() = module {
        single<NotificationRepository> { JooqNotificationRepository(get()) }
        single { NotificationService(get()) }
        single { AdminPageFactory(get(), get()) }
    }
