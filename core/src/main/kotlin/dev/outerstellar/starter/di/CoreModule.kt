package dev.outerstellar.starter.di

import dev.outerstellar.starter.persistence.AuditRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.service.ConsoleEmailService
import dev.outerstellar.starter.service.ContactService
import dev.outerstellar.starter.service.EmailService
import dev.outerstellar.starter.service.EventPublisher
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.service.NoOpEventPublisher
import dev.outerstellar.starter.service.OutboxProcessor
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
