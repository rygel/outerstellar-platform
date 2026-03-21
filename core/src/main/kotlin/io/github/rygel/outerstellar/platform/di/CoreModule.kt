package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.service.ConsoleEmailService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.EmailService
import io.github.rygel.outerstellar.platform.service.EventPublisher
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NoOpEventPublisher
import io.github.rygel.outerstellar.platform.service.OutboxProcessor
import org.koin.dsl.module

val coreModule
    get() = module {
        single {
            MessageService(
                get(),
                get(),
                get(),
                get(),
                get<EventPublisher>(),
                getOrNull<AuditRepository>(),
            )
        }
        single {
            ContactService(
                get(),
                get<EventPublisher>(),
                getOrNull<TransactionManager>(),
                getOrNull<AuditRepository>(),
            )
        }
        single { OutboxProcessor(get(), getOrNull<TransactionManager>()) }
        single<EventPublisher> { NoOpEventPublisher }
        single<EmailService> { ConsoleEmailService() }
    }
