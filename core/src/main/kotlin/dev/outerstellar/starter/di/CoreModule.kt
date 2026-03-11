package dev.outerstellar.starter.di

import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.service.ContactService
import dev.outerstellar.starter.service.EventPublisher
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.service.NoOpEventPublisher
import dev.outerstellar.starter.service.OutboxProcessor
import org.koin.dsl.module

val coreModule = module {
    single { MessageService(get(), get(), get(), get(), get<EventPublisher>()) }
    single { ContactService(get()) }
    single { OutboxProcessor(get(), getOrNull<TransactionManager>()) }
    single<EventPublisher> { NoOpEventPublisher }
}
