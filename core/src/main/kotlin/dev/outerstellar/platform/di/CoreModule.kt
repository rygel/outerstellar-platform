package dev.outerstellar.platform.di

import dev.outerstellar.platform.persistence.AuditRepository
import dev.outerstellar.platform.persistence.TransactionManager
import dev.outerstellar.platform.service.ConsoleEmailService
import dev.outerstellar.platform.service.ContactService
import dev.outerstellar.platform.service.EmailService
import dev.outerstellar.platform.service.EventPublisher
import dev.outerstellar.platform.service.MessageService
import dev.outerstellar.platform.service.NoOpEventPublisher
import dev.outerstellar.platform.service.OutboxProcessor
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
