package dev.outerstellar.starter.di

import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.service.OutboxProcessor
import dev.outerstellar.starter.persistence.TransactionManager
import org.koin.dsl.module

import dev.outerstellar.starter.service.EventPublisher
import dev.outerstellar.starter.service.NoOpEventPublisher

val coreModule = module {
    single { MessageService(get(), get(), get(), get(), get<EventPublisher>()) }
    single { OutboxProcessor(get(), getOrNull<TransactionManager>()) }
    single<EventPublisher> { NoOpEventPublisher }
}
