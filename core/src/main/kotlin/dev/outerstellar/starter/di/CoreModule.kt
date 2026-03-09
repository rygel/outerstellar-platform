package dev.outerstellar.starter.di

import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.service.OutboxProcessor
import dev.outerstellar.starter.service.SyncProvider
import org.koin.dsl.module

val coreModule = module {
    single { MessageService(get(), get(), get(), get()) }
    single { OutboxProcessor(get(), getOrNull<SyncProvider>()) }
}
